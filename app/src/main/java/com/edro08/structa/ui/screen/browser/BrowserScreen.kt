package com.edro08.structa.ui.screen.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.ui.component.FileRow
import com.edro08.structa.ui.component.ActionIconButton
import com.edro08.structa.ui.component.EmptyScreen
import com.edro08.structa.ui.component.StructaTopBar
import com.edro08.structa.ui.theme.StructaSpacing
import com.edro08.structa.ui.theme.StructaSizes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(state: BrowserUiState, onBack: () -> Unit, onChooseFolder: () -> Unit,
    onQuery: (String) -> Unit, onEntry: (FileEntry) -> Unit, onRetry: () -> Unit,
    onToggle: (FileEntry) -> Unit = {}, onCreate: (String, Boolean) -> Unit = { _, _ -> },
    onRename: (FileEntry, String) -> Unit = { _, _ -> }, onDelete: (FileEntry) -> Unit = {}) {
    var action by remember { mutableStateOf<BrowserAction?>(null) }
    var target by remember { mutableStateOf<FileEntry?>(null) }
    var name by remember { mutableStateOf("") }
    var activeFile by remember(state.stack.lastOrNull()) { mutableStateOf<DocumentId?>(null) }
    if (action != null) AlertDialog(onDismissRequest = { action = null },
        title = { Text(stringResource(when (action!!) {
            BrowserAction.NEW_FILE -> R.string.browser_new_file
            BrowserAction.NEW_FOLDER -> R.string.browser_new_folder
            BrowserAction.RENAME -> R.string.browser_rename
            BrowserAction.DELETE -> R.string.browser_delete
        })) }, text = {
            if (action == BrowserAction.DELETE) Text(stringResource(R.string.dialog_browser_delete_confirm, target?.name.orEmpty()))
            else TextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.browser_name)) })
        }, confirmButton = { TextButton(enabled = !state.busy && (action == BrowserAction.DELETE || name.isNotBlank()), onClick = {
            when (action) {
                BrowserAction.DELETE -> target?.let(onDelete)
                BrowserAction.RENAME -> target?.let { onRename(it, name) }
                BrowserAction.NEW_FILE, BrowserAction.NEW_FOLDER -> onCreate(name, action == BrowserAction.NEW_FOLDER)
                null -> Unit
            }
            action = null
        }) { Text(stringResource(R.string.common_confirm)) } }, dismissButton = { TextButton(onClick = { action = null }) { Text(stringResource(R.string.common_cancel)) } })
    Scaffold(topBar = { StructaTopBar(title = { Text(state.title.ifEmpty { stringResource(R.string.browser_title) }) }, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = StructaSpacing.content),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onChooseFolder, enabled = !state.busy) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.browser_workspace))
                }
                state.breadcrumbs.forEach { folder ->
                    Icon(Icons.Filled.ChevronRight, contentDescription = null,
                        modifier = Modifier.size(StructaSizes.breadcrumbIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(folder, Modifier.padding(horizontal = StructaSpacing.compact), maxLines = 1)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = StructaSpacing.content),
                verticalAlignment = Alignment.CenterVertically) {
                ActionIconButton(onClick = onRetry, enabled = !state.busy && state.stack.isNotEmpty()) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.browser_refresh))
                }
                if (state.stack.isNotEmpty()) {
                    Spacer(Modifier.width(StructaSpacing.compact))
                    FilledTonalButton(enabled = !state.busy, onClick = { name = ""; action = BrowserAction.NEW_FILE },
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.browser_new_file))
                    }
                    Spacer(Modifier.width(StructaSpacing.compact))
                    OutlinedButton(enabled = !state.busy, onClick = { name = ""; action = BrowserAction.NEW_FOLDER },
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.browser_new_folder))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = StructaSpacing.content,
                vertical = StructaSpacing.compact)) {
                TextField(value = state.query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                     singleLine = true, placeholder = { Text(stringResource(R.string.browser_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) })
            }
            when {
                 state.stack.isEmpty() -> EmptyScreen(stringResource(R.string.browser_no_folder)) {
                     Button(onClick = onChooseFolder) { Text(stringResource(R.string.common_choose_folder)) }
                }
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error != null -> Column(Modifier.padding(StructaSpacing.section)) {
                     Text(when (state.errorRes) {
                         R.string.error_open_documents -> stringResource(R.string.error_open_documents)
                         R.string.error_folder_read_failed -> if (state.error.isEmpty())
                             stringResource(R.string.error_folder_read_failed_generic)
                             else stringResource(R.string.error_folder_read_failed, state.error)
                         else -> if (state.error.isEmpty()) stringResource(R.string.error_operation_failed)
                             else stringResource(R.string.error_operation_failed_detail, state.error)
                     })
                     TextButton(onClick = onRetry) { Text(stringResource(R.string.browser_retry)) }
                     TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.common_choose_folder)) }
                }
                 state.entries.isEmpty() -> EmptyScreen(stringResource(R.string.browser_empty))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    val rows = buildList<Pair<FileEntry, Int>> {
                        fun addEntries(entries: List<FileEntry>, depth: Int) {
                            entries.forEach { entry ->
                                add(entry to depth)
                                if (entry.id in state.expanded) addEntries(state.children[entry.id].orEmpty(), depth + 1)
                            }
                        }
                        addEntries(state.entries, 0)
                    }
                    items(rows, key = { it.first.id.value }) { (entry, depth) ->
                        Row(Modifier.padding(start = (depth * 16).dp)) {
                             val actionsVisible = entry.isDirectory || activeFile == entry.id
                             val actionsState = stringResource(if (actionsVisible) R.string.browser_actions_visible
                                 else R.string.browser_actions_hidden)
                            FileRow(entry, onClick = {
                                if (entry.isDirectory) onEntry(entry)
                                else activeFile = if (actionsVisible) null else entry.id
                            }, modifier = if (entry.isDirectory) Modifier else Modifier.semantics {
                                 stateDescription = actionsState
                            }) {
                                if (actionsVisible) {
                                    ActionIconButton(enabled = !state.busy, onClick = { onEntry(entry) }) {
                                        Icon(if (entry.isDirectory) Icons.Filled.FolderOpen else Icons.AutoMirrored.Filled.OpenInNew,
                                             contentDescription = stringResource(R.string.browser_open_named, entry.name))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    if (entry.isDirectory) {
                                        ActionIconButton(enabled = !state.busy, onClick = { onToggle(entry) }) {
                                            Icon(if (entry.id in state.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                 contentDescription = stringResource(if (entry.id in state.expanded) R.string.browser_collapse_named
                                                     else R.string.browser_expand_named, entry.name))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    ActionIconButton(enabled = !state.busy,
                                         onClick = { target = entry; name = entry.name; action = BrowserAction.RENAME }) {
                                         Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.browser_rename_named, entry.name))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    ActionIconButton(enabled = !state.busy,
                                         onClick = { target = entry; action = BrowserAction.DELETE }) {
                                         Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.browser_delete_named, entry.name))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class BrowserAction { NEW_FILE, NEW_FOLDER, RENAME, DELETE }
