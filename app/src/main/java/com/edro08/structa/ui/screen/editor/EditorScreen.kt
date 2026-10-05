package com.edro08.structa.ui.screen.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileMode
import com.edro08.structa.ui.editor.component.StructaEditor
import com.edro08.structa.ui.editor.input.EditorAction
import com.edro08.structa.ui.editor.input.applicationActionFor
import com.edro08.structa.domain.editor.search.SearchOptions
import com.edro08.structa.ui.component.formatBytes

private val FileMode.displayName: String
    get() = when (this) {
        FileMode.TEXT -> "Texto"
        FileMode.JSON -> "JSON"
        FileMode.YAML -> "YAML"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(state: EditorUiState, onBack: () -> Unit, onExplore: () -> Unit,
    onMode: (FileMode) -> Unit, onSearch: (String) -> Unit,
    onFormat: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onLine: (Int) -> Unit, onSaveAs: () -> Unit,
    onMessage: (String) -> Unit,
    onSave: () -> Unit, onSelectDocument: (DocumentId) -> Unit, onCloseDocument: (DocumentId) -> Unit,
    onCancelClose: () -> Unit, onDiscardClose: () -> Unit, onSaveClose: () -> Unit,
    onSearchOptions: (SearchOptions) -> Unit = {}, onReplacement: (String) -> Unit = {},
    onFindNext: (Boolean) -> Unit = {}, onReplace: (Boolean) -> Unit = {}, onQuickOpen: () -> Unit = {},
    editorFont: com.edro08.structa.domain.settings.EditorFont = com.edro08.structa.domain.settings.EditorFont.MONOSPACE,
    editorFontSize: Int = 14) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showReplace by rememberSaveable { mutableStateOf(false) }
    var showFileMenu by remember { mutableStateOf(false) }
    var showEditMenu by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }
    var showGoToLine by rememberSaveable { mutableStateOf(false) }
    var requestedLine by rememberSaveable { mutableStateOf("") }
    val entry = state.entry
    val searchFocus = remember { FocusRequester() }
    val lineFocus = remember { FocusRequester() }
    LaunchedEffect(showSearch, showReplace) { if (showSearch && entry != null && !state.loading) searchFocus.requestFocus() }
    fun enabled(action: EditorAction): Boolean = when (action) {
        EditorAction.QUICK_OPEN, EditorAction.COMMAND_PALETTE -> true
        EditorAction.SAVE -> entry != null && !state.loading && !state.saving && state.pendingSave == null
        EditorAction.REPLACE -> entry != null && !state.loading
        EditorAction.UNDO -> !state.loading && state.canUndo
        EditorAction.REDO -> !state.loading && state.canRedo
        EditorAction.FORMAT -> entry != null && !state.loading && !state.formatting
        EditorAction.FIND_NEXT, EditorAction.FIND_PREVIOUS -> !state.loading && !state.searching && !state.replacing && state.occurrences > 0
        else -> entry != null && !state.loading
    }
    fun dispatch(action: EditorAction) {
        if (!enabled(action)) return
        state.inputSession?.finishComposingText()
        when (action) {
            EditorAction.SAVE -> onSave()
            EditorAction.FIND -> { showReplace = false; showSearch = true }
            EditorAction.REPLACE -> { showReplace = true; showSearch = true }
            EditorAction.GO_TO_LINE -> { requestedLine = ""; showGoToLine = true }
            EditorAction.QUICK_OPEN -> onQuickOpen()
            EditorAction.COMMAND_PALETTE -> { showFileMenu = false; showEditMenu = true }
            EditorAction.FIND_NEXT -> onFindNext(false)
            EditorAction.FIND_PREVIOUS -> onFindNext(true)
            EditorAction.UNDO -> onUndo()
            EditorAction.REDO -> onRedo()
            EditorAction.FORMAT -> onFormat()
            EditorAction.CLOSE -> entry?.let { onCloseDocument(it.id) }
        }
    }
    Scaffold(modifier = Modifier.onPreviewKeyEvent { event ->
        applicationActionFor(event.nativeKeyEvent)?.let { dispatch(it); true } ?: false
    }, topBar = {
        TopAppBar(title = {
            Column {
                Text((entry?.name ?: "Editor") + if (state.tabs.size == 1 && state.dirty) " ●" else "", maxLines = 1)
                if (entry != null) {
                    val language = remember(entry.name) {
                        com.edro08.structa.domain.editor.syntax.LanguageRegistry.forFileName(entry.name)
                    }
                    val limit = if (state.value.text.length > com.edro08.structa.domain.editor.syntax.IncrementalHighlighter.MAX_TEXT_LENGTH)
                        " · resaltado desactivado por tamaño" else ""
                    Text("${language.title} · ${formatBytes(entry.sizeBytes)}$limit", maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            } },
            actions = {
                Box {
                    OutlinedButton(onClick = { showEditMenu = false; showFileMenu = true },
                        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Archivo") }
                    DropdownMenu(expanded = showFileMenu, onDismissRequest = { showFileMenu = false }) {
                        DropdownMenuItem(text = { Text("Abrir") }, onClick = { showFileMenu = false; dispatch(EditorAction.QUICK_OPEN) })
                        DropdownMenuItem(text = { Text("Guardar") }, enabled = enabled(EditorAction.SAVE),
                            onClick = { showFileMenu = false; dispatch(EditorAction.SAVE) })
                        DropdownMenuItem(text = { Text("Guardar Como...") }, enabled = enabled(EditorAction.SAVE),
                            onClick = { showFileMenu = false; state.inputSession?.finishComposingText(); onSaveAs() })
                        DropdownMenuItem(text = { Text("Cerrar") }, enabled = enabled(EditorAction.CLOSE),
                            onClick = { showFileMenu = false; dispatch(EditorAction.CLOSE) })
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { dispatch(EditorAction.COMMAND_PALETTE) },
                        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Editor") }
                    DropdownMenu(expanded = showEditMenu, onDismissRequest = { showEditMenu = false }) {
                        listOf(EditorAction.FIND, EditorAction.REPLACE, EditorAction.GO_TO_LINE,
                            EditorAction.UNDO, EditorAction.REDO, EditorAction.FORMAT).forEach { action ->
                            DropdownMenuItem(text = { Text(action.title) }, enabled = enabled(action),
                                onClick = { showEditMenu = false; dispatch(action) })
                        }
                        DropdownMenuItem(text = { Text("Formato") }, enabled = entry != null && !state.loading,
                            onClick = { showEditMenu = false; showFormatMenu = true })
                    }
                }
                Spacer(Modifier.width(8.dp))
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.tabs.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    state.tabs.forEach { tab ->
                        Surface(color = if (tab.active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onSelectDocument(tab.documentId) }) {
                                    Text(tab.title + if (tab.dirty) " ●" else "")
                                }
                                TextButton(onClick = { onCloseDocument(tab.documentId) },
                                    modifier = Modifier.semantics { contentDescription = "Cerrar ${tab.title}" }) { Text("×") }
                            }
                        }
                    }
                }
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Abriendo archivo...", Modifier.padding(16.dp))
            } else if (entry == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay un archivo abierto", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Explora una carpeta y selecciona cualquier archivo para abrirlo como texto, JSON o YAML.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onExplore) { Text("Ir a explorar") }
                }
            } else {
                 if (state.formatting || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                  if (showSearch) {
                     TextField(state.search, onValueChange = onSearch,
                         modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).focusRequester(searchFocus).onPreviewKeyEvent {
                             if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { onFindNext(it.isShiftPressed); true }
                             else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { showSearch = false; true } else false
                         }, singleLine = true, label = { Text("Buscar dentro del archivo") })
                     Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                         FilterChip(selected = state.searchOptions.caseSensitive,
                             onClick = { onSearchOptions(state.searchOptions.copy(caseSensitive = !state.searchOptions.caseSensitive)) }, label = { Text("Aa") })
                         FilterChip(selected = state.searchOptions.wholeWord,
                             onClick = { onSearchOptions(state.searchOptions.copy(wholeWord = !state.searchOptions.wholeWord)) }, label = { Text("Palabra") })
                         FilterChip(selected = state.searchOptions.regex,
                             onClick = { onSearchOptions(state.searchOptions.copy(regex = !state.searchOptions.regex)) }, label = { Text("Regex") })
                         TextButton(onClick = { dispatch(EditorAction.FIND_PREVIOUS) }, enabled = enabled(EditorAction.FIND_PREVIOUS)) { Text("Anterior") }
                         TextButton(onClick = { dispatch(EditorAction.FIND_NEXT) }, enabled = enabled(EditorAction.FIND_NEXT)) { Text("Siguiente") }
                         TextButton(onClick = { showSearch = false }) { Text("Cerrar búsqueda") }
                     }
                     if (state.searching || state.replacing) LinearProgressIndicator(Modifier.fillMaxWidth())
                     Text(state.searchResult.error ?: "${state.occurrences}${if (state.searchResult.truncated) "+ (acota la búsqueda)" else ""} coincidencias",
                         Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
                      if (showReplace) {
                         TextField(state.replacement, onReplacement, singleLine = true,
                             label = { Text("Reemplazo") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                         Row {
                             TextButton(onClick = { onReplace(false) }, enabled = enabled(EditorAction.FIND_NEXT)) { Text("Reemplazar actual") }
                             TextButton(onClick = { onReplace(true) }, enabled = enabled(EditorAction.FIND_NEXT) && !state.searchResult.truncated) { Text("Reemplazar todo") }
                         }
                     }
                }
                  state.inputSession?.let { input ->
                      StructaEditor(input.engine, Modifier.weight(1f).fillMaxWidth(),
                           contentVersion = state.contentVersion, cursorVisible = true,
                            editable = true, inputSession = input, viewState = state.viewState,
                            fileName = entry.name, searchMatches = state.searchResult.matches,
                             selectedMatch = state.selectedMatch, font = editorFont, fontSize = editorFontSize,
                             onEditorAction = ::dispatch)
                  }
            }
        }
    }
    if (showGoToLine) {
        AlertDialog(onDismissRequest = { showGoToLine = false }, title = { Text("Ir a linea") },
            text = {
                LaunchedEffect(Unit) { withFrameNanos { }; lineFocus.requestFocus() }
                TextField(requestedLine, onValueChange = { requestedLine = it.filter(Char::isDigit) },
                modifier = Modifier.focusRequester(lineFocus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && requestedLine.toIntOrNull()?.let { n -> n > 0 } == true) {
                        onLine(requestedLine.toInt()); showGoToLine = false; true
                    } else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { showGoToLine = false; true } else false
                }, singleLine = true, label = { Text("Numero de linea") }) },
            confirmButton = { TextButton(enabled = requestedLine.toIntOrNull()?.let { it > 0 } == true,
                onClick = { requestedLine.toIntOrNull()?.let(onLine); showGoToLine = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { showGoToLine = false }) { Text("Cancelar") } })
    }
    if (showFormatMenu) {
        AlertDialog(onDismissRequest = { showFormatMenu = false }, title = { Text("Formato") },
            text = { Column {
                FileMode.entries.forEach { mode ->
                    TextButton(enabled = entry != null && !state.loading,
                        onClick = { state.inputSession?.finishComposingText(); onMode(mode); showFormatMenu = false }) {
                        RadioButton(selected = state.mode == mode, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(mode.displayName)
                    }
                }
            } }, confirmButton = {},
            dismissButton = { TextButton(onClick = { showFormatMenu = false }) { Text("Cancelar") } })
    }
    state.pendingClose?.let { id ->
        val title = state.tabs.firstOrNull { it.documentId == id }?.title.orEmpty()
        AlertDialog(onDismissRequest = onCancelClose,
            title = { Text("Cerrar $title") },
            text = { Text("Hay cambios sin guardar. ¿Quieres guardarlos antes de cerrar?") },
            confirmButton = { TextButton(onClick = onSaveClose, enabled = !state.saving && state.pendingSave == null) { Text("Guardar y cerrar") } },
            dismissButton = {
                Row {
                    TextButton(onClick = onDiscardClose, enabled = !state.saving) { Text("Descartar") }
                    TextButton(onClick = onCancelClose) { Text("Cancelar") }
                }
            })
    }
}
