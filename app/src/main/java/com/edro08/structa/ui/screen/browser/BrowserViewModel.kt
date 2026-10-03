package com.edro08.structa.ui.screen.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edro08.structa.application.browser.ListDirectory
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowserUiState(
    val stack: List<DocumentId> = emptyList(),
    val title: String = "Explorador",
    val query: String = "",
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class BrowserViewModel(
    private val listDirectory: ListDirectory,
    private val settings: SettingsRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserUiState())
    val state = mutableState.asStateFlow()
    private var entries = emptyList<FileEntry>()
    private var titles = listOf("Explorador")
    private var request = 0L
    private var job: Job? = null

    init { settings.lastFolder()?.let { selectFolder(it, persist = false) } }

    fun selectFolder(id: DocumentId, persist: Boolean = true) {
        if (persist) settings.setLastFolder(id)
        titles = listOf("Explorador")
        mutableState.value = BrowserUiState(stack = listOf(id))
        load()
    }

    fun enter(entry: FileEntry) {
        if (!entry.isDirectory) return
        titles = titles + entry.name
        mutableState.value = state.value.copy(stack = state.value.stack + entry.id, title = entry.name)
        load()
    }

    fun back(): Boolean {
        if (state.value.stack.size <= 1) return false
        titles = titles.dropLast(1)
        mutableState.value = state.value.copy(stack = state.value.stack.dropLast(1), title = titles.last())
        load()
        return true
    }

    fun setQuery(query: String) {
        mutableState.value = state.value.copy(query = query, entries = entries.filter { it.name.contains(query, true) })
    }

    fun refresh() = load()
    fun dismissError() { mutableState.value = state.value.copy(error = null) }

    private fun load() {
        val id = state.value.stack.lastOrNull() ?: return
        val token = ++request
        job?.cancel()
        entries = emptyList()
        mutableState.value = state.value.copy(entries = emptyList(), loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val result = listDirectory(id)
                if (token != request) return@launch
                entries = result
                mutableState.value = state.value.copy(loading = false,
                    entries = result.filter { it.name.contains(state.value.query, true) })
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (token == request) mutableState.value = state.value.copy(loading = false,
                    error = "No se pudo leer la carpeta: ${exception.message}")
            }
        }
    }
}
