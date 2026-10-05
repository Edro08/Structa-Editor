package com.edro08.structa.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R
import com.edro08.structa.domain.settings.EditorFont
import com.edro08.structa.ui.component.ScreenSection
import com.edro08.structa.ui.component.StructaTopBar
import com.edro08.structa.ui.component.SupportingText
import com.edro08.structa.ui.theme.StructaSpacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onBack: () -> Unit, onProvider: (String) -> Unit,
    onTheme: (Boolean) -> Unit, onFont: (EditorFont) -> Unit, onFontSize: (Int) -> Unit) {
    Scaffold(topBar = { StructaTopBar(title = { Text(stringResource(R.string.settings_title)) }, onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(StructaSpacing.content),
            verticalArrangement = Arrangement.spacedBy(StructaSpacing.section)) {
            item {
                ScreenSection(stringResource(R.string.settings_theme)) {
                    listOf(true to R.string.settings_dark, false to R.string.settings_light).forEach { (dark, label) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.darkTheme == dark, onClick = { onTheme(dark) })
                            TextButton(onClick = { onTheme(dark) }) { Text(stringResource(label)) }
                        }
                    }
                }
            }
            item {
                ScreenSection(stringResource(R.string.settings_editor)) {
                    SupportingText(stringResource(R.string.settings_font))
                    EditorFont.entries.forEach { font ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.editorFont == font, onClick = { onFont(font) })
                            TextButton(onClick = { onFont(font) }) { Text(stringResource(when (font) {
                                EditorFont.MONOSPACE -> R.string.settings_font_monospace
                                EditorFont.SANS_MONOSPACE -> R.string.settings_font_sans_monospace
                            })) }
                        }
                    }
                    SupportingText(stringResource(R.string.settings_font_size, state.editorFontSize))
                    Slider(value = state.editorFontSize.toFloat(), onValueChange = { onFontSize(it.roundToInt()) },
                        valueRange = 10f..32f, steps = 21)
                }
            }
            item {
                ScreenSection(stringResource(R.string.settings_file_access)) {
                    SupportingText(stringResource(R.string.settings_file_access_description))
                    listOf("SAF", "Shizuku").forEach { provider ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.provider == provider, onClick = { onProvider(provider) })
                            Text(stringResource(if (provider == "SAF") R.string.settings_provider_saf
                                else R.string.settings_provider_shizuku), Modifier.padding(start = StructaSpacing.compact))
                        }
                    }
                    if (state.provider == "Shizuku") {
                        Card(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.settings_shizuku_unavailable), Modifier.padding(StructaSpacing.content))
                        }
                    }
                }
            }
        }
    }
}
