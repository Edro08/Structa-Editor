package com.edro08.structa.ui.screen.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.edro08.structa.R
import com.edro08.structa.domain.settings.EditorFont
import com.edro08.structa.ui.component.StructaTopBar
import com.edro08.structa.ui.component.WorkspacePanel
import com.edro08.structa.ui.component.WorkspaceSectionTitle
import com.edro08.structa.ui.component.WorkspaceSelection
import com.edro08.structa.ui.theme.ScreenStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onBack: () -> Unit, onProvider: (String) -> Unit,
    onTheme: (Boolean) -> Unit, onFont: (EditorFont) -> Unit, onFontSize: (Int) -> Unit,
    directAuthorized: Boolean = false, onManagePermission: () -> Unit = {}) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { StructaTopBar(title = { Text(stringResource(R.string.settings_title)) }, onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(
            start = ScreenStyle.pagePadding, end = ScreenStyle.pagePadding,
            top = 8.dp, bottom = ScreenStyle.sectionGap),
            verticalArrangement = Arrangement.spacedBy(ScreenStyle.sectionGap)) {
            item {
                WorkspacePanel(Modifier.fillMaxWidth()) {
                    WorkspaceSectionTitle(Icons.Filled.Palette, stringResource(R.string.settings_theme),
                        stringResource(R.string.settings_theme_description))
                    Spacer(Modifier.height(12.dp))
                    WorkspaceSelection(stringResource(R.string.settings_dark), state.darkTheme) { onTheme(true) }
                    WorkspaceSelection(stringResource(R.string.settings_light), !state.darkTheme) { onTheme(false) }
                }
            }
            item {
                WorkspacePanel(Modifier.fillMaxWidth()) {
                    WorkspaceSectionTitle(Icons.Filled.Code, stringResource(R.string.settings_editor),
                        stringResource(R.string.settings_editor_description))
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.settings_font), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    EditorFont.entries.forEach { font ->
                        WorkspaceSelection(stringResource(when (font) {
                            EditorFont.MONOSPACE -> R.string.settings_font_monospace
                            EditorFont.SANS_MONOSPACE -> R.string.settings_font_sans_monospace
                        }), state.editorFont == font) { onFont(font) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_font_size, state.editorFontSize),
                        style = MaterialTheme.typography.bodyMedium)
                    DottedFontSlider(state.editorFontSize, onFontSize)
                }
            }
            item {
                WorkspacePanel(Modifier.fillMaxWidth()) {
                    WorkspaceSectionTitle(Icons.Filled.FolderOpen, stringResource(R.string.settings_file_access),
                        stringResource(R.string.settings_file_access_description))
                    Spacer(Modifier.height(12.dp))
                    WorkspaceSelection(stringResource(R.string.settings_provider_saf), state.provider == "SAF") {
                        onProvider("SAF")
                    }
                    Text(stringResource(R.string.settings_saf_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    WorkspaceSelection(stringResource(R.string.settings_provider_direct), state.provider == "DIRECT") {
                        onProvider("DIRECT")
                    }
                    Text(stringResource(R.string.settings_direct_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if (directAuthorized) R.string.settings_direct_active else R.string.settings_direct_inactive),
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onManagePermission) {
                        Text(stringResource(if (directAuthorized) R.string.settings_direct_manage else R.string.settings_direct_grant))
                    }
                    Spacer(Modifier.height(8.dp))
                    WorkspaceSelection(stringResource(R.string.settings_provider_privileged), false, enabled = false) {}
                    Text(stringResource(R.string.settings_privileged_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DottedFontSlider(size: Int, onSize: (Int) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    Slider(value = size.toFloat(), onValueChange = { onSize(it.roundToInt()) },
        valueRange = 10f..32f, steps = 21, modifier = Modifier.fillMaxWidth(),
        thumb = {
            Canvas(Modifier.size(width = 22.dp, height = 36.dp)) {
                val x = this.size.width / 2f
                drawLine(primary, Offset(x, 0f), Offset(x, this.size.height), strokeWidth = 2.dp.toPx())
                drawCircle(primary, radius = 11.dp.toPx(), center = Offset(x, this.size.height / 2f))
            }
        }, track = {
            Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                val y = this.size.height / 2f
                drawRoundRect(inactive.copy(alpha = .22f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(this.size.width, 14.dp.toPx()),
                    topLeft = Offset(0f, y - 7.dp.toPx()))
                val step = this.size.width / 22f
                for (index in 0..22) {
                    drawCircle(if (index <= size - 10) primary else inactive,
                        radius = 1.6.dp.toPx(), center = Offset(index * step, y))
                }
            }
        })
}
