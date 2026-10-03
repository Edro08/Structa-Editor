package com.edro08.structa.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeUiState, onOpenLastFolder: () -> Unit, onChooseFolder: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Structa") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Archivos sin depender de su extension", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text("Abre y edita texto, JSON o YAML desde cualquier carpeta autorizada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            if (state.lastFolder != null) {
                Button(onClick = onOpenLastFolder, modifier = Modifier.fillMaxWidth()) { Text("Abrir ultima carpeta") }
                Spacer(Modifier.height(12.dp))
            }
            OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text("Elegir carpeta") }
        }
    }
}
