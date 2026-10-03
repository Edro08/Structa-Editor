package com.edro08.structa.ui.screen.home

import androidx.lifecycle.ViewModel
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(val lastFolder: DocumentId? = null)

class HomeViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState(settings.lastFolder()))
    val state = mutableState.asStateFlow()
    fun refresh() { mutableState.value = HomeUiState(settings.lastFolder()) }
}
