package com.edro08.structa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

enum class Screen { HOME, BROWSER, EDITOR, SETTINGS }

@Composable
fun AppNavigation(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    NavigationBar {
        Screen.entries.forEach { screen ->
            val (icon, label) = when (screen) {
                Screen.HOME -> Icons.Default.Home to "Inicio"
                Screen.BROWSER -> Icons.Default.Folder to "Explorar"
                Screen.EDITOR -> Icons.Default.Code to "Editor"
                Screen.SETTINGS -> Icons.Default.Settings to "Configuracion"
            }
            NavigationBarItem(selected = currentScreen == screen, onClick = { onScreenSelected(screen) },
                icon = { Icon(icon, contentDescription = null) }, label = { Text(label) })
        }
    }
}
