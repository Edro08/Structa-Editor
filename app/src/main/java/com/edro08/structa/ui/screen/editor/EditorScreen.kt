package com.edro08.structa.ui.screen.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
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
import com.edro08.structa.ui.component.WorkspaceSelection
import com.edro08.structa.ui.theme.ScreenStyle

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
                  Text(if (state.tabs.size == 1 && state.dirty) stringResource(R.string.editor_dirty_title, title) else title,
                      maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                         shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp),
                         colors = ButtonDefaults.outlinedButtonColors(
                             containerColor = if (showFileMenu) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                         Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                         Spacer(Modifier.width(7.dp))
                         Text(stringResource(R.string.editor_file_menu))
                         Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                     }
                     DropdownMenu(expanded = showFileMenu, onDismissRequest = { showFileMenu = false },
                         modifier = Modifier.widthIn(min = 220.dp).border(1.dp,
                             MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                         shape = RoundedCornerShape(18.dp),
                         containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                         EditorMenuOption(stringResource(R.string.editor_open), Icons.Filled.FolderOpen) {
                             showFileMenu = false; dispatch(EditorAction.QUICK_OPEN)
                         }
                         EditorMenuOption(stringResource(R.string.editor_save), Icons.Filled.Save,
                             enabled = enabled(EditorAction.SAVE)) { showFileMenu = false; dispatch(EditorAction.SAVE) }
                         EditorMenuOption(stringResource(R.string.editor_save_as), Icons.Filled.SaveAs,
                             enabled = enabled(EditorAction.SAVE)) {
                             showFileMenu = false; state.inputSession?.finishComposingText(); onSaveAs()
                         }
                         HorizontalDivider()
                         EditorMenuOption(stringResource(R.string.editor_close), Icons.Filled.Close,
                             enabled = enabled(EditorAction.CLOSE)) { showFileMenu = false; dispatch(EditorAction.CLOSE) }
                     }
                 }
                 Box {
                     IconButton(onClick = { dispatch(EditorAction.COMMAND_PALETTE) }) {
                         Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.editor_edit_menu_description))
                     }
                     DropdownMenu(expanded = showEditMenu, onDismissRequest = { showEditMenu = false },
                         modifier = Modifier.widthIn(min = 260.dp).border(1.dp,
                             MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                         shape = RoundedCornerShape(18.dp),
                         containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                         listOf(Triple(EditorAction.FIND, Icons.Filled.Search, R.string.editor_shortcut_find),
                             Triple(EditorAction.REPLACE, Icons.Filled.FindReplace, R.string.editor_shortcut_replace),
                             Triple(EditorAction.GO_TO_LINE, Icons.Filled.FormatListNumbered, R.string.editor_shortcut_line))
                             .forEach { (action, icon, shortcut) ->
                                 EditorMenuOption(actionName(action), icon, stringResource(shortcut), enabled(action)) {
                                     showEditMenu = false; dispatch(action)
                                 }
                             }
                         HorizontalDivider()
                         listOf(Triple(EditorAction.UNDO, Icons.AutoMirrored.Filled.Undo, R.string.editor_shortcut_undo),
                             Triple(EditorAction.REDO, Icons.AutoMirrored.Filled.Redo, R.string.editor_shortcut_redo))
                             .forEach { (action, icon, shortcut) ->
                                 EditorMenuOption(actionName(action), icon, stringResource(shortcut), enabled(action)) {
                                     showEditMenu = false; dispatch(action)
                                 }
                             }
                         HorizontalDivider()
                         EditorMenuOption(actionName(EditorAction.FORMAT), Icons.Filled.AutoFixHigh,
                             enabled = enabled(EditorAction.FORMAT)) { showEditMenu = false; dispatch(EditorAction.FORMAT) }
                         EditorMenuOption(stringResource(R.string.editor_format), Icons.Filled.Code,
                             enabled = entry != null && !state.loading, submenu = true) {
                             showEditMenu = false; showFormatMenu = true
                         }
                     }
                 }
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
                      val searchShape = RoundedCornerShape(12.dp)
                      TextField(state.search, onValueChange = onSearch,
                          modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenStyle.pagePadding, vertical = 3.dp)
                              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, searchShape)
                              .focusRequester(searchFocus).onPreviewKeyEvent {
                                  if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { onFindNext(it.isShiftPressed); true }
                                  else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { showSearch = false; true } else false
                              }, singleLine = true, shape = searchShape,
                          placeholder = { Text(stringResource(R.string.editor_find_in_file), maxLines = 1) },
                          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                          trailingIcon = {
                              Row(verticalAlignment = Alignment.CenterVertically) {
                                  IconButton(onClick = { dispatch(EditorAction.FIND_PREVIOUS) },
                                      enabled = enabled(EditorAction.FIND_PREVIOUS), modifier = Modifier.size(40.dp)) {
                                      Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.editor_previous))
                                  }
                                  IconButton(onClick = { dispatch(EditorAction.FIND_NEXT) },
                                      enabled = enabled(EditorAction.FIND_NEXT), modifier = Modifier.size(40.dp)) {
                                      Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.editor_next))
                                  }
                                  IconButton(onClick = { showSearch = false }, modifier = Modifier.size(40.dp)) {
                                      Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.editor_close_search))
                                  }
                              }
                          }, colors = editorFieldColors())
                      if (showReplace) {
                          TextField(state.replacement, onReplacement, singleLine = true,
                              placeholder = { Text(stringResource(R.string.editor_replacement)) },
                              leadingIcon = { Icon(Icons.Filled.FindReplace, contentDescription = null) },
                              modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenStyle.pagePadding, vertical = 3.dp),
                              shape = searchShape, colors = editorFieldColors())
                      }
                      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                          .padding(horizontal = ScreenStyle.pagePadding), verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                          EditorSearchChip(stringResource(R.string.editor_case_sensitive), state.searchOptions.caseSensitive) {
                              onSearchOptions(state.searchOptions.copy(caseSensitive = !state.searchOptions.caseSensitive))
                          }
                          EditorSearchChip(stringResource(R.string.editor_whole_word), state.searchOptions.wholeWord) {
                              onSearchOptions(state.searchOptions.copy(wholeWord = !state.searchOptions.wholeWord))
                          }
                          EditorSearchChip(stringResource(R.string.editor_regex), state.searchOptions.regex) {
                              onSearchOptions(state.searchOptions.copy(regex = !state.searchOptions.regex))
                          }
                          if (showReplace) {
                              Spacer(Modifier.width(6.dp))
                              OutlinedButton(onClick = { onReplace(false) }, enabled = enabled(EditorAction.FIND_NEXT),
                                  modifier = Modifier.height(34.dp), shape = RoundedCornerShape(20.dp),
                                  contentPadding = PaddingValues(horizontal = 10.dp)) {
                                  Text(stringResource(R.string.editor_replace_current), style = MaterialTheme.typography.labelSmall)
                              }
                              Button(onClick = { onReplace(true) },
                                  enabled = enabled(EditorAction.FIND_NEXT) && !state.searchResult.truncated,
                                  modifier = Modifier.height(34.dp), shape = RoundedCornerShape(20.dp),
                                  contentPadding = PaddingValues(horizontal = 10.dp)) {
                                  Text(stringResource(R.string.editor_replace_all), style = MaterialTheme.typography.labelSmall)
                              }
                          } else {
                              Spacer(Modifier.width(8.dp))
                              Text(state.searchResult.error ?: if (state.searchResult.truncated)
                                  pluralStringResource(R.plurals.editor_matches_truncated, state.occurrences, state.occurrences)
                                  else pluralStringResource(R.plurals.editor_matches_count, state.occurrences, state.occurrences),
                                  style = MaterialTheme.typography.labelSmall, maxLines = 1)
                          }
                      }
                      if (showReplace && (state.searchResult.error != null || state.searchResult.truncated)) {
                          Text(state.searchResult.error ?: pluralStringResource(R.plurals.editor_matches_truncated,
                              state.occurrences, state.occurrences), Modifier.padding(horizontal = ScreenStyle.pagePadding),
                              style = MaterialTheme.typography.labelSmall)
                      }
                      if (state.searching || state.replacing) LinearProgressIndicator(Modifier.fillMaxWidth())
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
          AlertDialog(onDismissRequest = { showGoToLine = false },
             shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
             title = { Text(stringResource(R.string.dialog_editor_go_to_line)) },
            text = {
                LaunchedEffect(Unit) { withFrameNanos { }; lineFocus.requestFocus() }
                TextField(requestedLine, onValueChange = { requestedLine = it.filter(Char::isDigit) },
                modifier = Modifier.focusRequester(lineFocus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && requestedLine.toIntOrNull()?.let { n -> n > 0 } == true) {
                        onLine(requestedLine.toInt()); showGoToLine = false; true
                    } else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { showGoToLine = false; true } else false
                  }, singleLine = true, label = { Text(stringResource(R.string.dialog_editor_line_number)) },
                     shape = RoundedCornerShape(12.dp)) },
             confirmButton = { Button(enabled = requestedLine.toIntOrNull()?.let { it > 0 } == true,
                  onClick = { requestedLine.toIntOrNull()?.let(onLine); showGoToLine = false }) { Text(stringResource(R.string.editor_accept)) } },
             dismissButton = { TextButton(onClick = { showGoToLine = false }) { Text(stringResource(R.string.common_cancel)) } })
    }
    if (showFormatMenu) {
          AlertDialog(onDismissRequest = { showFormatMenu = false },
             shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
             title = { Text(stringResource(R.string.editor_language_dialog)) },
             text = { Column {
                 FileMode.entries.forEach { mode ->
                     WorkspaceSelection(modeName(mode), state.mode == mode) {
                         state.inputSession?.finishComposingText(); onMode(mode); showFormatMenu = false
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

@Composable
private fun EditorMenuOption(label: String, icon: ImageVector, shortcut: String? = null,
    enabled: Boolean = true, submenu: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, maxLines = 1) }, onClick = onClick, enabled = enabled,
        leadingIcon = { Icon(icon, contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)) },
        trailingIcon = when {
            shortcut != null -> {{ Text(shortcut, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }}
            submenu -> {{ Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }}
            else -> null
        })
}

@Composable
private fun editorFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)

@Composable
private fun EditorSearchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.height(34.dp).clip(shape)
        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, shape)
        .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
        .padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}
