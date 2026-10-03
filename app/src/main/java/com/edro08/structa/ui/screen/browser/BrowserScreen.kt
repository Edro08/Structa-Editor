package com.edro08.structa.ui.screen.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.ui.component.FileRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(state: BrowserUiState, onBack: () -> Unit, onChooseFolder: () -> Unit,
    onQuery: (String) -> Unit, onEntry: (FileEntry) -> Unit, onRetry: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.title) }, navigationIcon = { TextButton(onClick = onBack) { Text("Atras") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextField(value = state.query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, placeholder = { Text("Buscar por nombre") })
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
                    items(state.entries, key = { it.id.value }) { entry -> FileRow(entry) { onEntry(entry) } }
                }
            }
        }
    }
}
