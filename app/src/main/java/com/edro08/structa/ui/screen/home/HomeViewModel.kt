package com.edro08.structa.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileSystem
import com.edro08.structa.domain.settings.SettingsRepository
import com.edro08.structa.domain.workspace.WorkspaceHistoryRepository
import com.edro08.structa.domain.workspace.WorkspaceShortcut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class HomeUiState(val lastFolder: DocumentId? = null, val favorites: List<WorkspaceShortcut> = emptyList(),
    val recent: List<WorkspaceShortcut> = emptyList())

class HomeViewModel(private val settings: SettingsRepository, private val history: WorkspaceHistoryRepository,
    private val fileSystem: FileSystem? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state = mutableState.asStateFlow()
    init { refresh() }

    fun refresh() {
        val sorted = history.all().sortedByDescending { it.lastOpenedAt }
        mutableState.value = HomeUiState(settings.lastFolder(), sorted.filter { it.favorite },
            sorted.filterNot { it.favorite })
    }

    fun opened(id: DocumentId) {
        history.opened(id, System.currentTimeMillis())
        refresh()
        fileSystem?.let { fs ->
            viewModelScope.launch {
                try {
                    val actualName = fs.stat(id).name
                    history.rename(id, actualName)
                    refresh()
                } catch (exception: CancellationException) { throw exception
                } catch (_: Exception) { /* Keep the cached title if access is unavailable. */ }
            }
        }
    }

    fun toggleFavorite(id: DocumentId) {
        val entry = history.all().firstOrNull { it.id == id } ?: return
        history.setFavorite(id, !entry.favorite)
        refresh()
    }

    fun remove(id: DocumentId) {
        history.remove(id)
        refresh()
    }
}
