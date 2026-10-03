package com.edro08.structa.ui.screen.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(val provider: String = "SAF")

class SettingsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state = mutableState.asStateFlow()
    // This selection is informational, not an active storage provider.
    fun selectProvider(provider: String) { mutableState.value = SettingsUiState(provider) }
}
