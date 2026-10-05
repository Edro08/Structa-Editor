package com.edro08.structa.ui.screen.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.ui.component.*
import com.edro08.structa.ui.theme.ScreenStyle
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
    if (action != null) AlertDialog(onDismissRequest = { action = null },
        title = { Text(stringResource(when (action!!) {
            BrowserAction.NEW_FILE -> R.string.browser_new_file
            BrowserAction.NEW_FOLDER -> R.string.browser_new_folder
            BrowserAction.RENAME -> R.string.browser_rename
            BrowserAction.DELETE -> R.string.browser_delete
        })) }, text = {
            if (action == BrowserAction.DELETE) Text(stringResource(R.string.dialog_browser_delete_confirm, target?.name.orEmpty()))
            else TextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(R.string.browser_name)) })
        }, confirmButton = { TextButton(enabled = !state.busy && (action == BrowserAction.DELETE || name.isNotBlank()), onClick = {
            when (action) {
                BrowserAction.DELETE -> target?.let(onDelete)
                BrowserAction.RENAME -> target?.let { onRename(it, name) }
                BrowserAction.NEW_FILE, BrowserAction.NEW_FOLDER -> onCreate(name, action == BrowserAction.NEW_FOLDER)
                null -> Unit
            }
            action = null
        }) { Text(stringResource(R.string.common_confirm)) } }, dismissButton = {
            TextButton(onClick = { action = null }) { Text(stringResource(R.string.common_cancel)) }
        })

    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { StructaTopBar(title = { Text(stringResource(R.string.browser_title)) }, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            WorkspacePanel(Modifier.fillMaxWidth().padding(horizontal = ScreenStyle.pagePadding), compact = true) {
                Row(Modifier.horizontalScroll(rememberScrollState()).heightIn(min = 28.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onChooseFolder, enabled = !state.busy,
                        contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.browser_workspace))
                    }
                    state.breadcrumbs.forEach { folder ->
                        Icon(Icons.Filled.ChevronRight, contentDescription = null,
                            modifier = Modifier.size(StructaSizes.breadcrumbIcon),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(folder, Modifier.padding(horizontal = StructaSpacing.compact), maxLines = 1)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = ScreenStyle.pagePadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedIconButton(onClick = onRetry, enabled = !state.busy && state.stack.isNotEmpty(),
                    modifier = Modifier.size(45.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.browser_refresh))
                }
                if (state.stack.isNotEmpty()) {
                    WorkspaceActionChip(stringResource(R.string.browser_new_file), Icons.AutoMirrored.Filled.NoteAdd,
                        !state.busy) { name = ""; action = BrowserAction.NEW_FILE }
                    WorkspaceActionChip(stringResource(R.string.browser_new_folder), Icons.Filled.CreateNewFolder,
                        !state.busy) { name = ""; action = BrowserAction.NEW_FOLDER }
                }
            }
            TextField(value = state.query, onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenStyle.pagePadding, vertical = 2.dp),
                singleLine = true, placeholder = { Text(stringResource(R.string.browser_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(ScreenStyle.panelRadius),
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
            Spacer(Modifier.height(7.dp))
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
                else -> {
                    val rows = buildList<Pair<FileEntry, Int>> {
                        fun addEntries(entries: List<FileEntry>, depth: Int) {
                            entries.forEach { entry ->
                                add(entry to depth)
                                if (entry.id in state.expanded) addEntries(state.children[entry.id].orEmpty(), depth + 1)
                            }
                        }
                        addEntries(state.entries, 0)
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
                        start = ScreenStyle.pagePadding, end = ScreenStyle.pagePadding, bottom = ScreenStyle.sectionGap),
                        verticalArrangement = Arrangement.spacedBy(ScreenStyle.tileGap)) {
                        items(rows, key = { it.first.id.value }) { (entry, depth) ->
                            var menu by remember { mutableStateOf(false) }
                            FileRow(entry, onClick = { onEntry(entry) },
                                modifier = Modifier.padding(start = (depth * 14).dp),
                                detail = if (entry.isDirectory) state.children[entry.id]?.let {
                                pluralStringResource(R.plurals.browser_folder_elements, it.size, it.size)
                            } else null) {
                                Box {
                                    IconButton(onClick = { menu = true }, enabled = !state.busy,
                                        modifier = Modifier.size(38.dp)) {
                                        Icon(Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.browser_actions_named, entry.name),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.browser_open_named, entry.name)) },
                                            onClick = { menu = false; onEntry(entry) },
                                            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) })
                                        if (entry.isDirectory) DropdownMenuItem(text = { Text(stringResource(
                                            if (entry.id in state.expanded) R.string.browser_collapse_named
                                            else R.string.browser_expand_named, entry.name)) },
                                            onClick = { menu = false; onToggle(entry) },
                                            leadingIcon = { Icon(Icons.Filled.ExpandMore, contentDescription = null) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.browser_rename_named, entry.name)) },
                                            onClick = { menu = false; target = entry; name = entry.name; action = BrowserAction.RENAME },
                                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.browser_delete_named, entry.name)) },
                                            onClick = { menu = false; target = entry; action = BrowserAction.DELETE },
                                            leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) })
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
