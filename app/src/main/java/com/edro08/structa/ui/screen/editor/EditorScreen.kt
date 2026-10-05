package com.edro08.structa.ui.screen.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileMode
import com.edro08.structa.ui.editor.component.StructaEditor
import com.edro08.structa.ui.editor.input.EditorAction
import com.edro08.structa.ui.editor.input.applicationActionFor
import com.edro08.structa.domain.editor.search.SearchOptions
import com.edro08.structa.ui.component.formatBytes
import com.edro08.structa.ui.component.EmptyScreen
import com.edro08.structa.ui.component.StructaTopBar
import com.edro08.structa.ui.theme.StructaSpacing

@Composable
private fun modeName(mode: FileMode): String = stringResource(when (mode) {
    FileMode.TEXT -> R.string.editor_text_mode
    FileMode.JSON -> R.string.editor_json_mode
    FileMode.YAML -> R.string.editor_yaml_mode
})

@Composable
private fun actionName(action: EditorAction): String = stringResource(when (action) {
    EditorAction.SAVE -> R.string.editor_save
    EditorAction.FIND -> R.string.editor_find
    EditorAction.REPLACE -> R.string.editor_replace
    EditorAction.GO_TO_LINE -> R.string.editor_go_to_line
    EditorAction.QUICK_OPEN -> R.string.editor_open
    EditorAction.COMMAND_PALETTE -> R.string.editor_edit_menu
    EditorAction.FIND_NEXT -> R.string.editor_next
    EditorAction.FIND_PREVIOUS -> R.string.editor_previous
    EditorAction.UNDO -> R.string.editor_undo
    EditorAction.REDO -> R.string.editor_redo
    EditorAction.FORMAT -> R.string.editor_format_document
    EditorAction.CLOSE -> R.string.editor_close
})

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
        StructaTopBar(title = {
            Column {
                 val title = entry?.name ?: stringResource(R.string.editor_title)
                 Text(if (state.tabs.size == 1 && state.dirty) stringResource(R.string.editor_dirty_title, title) else title, maxLines = 1)
                if (entry != null) {
                     val language = remember(entry.name) {
                         com.edro08.structa.domain.editor.syntax.LanguageRegistry.forFileName(entry.name)
                     }
                     val languageName = stringResource(when (language.id) {
                         "kotlin" -> R.string.editor_language_kotlin
                         "java" -> R.string.editor_language_java
                         "go" -> R.string.editor_language_go
                         "json" -> R.string.editor_language_json
                         "yaml" -> R.string.editor_language_yaml
                         "markdown" -> R.string.editor_language_markdown
                         else -> R.string.editor_language_plain
                     })
                     val info = if (state.value.text.length > com.edro08.structa.domain.editor.syntax.IncrementalHighlighter.MAX_TEXT_LENGTH)
                         R.string.editor_document_info_syntax_limited else R.string.editor_document_info
                     Text(stringResource(info, languageName, formatBytes(entry.sizeBytes)), maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }, onBack = onBack,
            actions = {
                Box {
                    OutlinedButton(onClick = { showEditMenu = false; showFileMenu = true },
                         shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text(stringResource(R.string.editor_file_menu)) }
                    DropdownMenu(expanded = showFileMenu, onDismissRequest = { showFileMenu = false }) {
                         DropdownMenuItem(text = { Text(stringResource(R.string.editor_open)) }, onClick = { showFileMenu = false; dispatch(EditorAction.QUICK_OPEN) })
                         DropdownMenuItem(text = { Text(stringResource(R.string.editor_save)) }, enabled = enabled(EditorAction.SAVE),
                            onClick = { showFileMenu = false; dispatch(EditorAction.SAVE) })
                         DropdownMenuItem(text = { Text(stringResource(R.string.editor_save_as)) }, enabled = enabled(EditorAction.SAVE),
                            onClick = { showFileMenu = false; state.inputSession?.finishComposingText(); onSaveAs() })
                         DropdownMenuItem(text = { Text(stringResource(R.string.editor_close)) }, enabled = enabled(EditorAction.CLOSE),
                            onClick = { showFileMenu = false; dispatch(EditorAction.CLOSE) })
                    }
                }
                Spacer(Modifier.width(StructaSpacing.compact))
                Box {
                    OutlinedButton(onClick = { dispatch(EditorAction.COMMAND_PALETTE) },
                          shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text(stringResource(R.string.editor_edit_menu)) }
                    DropdownMenu(expanded = showEditMenu, onDismissRequest = { showEditMenu = false }) {
                        listOf(EditorAction.FIND, EditorAction.REPLACE, EditorAction.GO_TO_LINE,
                            EditorAction.UNDO, EditorAction.REDO, EditorAction.FORMAT).forEach { action ->
                             DropdownMenuItem(text = { Text(actionName(action)) }, enabled = enabled(action),
                                onClick = { showEditMenu = false; dispatch(action) })
                        }
                         DropdownMenuItem(text = { Text(stringResource(R.string.editor_format)) }, enabled = entry != null && !state.loading,
                            onClick = { showEditMenu = false; showFormatMenu = true })
                    }
                }
                Spacer(Modifier.width(StructaSpacing.compact))
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.tabs.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    state.tabs.forEach { tab ->
                        Surface(color = if (tab.active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onSelectDocument(tab.documentId) }) {
                                     Text(if (tab.dirty) stringResource(R.string.editor_dirty_title, tab.title) else tab.title)
                                }
                                 val closeDescription = stringResource(R.string.editor_close_named, tab.title)
                                 TextButton(onClick = { onCloseDocument(tab.documentId) },
                                     modifier = Modifier.semantics { contentDescription = closeDescription }) { Text(stringResource(R.string.editor_close_tab)) }
                            }
                        }
                    }
                }
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                 Text(stringResource(R.string.editor_opening), Modifier.padding(16.dp))
            } else if (entry == null) {
                 EmptyScreen(stringResource(R.string.editor_no_file),
                     stringResource(R.string.editor_no_file_description)) {
                     Button(onClick = onExplore) { Text(stringResource(R.string.editor_go_explore)) }
                }
            } else {
                 if (state.formatting || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                  if (showSearch) {
                     TextField(state.search, onValueChange = onSearch,
                         modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).focusRequester(searchFocus).onPreviewKeyEvent {
                             if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { onFindNext(it.isShiftPressed); true }
                             else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { showSearch = false; true } else false
                          }, singleLine = true, label = { Text(stringResource(R.string.editor_find_in_file)) })
                     Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                         FilterChip(selected = state.searchOptions.caseSensitive,
                              onClick = { onSearchOptions(state.searchOptions.copy(caseSensitive = !state.searchOptions.caseSensitive)) }, label = { Text(stringResource(R.string.editor_case_sensitive)) })
                         FilterChip(selected = state.searchOptions.wholeWord,
                              onClick = { onSearchOptions(state.searchOptions.copy(wholeWord = !state.searchOptions.wholeWord)) }, label = { Text(stringResource(R.string.editor_whole_word)) })
                         FilterChip(selected = state.searchOptions.regex,
                              onClick = { onSearchOptions(state.searchOptions.copy(regex = !state.searchOptions.regex)) }, label = { Text(stringResource(R.string.editor_regex)) })
                          TextButton(onClick = { dispatch(EditorAction.FIND_PREVIOUS) }, enabled = enabled(EditorAction.FIND_PREVIOUS)) { Text(stringResource(R.string.editor_previous)) }
                          TextButton(onClick = { dispatch(EditorAction.FIND_NEXT) }, enabled = enabled(EditorAction.FIND_NEXT)) { Text(stringResource(R.string.editor_next)) }
                          TextButton(onClick = { showSearch = false }) { Text(stringResource(R.string.editor_close_search)) }
                     }
                     if (state.searching || state.replacing) LinearProgressIndicator(Modifier.fillMaxWidth())
                      Text(state.searchResult.error ?: if (state.searchResult.truncated)
                          pluralStringResource(R.plurals.editor_matches_truncated, state.occurrences, state.occurrences)
                          else pluralStringResource(R.plurals.editor_matches_count, state.occurrences, state.occurrences),
                         Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
                      if (showReplace) {
                         TextField(state.replacement, onReplacement, singleLine = true,
                              label = { Text(stringResource(R.string.editor_replacement)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                         Row {
                              TextButton(onClick = { onReplace(false) }, enabled = enabled(EditorAction.FIND_NEXT)) { Text(stringResource(R.string.editor_replace_current)) }
                              TextButton(onClick = { onReplace(true) }, enabled = enabled(EditorAction.FIND_NEXT) && !state.searchResult.truncated) { Text(stringResource(R.string.editor_replace_all)) }
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
         AlertDialog(onDismissRequest = { showGoToLine = false }, title = { Text(stringResource(R.string.dialog_editor_go_to_line)) },
            text = {
                LaunchedEffect(Unit) { withFrameNanos { }; lineFocus.requestFocus() }
                TextField(requestedLine, onValueChange = { requestedLine = it.filter(Char::isDigit) },
                modifier = Modifier.focusRequester(lineFocus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && requestedLine.toIntOrNull()?.let { n -> n > 0 } == true) {
                        onLine(requestedLine.toInt()); showGoToLine = false; true
                    } else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { showGoToLine = false; true } else false
                 }, singleLine = true, label = { Text(stringResource(R.string.dialog_editor_line_number)) }) },
            confirmButton = { TextButton(enabled = requestedLine.toIntOrNull()?.let { it > 0 } == true,
                 onClick = { requestedLine.toIntOrNull()?.let(onLine); showGoToLine = false }) { Text(stringResource(R.string.common_confirm)) } },
             dismissButton = { TextButton(onClick = { showGoToLine = false }) { Text(stringResource(R.string.common_cancel)) } })
    }
    if (showFormatMenu) {
         AlertDialog(onDismissRequest = { showFormatMenu = false }, title = { Text(stringResource(R.string.editor_format)) },
            text = { Column {
                FileMode.entries.forEach { mode ->
                    TextButton(enabled = entry != null && !state.loading,
                        onClick = { state.inputSession?.finishComposingText(); onMode(mode); showFormatMenu = false }) {
                        RadioButton(selected = state.mode == mode, onClick = null)
                        Spacer(Modifier.width(8.dp))
                         Text(modeName(mode))
                    }
                }
            } }, confirmButton = {},
             dismissButton = { TextButton(onClick = { showFormatMenu = false }) { Text(stringResource(R.string.common_cancel)) } })
    }
    state.pendingClose?.let { id ->
        val title = state.tabs.firstOrNull { it.documentId == id }?.title.orEmpty()
        AlertDialog(onDismissRequest = onCancelClose,
             title = { Text(stringResource(R.string.editor_close_named, title)) },
             text = { Text(stringResource(R.string.dialog_editor_unsaved_changes)) },
             confirmButton = { TextButton(onClick = onSaveClose, enabled = !state.saving && state.pendingSave == null) { Text(stringResource(R.string.dialog_editor_save_close)) } },
            dismissButton = {
                Row {
                     TextButton(onClick = onDiscardClose, enabled = !state.saving) { Text(stringResource(R.string.dialog_editor_discard)) }
                     TextButton(onClick = onCancelClose) { Text(stringResource(R.string.common_cancel)) }
                }
            })
    }
}
