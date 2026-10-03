package com.edro08.structa.ui.screen.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edro08.structa.domain.filesystem.FileMode
import com.edro08.structa.ui.editor.component.StructaEditor
import com.edro08.structa.ui.editor.input.EditorAction
import com.edro08.structa.ui.component.formatBytes

private const val MAX_INTERACTIVE_EDITOR_BYTES = 1L * 1024L * 1024L

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
    onMessage: (String) -> Unit) {
    var showModeMenu by remember { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showGoToLine by rememberSaveable { mutableStateOf(false) }
    var requestedLine by rememberSaveable { mutableStateOf("") }
    val entry = state.entry
    val largeDocument = entry != null &&
        (entry.sizeBytes > MAX_INTERACTIVE_EDITOR_BYTES || state.value.text.length > MAX_INTERACTIVE_EDITOR_BYTES)
    Scaffold(topBar = {
        TopAppBar(title = { Text(entry?.name ?: "Editor", maxLines = 1) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Atras") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
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
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { showModeMenu = true }) { Text(state.mode.displayName) }
                        DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                            FileMode.entries.forEach { mode ->
                                DropdownMenuItem(text = { Text(mode.displayName) }, onClick = { onMode(mode); showModeMenu = false })
                            }
                        }
                    }
                     TextButton(onClick = { showSearch = !showSearch }, enabled = !largeDocument) { Text("Buscar") }
                     TextButton(onClick = onFormat, enabled = !largeDocument && !state.formatting) { Text("Formatear") }
                     TextButton(onClick = onUndo, enabled = !largeDocument && state.canUndo) { Text("Deshacer") }
                     TextButton(onClick = onRedo, enabled = !largeDocument && state.canRedo) { Text("Rehacer") }
                     TextButton(onClick = { showGoToLine = true }, enabled = !largeDocument) { Text("Linea") }
                 }
                 if (state.formatting || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                 if (largeDocument) {
                     Text(
                         "Documento grande: visor de lectura para mantener la aplicacion fluida. La edicion y las operaciones pesadas estan desactivadas.",
                         Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                 } else if (showSearch) {
                    TextField(state.search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth().padding(8.dp),
                        singleLine = true, placeholder = { Text("Buscar dentro del archivo") })
                    if (state.search.isNotBlank()) Text("${state.occurrences} coincidencias", Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${state.mode.displayName} - ${formatBytes(entry.sizeBytes)}", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                  state.inputSession?.let { input ->
                      StructaEditor(input.engine, Modifier.weight(1f).fillMaxWidth(),
                          contentVersion = state.contentVersion, cursorVisible = !largeDocument,
                          editable = !largeDocument, inputSession = input,
                          onEditorAction = { action ->
                              when (action) {
                                  EditorAction.SAVE -> onSaveAs()
                                  EditorAction.FIND -> showSearch = true
                                  EditorAction.GO_TO_LINE -> showGoToLine = true
                                  EditorAction.REPLACE -> onMessage("Reemplazar está previsto para la fase 10.")
                                  EditorAction.QUICK_OPEN -> onMessage("Quick Open está previsto para la fase 10.")
                                  EditorAction.COMMAND_PALETTE -> onMessage("Command Palette está prevista para la fase 10.")
                              }
                          })
                  }
                 Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                     Button(onClick = onSaveAs, enabled = !state.saving && state.pendingSave == null && !largeDocument) { Text("Guardar como") }
                 }
            }
        }
    }
    if (showGoToLine) {
        AlertDialog(onDismissRequest = { showGoToLine = false }, title = { Text("Ir a linea") },
            text = { TextField(requestedLine, onValueChange = { requestedLine = it.filter(Char::isDigit) },
                singleLine = true, placeholder = { Text("Numero de linea") }) },
            confirmButton = { TextButton(onClick = { requestedLine.toIntOrNull()?.let(onLine); showGoToLine = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { showGoToLine = false }) { Text("Cancelar") } })
    }
}
