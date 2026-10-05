package com.edro08.structa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.edro08.structa.R
import com.edro08.structa.ui.theme.ScreenStyle

enum class Screen { HOME, BROWSER, EDITOR, SETTINGS }

@Composable
fun AppNavigation(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.surfaceContainerHigh)))
        .navigationBarsPadding().height(ScreenStyle.navigationHeight),
        verticalAlignment = Alignment.CenterVertically) {
        Screen.entries.forEach { screen ->
            val (icon, label) = when (screen) {
                Screen.HOME -> Icons.Default.Home to R.string.nav_home
                Screen.BROWSER -> Icons.Default.Folder to R.string.nav_browser
                Screen.EDITOR -> Icons.Default.Code to R.string.nav_editor
                Screen.SETTINGS -> Icons.Default.Settings to R.string.nav_settings
            }
            val selected = currentScreen == screen
            TextButton(onClick = { onScreenSelected(screen) }, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(64.dp).height(34.dp).background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}
