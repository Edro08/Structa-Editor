package com.edro08.structa.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onBack: () -> Unit, onProvider: (String) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Configuracion") }, navigationIcon = { TextButton(onClick = onBack) { Text("Atras") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                Text("Acceso a archivos", style = MaterialTheme.typography.titleLarge)
                Text("SAF funciona sin configuracion adicional. Shizuku se incorporara como proveedor avanzado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("SAF", "Shizuku").forEach { provider ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.provider == provider, onClick = { onProvider(provider) })
                        Text(provider, Modifier.padding(start = 8.dp))
                    }
                }
                if (state.provider == "Shizuku") {
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Shizuku aun no esta conectado en esta version.", Modifier.padding(16.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Editor", style = MaterialTheme.typography.titleLarge)
                Text("Tema oscuro activo. El tamano de fuente y mas preferencias se añadiran con el editor avanzado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
