package com.edro08.structa.ui.screen.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.ui.component.FileRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(state: BrowserUiState, onBack: () -> Unit, onChooseFolder: () -> Unit,
    onQuery: (String) -> Unit, onEntry: (FileEntry) -> Unit, onRetry: () -> Unit,
    onToggle: (FileEntry) -> Unit = {}, onCreate: (String, Boolean) -> Unit = { _, _ -> },
    onRename: (FileEntry, String) -> Unit = { _, _ -> }, onDelete: (FileEntry) -> Unit = {}) {
    var action by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<FileEntry?>(null) }
    var name by remember { mutableStateOf("") }
    var activeFile by remember(state.stack.lastOrNull()) { mutableStateOf<DocumentId?>(null) }
    if (action != null) AlertDialog(onDismissRequest = { action = null },
        title = { Text(action!!) }, text = {
            if (action == "Eliminar") Text("¿Eliminar ${target?.name}? Esta operación no se puede deshacer.")
            else TextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nombre") })
        }, confirmButton = { TextButton(enabled = !state.busy && (action == "Eliminar" || name.isNotBlank()), onClick = {
            when (action) {
                "Eliminar" -> target?.let(onDelete)
                "Renombrar" -> target?.let { onRename(it, name) }
                else -> onCreate(name, action == "Nueva carpeta")
            }
            action = null
        }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("Cancelar") } })
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.title) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onChooseFolder, enabled = !state.busy) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Workspace")
                }
                state.breadcrumbs.forEach { folder ->
                    Icon(Icons.Filled.ChevronRight, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(folder, Modifier.padding(horizontal = 8.dp), maxLines = 1)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = onRetry, enabled = !state.busy && state.stack.isNotEmpty(),
                    modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                }
                if (state.stack.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(enabled = !state.busy, onClick = { name = ""; action = "Nuevo archivo" },
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Nuevo archivo")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(enabled = !state.busy, onClick = { name = ""; action = "Nueva carpeta" },
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Nueva carpeta")
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextField(value = state.query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, placeholder = { Text("Buscar por nombre") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) })
            }
            when {
                state.stack.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay una carpeta autorizada disponible.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onChooseFolder) { Text("Elegir carpeta") }
                }
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error != null -> Column(Modifier.padding(24.dp)) {
                    Text(state.error)
                    TextButton(onClick = onRetry) { Text("Reintentar") }
                    TextButton(onClick = onChooseFolder) { Text("Elegir carpeta") }
                }
                state.entries.isEmpty() -> Text("No hay archivos o carpetas que mostrar.", Modifier.padding(24.dp))
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
                            FileRow(entry, onClick = {
                                if (entry.isDirectory) onEntry(entry)
                                else activeFile = if (actionsVisible) null else entry.id
                            }, modifier = if (entry.isDirectory) Modifier else Modifier.semantics {
                                stateDescription = if (actionsVisible) "Acciones visibles" else "Acciones ocultas"
                            }) {
                                if (actionsVisible) {
                                    OutlinedIconButton(enabled = !state.busy, onClick = { onEntry(entry) },
                                        modifier = Modifier.size(40.dp)) {
                                        Icon(if (entry.isDirectory) Icons.Filled.FolderOpen else Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Abrir ${entry.name}")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    if (entry.isDirectory) {
                                        OutlinedIconButton(enabled = !state.busy, onClick = { onToggle(entry) },
                                            modifier = Modifier.size(40.dp)) {
                                            Icon(if (entry.id in state.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = "${if (entry.id in state.expanded) "Contraer" else "Expandir"} ${entry.name}")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    OutlinedIconButton(enabled = !state.busy,
                                        onClick = { target = entry; name = entry.name; action = "Renombrar" },
                                        modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Renombrar ${entry.name}")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedIconButton(enabled = !state.busy,
                                        onClick = { target = entry; action = "Eliminar" }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar ${entry.name}")
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
