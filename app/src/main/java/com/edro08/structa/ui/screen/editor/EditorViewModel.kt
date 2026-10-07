package com.edro08.structa.ui.screen.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edro08.structa.application.document.OpenDocument
import com.edro08.structa.application.document.SaveDocumentCopy
import com.edro08.structa.application.editor.FormatJsonDocument
import com.edro08.structa.application.editor.FormatXmlDocument
import com.edro08.structa.application.editor.FormatYamlDocument
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.FileMode
import com.edro08.structa.domain.filesystem.FileSystem
import com.edro08.structa.domain.workspace.*
import kotlinx.coroutines.delay
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.document.EditorDocument
import com.edro08.structa.domain.editor.command.*
import com.edro08.structa.domain.editor.search.*
import com.edro08.structa.domain.editor.syntax.Language
import com.edro08.structa.domain.editor.syntax.LanguageRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange as CoreTextRange
import com.edro08.structa.ui.editor.input.EditorInputSession
import com.edro08.structa.ui.editor.model.EditorViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SaveSnapshot(val name: String, val content: String,
    val document: EditorDocument, val revision: Long)

data class EditorTab(val documentId: DocumentId, val title: String, val dirty: Boolean, val active: Boolean)

data class EditorUiState(
    val entry: FileEntry? = null,
    val value: TextFieldValue = TextFieldValue(),
    val mode: FileMode = FileMode.TEXT,
    val languageOverride: Language? = null,
    val inputSession: EditorInputSession? = null,
    val viewState: EditorViewState = EditorViewState(),
    val tabs: List<EditorTab> = emptyList(),
    val dirty: Boolean = false,
    val pendingClose: DocumentId? = null,
    val contentVersion: Long = 0L,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val search: String = "",
    val occurrences: Int = 0,
    val searchOptions: SearchOptions = SearchOptions(),
    val searchResult: SearchResult = SearchResult(),
    val searching: Boolean = false,
    val replacing: Boolean = false,
    val replacement: String = "",
    val selectedMatch: Int = -1,
    val lineNumbers: String = "1",
    val lineStarts: List<Int> = listOf(0),
    val loading: Boolean = false,
    val formatting: Boolean = false,
    val saving: Boolean = false,
    val pendingSave: SaveSnapshot? = null,
    val message: String? = null
)

private fun FileMode.syntaxLanguage(): Language = when (this) {
    FileMode.JSON -> LanguageRegistry.languages.first { it.id == "json" }
    FileMode.YAML -> LanguageRegistry.languages.first { it.id == "yaml" }
    FileMode.TEXT -> LanguageRegistry.plain
}

fun EditorUiState.highlightLanguage(): Language {
    languageOverride?.let { return it }
    // Removed code grammars always open as plain text, even if content heuristics resemble YAML/JSON.
    if (entry?.name?.substringAfterLast('.', "")?.lowercase(java.util.Locale.ROOT) in
        setOf("kt", "kts", "java", "js", "mjs", "cjs")) return LanguageRegistry.plain
    val detected = entry?.let { LanguageRegistry.forFileName(it.name) } ?: LanguageRegistry.plain
    if (detected != LanguageRegistry.plain) return detected
    return mode.syntaxLanguage()
}

