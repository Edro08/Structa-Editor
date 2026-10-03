package com.edro08.structa.ui.screen.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edro08.structa.application.document.OpenDocument
import com.edro08.structa.application.document.SaveDocumentCopy
import com.edro08.structa.application.editor.FormatJsonDocument
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.FileMode
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.document.EditorDocument
import com.edro08.structa.domain.editor.command.*
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange as CoreTextRange
import com.edro08.structa.ui.editor.input.EditorInputSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SaveSnapshot(val name: String, val content: String)

data class EditorUiState(
    val entry: FileEntry? = null,
    val value: TextFieldValue = TextFieldValue(),
    val mode: FileMode = FileMode.TEXT,
    val inputSession: EditorInputSession? = null,
    val contentVersion: Long = 0L,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val search: String = "",
    val occurrences: Int = 0,
    val lineNumbers: String = "1",
    val lineStarts: List<Int> = listOf(0),
    val loading: Boolean = false,
    val formatting: Boolean = false,
    val saving: Boolean = false,
    val pendingSave: SaveSnapshot? = null,
    val message: String? = null
)

class EditorViewModel(
    private val openDocument: OpenDocument,
    private val formatDocument: FormatJsonDocument,
    private val saveCopy: SaveDocumentCopy
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state = mutableState.asStateFlow()
    private var openJob: Job? = null
    private var formatJob: Job? = null
    private var generation = 0L
    private var revision = 0L

    fun open(entry: FileEntry) {
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
                val previous = state.value
                val input = EditorInputSession(EditorEngine(EditorDocument(PieceTableBuffer(document.content), document.entry.id)))
                input.addListener { textChanged ->
                    if (state.value.inputSession === input && !state.value.loading) syncInput(input, textChanged)
                }
                mutableState.value = derive(EditorUiState(entry = document.entry,
                    value = TextFieldValue(document.content), mode = document.mode,
                    inputSession = input,
                    pendingSave = previous.pendingSave, saving = previous.saving))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (token == generation) mutableState.value = state.value.copy(loading = false,
                    message = "No se pudo abrir el archivo: ${exception.message}")
            }
        }
    }

    fun edit(value: TextFieldValue) {
        if (state.value.loading) return
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
        if (textChanged) invalidateFormat()
        val current = state.value
        val text = if (textChanged) input.buffer.getText(0, input.buffer.length).toString() else current.value.text
        val composing = input.composition?.let { TextRange(it.start.value, it.end.value) }
        val updated = current.copy(value = TextFieldValue(text, TextRange(input.anchor, input.active), composing),
            contentVersion = current.contentVersion + 1, canUndo = input.canUndo, canRedo = input.canRedo)
        mutableState.value = if (textChanged) derive(updated) else updated
    }

    fun setMode(mode: FileMode) {
        invalidateFormat()
        mutableState.value = state.value.copy(mode = mode)
    }

    fun setSearch(search: String) { mutableState.value = derive(state.value.copy(search = search), lines = false) }

    fun undo() {
        if (!state.value.loading) state.value.inputSession?.execute(UndoCommand)
    }

    fun redo() {
        if (!state.value.loading) state.value.inputSession?.execute(RedoCommand)
    }

    fun goToLine(line: Int) {
        if (state.value.loading) return
        val input = state.value.inputSession ?: return
        val index = if (line <= 1) 0 else if (line > input.buffer.lineCount) input.buffer.length else input.buffer.getLineStart(line - 1)
        input.execute(MoveCursorCommand(TextOffset(index)), false)
    }

    fun format() {
        state.value.inputSession?.finishComposingText()
        val snapshot = state.value
        if (snapshot.entry == null || snapshot.loading) return
        invalidateFormat()
        val token = revision
        val documentToken = generation
        mutableState.value = state.value.copy(formatting = true, message = null)
        formatJob = viewModelScope.launch {
            try {
                val result = formatDocument(snapshot.value.text, snapshot.mode)
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
        if (current.loading || current.pendingSave != null || current.saving) return null
        val snapshot = SaveSnapshot(entry.name, current.value.text)
        mutableState.value = current.copy(pendingSave = snapshot)
        return snapshot
    }

    fun completeSave(destination: DocumentId?) {
        val snapshot = state.value.pendingSave ?: return
        mutableState.value = state.value.copy(pendingSave = null)
        if (destination == null) return
        mutableState.value = state.value.copy(saving = true, message = null)
        viewModelScope.launch {
            try {
                saveCopy(destination, snapshot.content)
                mutableState.value = state.value.copy(saving = false, message = "Archivo guardado correctamente.")
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = state.value.copy(saving = false, message = "No se pudo guardar el archivo: ${exception.message}")
            }
        }
    }

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
        var count = 0
        if (current.search.isNotBlank()) {
            var index = content.indexOf(current.search, ignoreCase = true)
            while (index >= 0) {
                count++
                index = content.indexOf(current.search, index + current.search.length, ignoreCase = true)
            }
        }
        return current.copy(occurrences = count, lineStarts = starts,
            lineNumbers = if (lines) (1..starts.size.coerceAtMost(1_000)).joinToString("\n") else current.lineNumbers)
    }
}
