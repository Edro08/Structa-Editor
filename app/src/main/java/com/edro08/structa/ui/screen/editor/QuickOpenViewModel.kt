package com.edro08.structa.ui.screen.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edro08.structa.application.browser.ListDirectory
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.workspace.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QuickOpenState(val visible: Boolean = false, val query: String = "",
    val files: List<IndexedFile> = emptyList(), val indexing: Boolean = false,
    val indexedCount: Int = 0, val message: String? = null)

class QuickOpenViewModel(private val listDirectory: ListDirectory) : ViewModel() {
    private val mutableState = MutableStateFlow(QuickOpenState())
    val state = mutableState.asStateFlow()
    private var root: DocumentId? = null
    private var scanJob: Job? = null
    private var filterJob: Job? = null
    private var index = emptyList<IndexedFile>()
    private var generation = 0L

    fun setRoot(id: DocumentId?) {
        if (root == id) return
        root = id
        val visible = state.value.visible
        close()
        index = emptyList()
        if (visible) open()
    }

    fun open() {
        close()
        index = emptyList()
        mutableState.value = QuickOpenState(visible = true, indexing = root != null,
            message = if (root == null) "Elige una carpeta de workspace en Explorar." else null)
        val root = root ?: return
        val token = generation
        scanJob = viewModelScope.launch {
            val queue = ArrayDeque<Pair<DocumentId, String>>()
            val visited = mutableSetOf<DocumentId>()
            val found = linkedMapOf<DocumentId, IndexedFile>()
            var failed = 0
            var limited = false
            var lastPublish = 0L
            queue.add(root to "")
            while (queue.isNotEmpty()) {
                ensureActive()
                val (id, path) = queue.removeFirst()
                if (!visited.add(id)) continue
                if (visited.size > 5_000) { limited = true; break }
                try {
                    val entries = withContext(Dispatchers.IO) { listDirectory(id) }
                    ensureActive()
                    for (entry in entries) {
                        val relative = if (path.isEmpty()) entry.name else "$path/${entry.name}"
                        if (entry.isDirectory) {
                            if (queue.size + visited.size < 5_000) queue.add(entry.id to relative) else limited = true
                        } else found.putIfAbsent(entry.id, IndexedFile(entry, relative))
                        if (found.size >= 20_000) { limited = true; break }
                    }
                } catch (exception: CancellationException) { throw exception
                } catch (_: Exception) { failed++ }
                if (token != generation) return@launch
                if (System.nanoTime() - lastPublish > 100_000_000L) {
                    index = found.values.toList()
                    mutableState.value = state.value.copy(indexedCount = index.size)
                    filter()
                    lastPublish = System.nanoTime()
                }
                if (found.size >= 20_000) break
            }
            if (token != generation) return@launch
            index = found.values.toList()
            mutableState.value = state.value.copy(indexing = false, indexedCount = index.size,
                message = listOfNotNull(
                    if (failed > 0) "No se pudieron leer $failed carpetas." else null,
                    if (limited) "Índice parcial: límite de 20 000 archivos / 5 000 carpetas." else null
                ).joinToString(" ").ifEmpty { null })
            filter()
        }
    }

    fun setQuery(query: String) { mutableState.value = state.value.copy(query = query); filter() }

    private fun filter() {
        filterJob?.cancel()
        val files = index
        val query = state.value.query
        val token = generation
        filterJob = viewModelScope.launch {
            val results = withContext(Dispatchers.Default) { FuzzySearch.files(files, query) }
            if (token == generation) mutableState.value = state.value.copy(files = results)
        }
    }

    fun close() {
        generation++
        scanJob?.cancel()
        filterJob?.cancel()
        mutableState.value = state.value.copy(visible = false, indexing = false)
    }
}