class EditorViewModel(
    private val openDocument: OpenDocument,
    private val formatDocument: FormatJsonDocument,
    private val saveCopy: SaveDocumentCopy,
    private val sessionRepository: SessionRepository? = null,
    private val fileSystem: FileSystem? = null,
    private val formatXmlDocument: FormatXmlDocument = FormatXmlDocument(),
    private val formatYamlDocument: FormatYamlDocument = FormatYamlDocument()
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state = mutableState.asStateFlow()
    private var openJob: Job? = null
    private var formatJob: Job? = null
    private var generation = 0L
    private var revision = 0L
    private val documents = linkedMapOf<DocumentId, EditorUiState>()
    private var savingDocument: EditorDocument? = null
    private var restoring = false
    private var checkpointJob: Job? = null
    private val searchJobs = mutableMapOf<DocumentId, Job>()
    private val closeQueue = ArrayDeque<DocumentId>()
    private var advancingCloseQueue = false

    init {
        if (sessionRepository != null && fileSystem != null) {
            restoring = true
            mutableState.value = state.value.copy(loading = true)
            viewModelScope.launch {
                val failures = mutableListOf<String>()
                try {
                    val saved = sessionRepository.load()
                    for (tab in saved.tabs) {
                        try {
                            val document = openDocument(fileSystem.stat(tab.id))
                            register(document.entry, document.content, document.mode)
                            val opened = documents.getValue(document.entry.id)
                            opened.inputSession!!.setSelection(tab.anchor.coerceIn(0, document.content.length),
                                tab.active.coerceIn(0, document.content.length))
                            opened.viewState.scrollX = tab.scrollX
                            opened.viewState.scrollY = tab.scrollY
                        } catch (exception: CancellationException) { throw exception
                        } catch (_: Exception) { failures += tab.id.value }
                    }
                    restoring = false
                    val active = saved.active?.takeIf { it in documents } ?: documents.keys.firstOrNull()
                    if (active != null) selectDocument(active)
                    else mutableState.value = state.value.copy(loading = false)
                    if (failures.isNotEmpty()) showMessage("No se pudieron restaurar ${failures.size} archivos. Comprueba los permisos o si fueron eliminados.")
                    checkpointSession()
                } catch (exception: CancellationException) { throw exception
                } catch (exception: Exception) {
                    mutableState.value = state.value.copy(loading = false,
                        message = "No se pudo restaurar la sesión: ${exception.message}")
                } finally { restoring = false }
            }
        }
    }

    private fun register(entry: FileEntry, content: String, mode: FileMode) {
        val input = EditorInputSession(EditorEngine(EditorDocument(PieceTableBuffer(content), entry.id)))
        input.addListener { textChanged ->
            if (documents[entry.id]?.inputSession === input) syncInput(input, textChanged)
        }
        val view = EditorViewState().also { it.onScrollChanged = ::scheduleCheckpoint }
        documents[entry.id] = derive(EditorUiState(entry = entry, value = TextFieldValue(content),
            mode = mode, inputSession = input, viewState = view))
    }

    private fun scheduleCheckpoint() {
        if (restoring || sessionRepository == null) return
        checkpointJob?.cancel()
        checkpointJob = viewModelScope.launch { delay(300); checkpointSession() }
    }

    fun checkpointSession() {
        if (restoring) return
        checkpointJob?.cancel()
        checkpointJob = null
        sessionRepository?.save(EditorSession(documents.map { (id, opened) ->
            val input = opened.inputSession!!
            SessionTab(id, input.anchor, input.active, opened.viewState.scrollX, opened.viewState.scrollY)
        }, state.value.entry?.id))
    }

    private fun tabs(active: DocumentId? = state.value.entry?.id) = documents.map { (id, document) ->
        EditorTab(id, document.entry!!.name, document.inputSession!!.engine.document.dirty, id == active)
    }

    private fun publish(document: EditorUiState) {
        val id = document.entry?.id ?: return
        val updated = document.copy(dirty = document.inputSession!!.engine.document.dirty, tabs = emptyList())
        documents[id] = updated
        mutableState.value = if (state.value.entry?.id == id) updated.copy(tabs = tabs())
            else state.value.copy(tabs = tabs())
        scheduleCheckpoint()
    }

    fun selectDocument(id: DocumentId) {
        if (restoring) return
        if (id !in documents) return
        state.value.inputSession?.finishComposingText()
        generation++
        openJob?.cancel()
        invalidateFormat()
        val current = state.value
        mutableState.value = documents.getValue(id).copy(loading = false, formatting = false,
            pendingSave = current.pendingSave, saving = current.saving, pendingClose = current.pendingClose,
            message = null, tabs = tabs(id), dirty = documents.getValue(id).inputSession!!.engine.document.dirty)
        scheduleCheckpoint()
    }

    fun open(entry: FileEntry) {
        if (restoring) return
        if (entry.id in documents) { selectDocument(entry.id); return }
        state.value.inputSession?.finishComposingText()
        val token = ++generation
        revision++
        openJob?.cancel()
        formatJob?.cancel()
        mutableState.value = state.value.copy(loading = true, formatting = false, message = null)
        openJob = viewModelScope.launch {
            try {
                val document = openDocument(entry)
                if (token != generation) return@launch
                register(document.entry, document.content, document.mode)
                selectDocument(document.entry.id)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (token == generation) mutableState.value = state.value.copy(loading = false,
                    message = "No se pudo abrir el archivo: ${exception.message}")
            }
        }
    }

    fun openExternal(entry: FileEntry) {
        viewModelScope.launch {
            while (restoring) delay(20)
            open(entry)
        }
    }

    fun edit(value: TextFieldValue) {
        if (!canEdit(state.value)) return
        val input = state.value.inputSession ?: return
        val old = state.value.value.text
        if (value.text != old) {
            var start = 0
            while (start < old.length && start < value.text.length && old[start] == value.text[start]) start++
            var oldEnd = old.length
            var newEnd = value.text.length
            while (oldEnd > start && newEnd > start && old[oldEnd - 1] == value.text[newEnd - 1]) { oldEnd--; newEnd-- }
            input.execute(ReplaceTextCommand(CoreTextRange(TextOffset(start), TextOffset(oldEnd)), value.text.substring(start, newEnd)))
        }
        input.setSelection(value.selection.start, value.selection.end)
    }

    private fun syncInput(input: EditorInputSession, textChanged: Boolean) {
        val active = state.value.inputSession === input
        if (textChanged && active) invalidateFormat()
        val current = if (active) state.value else documents[input.engine.document.id] ?: return
        val text = if (textChanged) input.buffer.getText(0, input.buffer.length).toString() else current.value.text
        val composing = input.composition?.let { TextRange(it.start.value, it.end.value) }
        val updated = current.copy(value = TextFieldValue(text, TextRange(input.anchor, input.active), composing),
            contentVersion = current.contentVersion + 1, canUndo = input.canUndo, canRedo = input.canRedo)
        current.viewState.cursor = input.engine.cursor
        current.viewState.selection = input.engine.selection
        publish(if (textChanged) derive(updated) else updated)
        if (textChanged) scheduleSearch(input.engine.document.id)
    }

    fun setMode(mode: FileMode) {
        setLanguage(mode.syntaxLanguage())
    }

    fun setLanguage(language: Language) {
        invalidateFormat()
        val mode = when (language.id) {
            "json" -> FileMode.JSON
            "yaml" -> FileMode.YAML
            else -> FileMode.TEXT
        }
        publish(state.value.copy(mode = mode, languageOverride = language))
    }

    fun setSearch(search: String) {
        publish(state.value.copy(search = search))
        state.value.entry?.id?.let(::scheduleSearch)
    }

    fun setSearchOptions(options: SearchOptions) {
        publish(state.value.copy(searchOptions = options))
        state.value.entry?.id?.let(::scheduleSearch)
    }

    fun setReplacement(text: String) { publish(state.value.copy(replacement = text)) }

    private fun scheduleSearch(id: DocumentId) {
        searchJobs.remove(id)?.cancel()
        val snapshot = documents[id] ?: return
        publish(snapshot.copy(searchResult = SearchResult(), occurrences = 0, selectedMatch = -1,
            searching = snapshot.search.isNotEmpty(), replacing = false))
        if (snapshot.search.isEmpty()) return
        searchJobs[id] = viewModelScope.launch {
            delay(120)
            val result = withContext(Dispatchers.Default) {
                DocumentSearch.find(snapshot.value.text, snapshot.search, snapshot.searchOptions,
                    checkCancelled = { ensureActive() })
            }
            val current = documents[id] ?: return@launch
            publish(current.copy(searchResult = result, occurrences = result.matches.size, searching = false))
        }
    }

    fun findNext(backwards: Boolean = false) {
        val current = state.value
        if (current.loading || current.searching || current.replacing) return
        val matches = current.searchResult.matches
        if (matches.isEmpty()) return
        val input = current.inputSession ?: return
        input.finishComposingText()
        val selected = current.selectedMatch.takeIf { index ->
            index in matches.indices && matches[index].start == input.selectionStart && matches[index].end == input.selectionEnd
        }
        val index = if (selected != null) Math.floorMod(selected + if (backwards) -1 else 1, matches.size)
            else if (backwards) matches.indexOfLast { it.end <= input.selectionStart }.takeIf { it >= 0 } ?: matches.lastIndex
            else matches.indexOfFirst { it.start >= input.selectionEnd }.takeIf { it >= 0 } ?: 0
        val match = matches[index]
        input.setSelection(match.start, match.end)
        publish(state.value.copy(selectedMatch = index))
    }

    fun replace(all: Boolean = false) {
        state.value.inputSession?.finishComposingText()
        val snapshot = state.value
        if (!canEdit(snapshot) || snapshot.searching || snapshot.replacing || snapshot.search.isEmpty()) return
        if (snapshot.searchResult.error != null || snapshot.searchResult.matches.isEmpty()) return
        val input = snapshot.inputSession!!
        val selected = snapshot.searchResult.matches.firstOrNull { it.start == input.selectionStart && it.end == input.selectionEnd }
        if (!all && selected == null) { findNext(); return }
        val id = snapshot.entry!!.id
        val version = input.engine.document.revision
        val documentToken = generation
        searchJobs.remove(id)?.cancel()
        publish(snapshot.copy(replacing = true))
        searchJobs[id] = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                DocumentSearch.find(snapshot.value.text, snapshot.search, snapshot.searchOptions, snapshot.replacement,
                    replacementMatch = if (all) null else selected) { ensureActive() }
            }
            val current = documents[id] ?: return@launch
            publish(current.copy(replacing = false))
            if (input.engine.document.revision != version || documentToken != generation || state.value.entry?.id != id ||
                current.search != snapshot.search || current.searchOptions != snapshot.searchOptions ||
                current.replacement != snapshot.replacement ||
                (!all && (input.selectionStart != selected!!.start || input.selectionEnd != selected.end))) return@launch
            if (result.error != null || result.truncated) {
                showMessage(result.error ?: "Demasiadas coincidencias. Acota la búsqueda antes de reemplazar.")
                return@launch
            }
            val changes = if (all) result.matches else result.matches.filter { it.start == selected!!.start && it.end == selected.end }
            if (changes.isNotEmpty()) input.execute(ReplaceMatchesCommand(changes))
        }
    }

    fun undo() {
        if (canEdit(state.value)) state.value.inputSession?.execute(UndoCommand)
    }

    fun redo() {
        if (canEdit(state.value)) state.value.inputSession?.execute(RedoCommand)
    }

    fun goToLine(line: Int) {
        if (state.value.loading) return
        val input = state.value.inputSession ?: return
        val index = if (line <= 1) 0 else if (line > input.buffer.lineCount) input.buffer.length else input.buffer.getLineStart(line - 1)
        input.execute(MoveCursorCommand(TextOffset(index)), false)
    }

    fun goToStart() {
        if (state.value.loading) return
        state.value.inputSession?.execute(MoveCursorCommand(TextOffset(0)), false)
    }

    fun goToEnd() {
        if (state.value.loading) return
        val input = state.value.inputSession ?: return
        input.execute(MoveCursorCommand(TextOffset(input.buffer.length)), false)
    }

    fun format() {
        state.value.inputSession?.finishComposingText()
        val snapshot = state.value
        if (!canEdit(snapshot)) return
        invalidateFormat()
        val token = revision
        val documentToken = generation
        mutableState.value = state.value.copy(formatting = true, message = null)
        formatJob = viewModelScope.launch {
            try {
                val result = when (snapshot.highlightLanguage().id) {
                    "xml" -> formatXmlDocument(snapshot.value.text)
                    "yaml" -> formatYamlDocument(snapshot.value.text)
                    "json" -> formatDocument(snapshot.value.text, FileMode.JSON)
                    else -> snapshot.value.text
                }
                if (token != revision || documentToken != generation) return@launch
                formatJob = null
                if (result != snapshot.value.text) edit(TextFieldValue(result))
                mutableState.value = state.value.copy(formatting = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (token == revision && documentToken == generation) mutableState.value = state.value.copy(
                    formatting = false, message = "No se pudo formatear: ${exception.message}")
            }
        }
    }

    fun prepareSave(): SaveSnapshot? {
        state.value.inputSession?.finishComposingText()
        val current = state.value
        val entry = current.entry ?: return null
        if (!canEdit(current) || current.pendingSave != null || current.saving) return null
        val document = current.inputSession!!.engine.document
        val snapshot = SaveSnapshot(entry.name, current.value.text, document, document.revision)
        mutableState.value = current.copy(pendingSave = snapshot)
        return snapshot
    }

    fun completeSave(destination: DocumentId?) {
        val snapshot = state.value.pendingSave ?: return
        mutableState.value = state.value.copy(pendingSave = null)
        if (destination == null) return
        if (destination != snapshot.document.id && destination in documents) {
            showMessage("El destino está abierto en otra pestaña. Guarda desde esa pestaña.")
            return
        }
        writeSnapshot(snapshot, destination, closeAfter = false)
    }

    fun save() { saveDocument(state.value.entry?.id ?: return, closeAfter = false) }

    fun saveAll() {
        if (restoring || state.value.loading || state.value.saving || state.value.pendingSave != null ||
            state.value.pendingClose != null) return
        val snapshots = documents.values.toList().mapNotNull { opened ->
            if (!canEdit(opened)) return@mapNotNull null
            val input = opened.inputSession!!
            input.finishComposingText()
            val document = input.engine.document
            if (!document.dirty) return@mapNotNull null
            SaveSnapshot(opened.entry!!.name, document.buffer.getText(0, document.buffer.length).toString(),
                document, document.revision)
        }
        if (snapshots.isEmpty()) return
        mutableState.value = state.value.copy(saving = true, message = null)
        viewModelScope.launch {
            var failure: Exception? = null
            for (snapshot in snapshots) {
                try {
                    saveCopy(snapshot.document.id, snapshot.content)
                    snapshot.document.markSaved(snapshot.revision)
                    mutableState.value = state.value.copy(
                        dirty = state.value.inputSession?.engine?.document?.dirty ?: false, tabs = tabs())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    if (failure == null) failure = exception
                }
            }
            mutableState.value = state.value.copy(saving = false,
                dirty = state.value.inputSession?.engine?.document?.dirty ?: false, tabs = tabs(),
                message = failure?.let { "No se pudo guardar el archivo: ${it.message}" }
                    ?: "Archivo guardado correctamente.")
        }
    }

    private fun saveDocument(id: DocumentId, closeAfter: Boolean) {
        if (state.value.saving || state.value.pendingSave != null) return
        val opened = documents[id] ?: return
        if (!canEdit(opened)) return
        opened.inputSession!!.finishComposingText()
        val document = opened.inputSession.engine.document
        val snapshot = SaveSnapshot(opened.entry!!.name,
            document.buffer.getText(0, document.buffer.length).toString(), document, document.revision)
        writeSnapshot(snapshot, id, closeAfter)
    }

    private fun writeSnapshot(snapshot: SaveSnapshot, destination: DocumentId, closeAfter: Boolean) {
        savingDocument = snapshot.document
        mutableState.value = state.value.copy(saving = true, message = null)
        viewModelScope.launch {
            try {
                saveCopy(destination, snapshot.content)
                if (destination == snapshot.document.id) snapshot.document.markSaved(snapshot.revision)
                savingDocument = null
                val activeDirty = state.value.inputSession?.engine?.document?.dirty ?: false
                mutableState.value = state.value.copy(saving = false, dirty = activeDirty, tabs = tabs(),
                    message = if (destination == snapshot.document.id) "Archivo guardado correctamente." else "Copia guardada correctamente.")
                if (closeAfter && state.value.pendingClose == snapshot.document.id) {
                    if (!snapshot.document.dirty) removeDocument(snapshot.document.id)
                    else closeQueue.clear()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                savingDocument = null
                if (closeAfter) closeQueue.clear()
                mutableState.value = state.value.copy(saving = false, message = "No se pudo guardar el archivo: ${exception.message}")
            }
        }
    }

    fun requestClose(id: DocumentId) {
        if (state.value.pendingClose != null) return
        if (state.value.saving) { showMessage("Espera a que termine el guardado."); return }
        val opened = documents[id] ?: return
        opened.inputSession!!.finishComposingText()
        if (savingDocument === opened.inputSession.engine.document) {
            showMessage("Espera a que termine el guardado.")
            return
        }
        if (opened.inputSession.engine.document.dirty) mutableState.value = state.value.copy(pendingClose = id)
        else removeDocument(id)
    }

    fun requestCloseAll() { startCloseSequence(documents.keys.toList()) }

    fun requestCloseOthers() {
        val active = state.value.entry?.id ?: return
        startCloseSequence(documents.keys.filter { it != active })
    }

    private fun startCloseSequence(ids: List<DocumentId>) {
        if (restoring || state.value.loading || state.value.pendingClose != null || state.value.saving ||
            state.value.pendingSave != null) return
        closeQueue.clear()
        closeQueue.addAll(ids)
        continueCloseSequence()
    }

    private fun continueCloseSequence() {
        if (advancingCloseQueue) return
        advancingCloseQueue = true
        try {
            while (closeQueue.isNotEmpty()) {
                val id = closeQueue.removeFirst()
                val opened = documents[id] ?: continue
                opened.inputSession!!.finishComposingText()
                if (savingDocument === opened.inputSession.engine.document) {
                    closeQueue.clear()
                    showMessage("Espera a que termine el guardado.")
                    break
                }
                if (opened.inputSession.engine.document.dirty) {
                    mutableState.value = state.value.copy(pendingClose = id)
                    break
                }
                removeDocument(id)
            }
        } finally { advancingCloseQueue = false }
    }

    fun cancelClose() {
        closeQueue.clear()
        mutableState.value = state.value.copy(pendingClose = null)
    }
    fun discardAndClose() {
        val id = state.value.pendingClose ?: return
        if (savingDocument?.id != id) removeDocument(id)
    }
    fun saveAndClose() { saveDocument(state.value.pendingClose ?: return, closeAfter = true) }

    private fun removeDocument(id: DocumentId) {
        val keys = documents.keys.toList()
        val index = keys.indexOf(id)
        if (index < 0) return
        documents.remove(id)
        searchJobs.remove(id)?.cancel()
        val current = state.value
        mutableState.value = current.copy(pendingClose = null,
            pendingSave = current.pendingSave?.takeUnless { it.document.id == id }, tabs = tabs())
        if (current.entry?.id == id) {
            val next = documents.keys.toList().getOrNull(index.coerceAtMost(documents.size - 1))
            if (next != null) selectDocument(next)
            else {
                generation++
                openJob?.cancel()
                invalidateFormat()
                mutableState.value = EditorUiState(saving = current.saving, message = current.message,
                    pendingSave = state.value.pendingSave)
            }
        }
        scheduleCheckpoint()
        continueCloseSequence()
    }

    private fun canEdit(document: EditorUiState): Boolean =
        document.entry != null && !document.loading && document.inputSession != null

    fun showMessage(message: String) { mutableState.value = state.value.copy(message = message) }
    fun dismissMessage() { mutableState.value = state.value.copy(message = null) }

    private fun invalidateFormat() {
        revision++
        formatJob?.cancel()
        mutableState.value = state.value.copy(formatting = false)
    }

    private fun derive(current: EditorUiState, lines: Boolean = true): EditorUiState {
        val content = current.value.text
        val starts = if (lines) buildList {
            add(0)
            content.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
        } else current.lineStarts
        return current.copy(lineStarts = starts,
            lineNumbers = if (lines) (1..starts.size.coerceAtMost(1_000)).joinToString("\n") else current.lineNumbers)
    }
}
