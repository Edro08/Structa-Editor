package com.edro08.structa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R

enum class Screen { HOME, BROWSER, EDITOR, SETTINGS }

@Composable
fun AppNavigation(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    NavigationBar {
        Screen.entries.forEach { screen ->
            val (icon, label) = when (screen) {
                Screen.HOME -> Icons.Default.Home to R.string.nav_home
                Screen.BROWSER -> Icons.Default.Folder to R.string.nav_browser
                Screen.EDITOR -> Icons.Default.Code to R.string.nav_editor
                Screen.SETTINGS -> Icons.Default.Settings to R.string.nav_settings
            }
            NavigationBarItem(selected = currentScreen == screen, onClick = { onScreenSelected(screen) },
                icon = { Icon(icon, contentDescription = null) }, label = { Text(stringResource(label)) })
        }
    }
}
