package com.edro08.structa.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edro08.structa.domain.settings.EditorFont
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onBack: () -> Unit, onProvider: (String) -> Unit,
    onTheme: (Boolean) -> Unit, onFont: (EditorFont) -> Unit, onFontSize: (Int) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Configuracion") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                Text("Tema", style = MaterialTheme.typography.titleLarge)
                listOf(true to "Oscuro", false to "Claro").forEach { (dark, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.darkTheme == dark, onClick = { onTheme(dark) })
                        TextButton(onClick = { onTheme(dark) }) { Text(label) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Editor", style = MaterialTheme.typography.titleLarge)
                Text("Tipo de letra", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorFont.entries.forEach { font ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.editorFont == font, onClick = { onFont(font) })
                        TextButton(onClick = { onFont(font) }) { Text(font.label) }
                    }
                }
                Text("Tamaño de letra: ${state.editorFontSize} sp", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = state.editorFontSize.toFloat(), onValueChange = { onFontSize(it.roundToInt()) },
                    valueRange = 10f..32f, steps = 21)
                Spacer(Modifier.height(24.dp))
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
            }
        }
    }
}
