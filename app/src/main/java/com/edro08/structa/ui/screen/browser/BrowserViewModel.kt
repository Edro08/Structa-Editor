package com.edro08.structa.ui.screen.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edro08.structa.R
import com.edro08.structa.application.browser.ListDirectory
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.FileSystem
import com.edro08.structa.domain.filesystem.FileRef
import com.edro08.structa.domain.workspace.Workspace
import com.edro08.structa.domain.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowserUiState(
    val stack: List<DocumentId> = emptyList(),
    val title: String = "",
    val query: String = "",
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
    val workspace: Workspace? = null,
    val expanded: Set<DocumentId> = emptySet(),
    val children: Map<DocumentId, List<FileEntry>> = emptyMap(),
    val busy: Boolean = false,
    val breadcrumbs: List<String> = emptyList()
)

class BrowserViewModel(
    private val listDirectory: ListDirectory,
    private val settings: SettingsRepository,
    private val fileSystem: FileSystem? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserUiState())
    val state = mutableState.asStateFlow()
    private var entries = emptyList<FileEntry>()
    private var titles = listOf("")
    private var rootName: String? = null
    private var rootSelection = 0L
    private var request = 0L
    private var job: Job? = null
    private var treeGeneration = 0L
    private val expanding = mutableSetOf<DocumentId>()

    init { settings.lastFolderRef()?.let { selectFolder(it, persist = false) } }

    fun selectFolder(id: DocumentId, persist: Boolean = true) = selectFolder(FileRef.of(id), persist)

    fun selectFolder(root: FileRef, persist: Boolean = true) {
        val id = root.id
        if (persist) settings.setLastFolderRef(root)
        titles = listOf("")
        rootName = null
        val selected = ++rootSelection
        treeGeneration++
        expanding.clear()
        mutableState.value = BrowserUiState(stack = listOf(id), workspace = Workspace(id.value,
            root, state.value.workspace?.openDocuments.orEmpty()))
        load()
        fileSystem?.let { fs ->
            viewModelScope.launch {
                try {
                    val name = fs.stat(id).name
                    if (selected == rootSelection) {
                        rootName = name
                        mutableState.value = state.value.copy(breadcrumbs = listOf(name) + titles.drop(1))
                    }
                } catch (exception: CancellationException) { throw exception
                } catch (_: Exception) { /* The folder listing reports inaccessible roots. */ }
            }
        }
    }

    fun enter(entry: FileEntry) {
        if (!entry.isDirectory) return
        titles = titles + entry.name
        mutableState.value = state.value.copy(stack = state.value.stack + entry.id, title = entry.name,
            breadcrumbs = listOfNotNull(rootName) + titles.drop(1))
        load()
    }

    fun back(): Boolean {
        if (state.value.stack.size <= 1) return false
        titles = titles.dropLast(1)
        mutableState.value = state.value.copy(stack = state.value.stack.dropLast(1), title = titles.last(),
            breadcrumbs = listOfNotNull(rootName) + titles.drop(1))
        load()
        return true
    }

    fun navigateTo(index: Int) {
        if (index !in state.value.stack.indices || index == state.value.stack.lastIndex) return
        titles = titles.take(index + 1)
        mutableState.value = state.value.copy(stack = state.value.stack.take(index + 1), title = titles.last(),
            breadcrumbs = listOfNotNull(rootName) + titles.drop(1))
        load()
    }

    fun setQuery(query: String) {
        mutableState.value = state.value.copy(query = query, entries = entries.filter { it.name.contains(query, true) })
    }

    fun refresh() = load()
    fun setOpenDocuments(ids: List<DocumentId>) {
        mutableState.value = state.value.copy(workspace = state.value.workspace?.copy(openDocuments = ids))
    }

    fun toggleFolder(entry: FileEntry) {
        if (!entry.isDirectory) return
        if (entry.id in state.value.expanded) {
            mutableState.value = state.value.copy(expanded = state.value.expanded - entry.id)
            return
        }
        if (!expanding.add(entry.id)) return
        val token = treeGeneration
        viewModelScope.launch {
            try {
                val children = listDirectory(entry.id)
                if (token == treeGeneration) mutableState.value = state.value.copy(
                    expanded = state.value.expanded + entry.id, children = state.value.children + (entry.id to children))
            } catch (exception: CancellationException) { throw exception
            } catch (exception: Exception) {
                if (token == treeGeneration) mutableState.value = state.value.copy(error = exception.message.orEmpty(),
                    errorRes = R.string.error_folder_read_failed)
            } finally { if (token == treeGeneration) expanding.remove(entry.id) }
        }
    }

    fun create(name: String, directory: Boolean) {
        val parent = state.value.stack.lastOrNull() ?: return
        mutate { it.create(parent, name.trim(), directory) }
    }

    fun rename(entry: FileEntry, name: String) {
        if (protectOpenDocument(entry)) return
        mutate { it.rename(entry.id, name.trim()) }
    }

    fun delete(entry: FileEntry) {
        if (protectOpenDocument(entry)) return
        mutate { it.delete(entry.id) }
    }

    private fun protectOpenDocument(entry: FileEntry): Boolean {
        val opened = state.value.workspace?.openDocuments.orEmpty()
        val protected = entry.id in opened || (entry.isDirectory && opened.isNotEmpty())
        if (protected) mutableState.value = state.value.copy(error = "", errorRes = R.string.error_open_documents)
        return protected
    }

    private fun mutate(action: suspend (FileSystem) -> Unit) {
        val fs = fileSystem ?: return
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null, errorRes = null)
        viewModelScope.launch {
            try { action(fs); load()
            } catch (exception: CancellationException) { throw exception
            } catch (exception: Exception) { mutableState.value = state.value.copy(error = exception.message.orEmpty(),
                errorRes = R.string.error_operation_failed)
            } finally { mutableState.value = state.value.copy(busy = false) }
        }
    }
    fun dismissError() { mutableState.value = state.value.copy(error = null, errorRes = null) }

    private fun load() {
        val id = state.value.stack.lastOrNull() ?: return
        val token = ++request
        treeGeneration++
        expanding.clear()
        job?.cancel()
        entries = emptyList()
        mutableState.value = state.value.copy(entries = emptyList(), loading = true, error = null, errorRes = null,
            expanded = emptySet(), children = emptyMap())
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
                    error = exception.message.orEmpty(), errorRes = R.string.error_folder_read_failed)
            }
        }
    }
}
