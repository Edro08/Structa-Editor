package com.edro08.structa.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.edro08.structa.domain.settings.EditorFont
import com.edro08.structa.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(val provider: String = "SAF", val darkTheme: Boolean = true,
    val editorFont: EditorFont = EditorFont.MONOSPACE, val editorFontSize: Int = 14)

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState(darkTheme = settings.darkTheme(),
        editorFont = settings.editorFont(), editorFontSize = settings.editorFontSize()))
    val state = mutableState.asStateFlow()
    // This selection is informational, not an active storage provider.
    fun selectProvider(provider: String) { mutableState.value = mutableState.value.copy(provider = provider) }
    fun selectTheme(dark: Boolean) {
        settings.setDarkTheme(dark)
        mutableState.value = mutableState.value.copy(darkTheme = dark)
    }
    fun selectFont(font: EditorFont) {
        settings.setEditorFont(font)
        mutableState.value = mutableState.value.copy(editorFont = font)
    }
    fun selectFontSize(size: Int) {
        if (size !in 10..32) return
        settings.setEditorFontSize(size)
        mutableState.value = mutableState.value.copy(editorFontSize = size)
    }
}
