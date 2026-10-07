package com.edro08.structa.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.edro08.structa.application.browser.ListDirectory
import com.edro08.structa.application.document.*
import com.edro08.structa.application.editor.*
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.*
import com.edro08.structa.domain.settings.SettingsRepository
import com.edro08.structa.domain.workspace.*
import com.edro08.structa.ui.screen.browser.BrowserViewModel
import com.edro08.structa.ui.screen.editor.EditorViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceTest {
    private val dispatcher = StandardTestDispatcher()
    private val a = FileEntry(DocumentId("a"), "a.txt", 3, false)
    private val b = a.copy(id = DocumentId("b"), name = "b.txt")
    private val folder = a.copy(id = DocumentId("root"), name = "root", isDirectory = true)
    private val fs = MemoryFileSystem()
    private val sessions = object : SessionRepository {
        var value = EditorSession()
        override fun load() = value
        override fun save(session: EditorSession) { value = session }
    }
    private val settings = object : SettingsRepository {
        var root: DocumentId? = null
        override fun lastFolder() = root
        override fun setLastFolder(folder: DocumentId) { root = folder }
    }

    private inner class MemoryFileSystem : FileSystem {
        override val key = "memory"
        val entries = linkedMapOf(a.id to a, b.id to b, folder.id to folder)
        val text = mutableMapOf(a.id to "one", b.id to "two")
        val lists = mutableListOf<DocumentId>()
        var mutations = 0
        override suspend fun stat(id: DocumentId) = entries.getValue(id)
        override suspend fun read(file: FileEntry) = text.getValue(file.id)
        override suspend fun write(destination: DocumentId, content: String) { text[destination] = content }
        override suspend fun list(directory: DocumentId): List<FileEntry> {
            lists += directory
            return if (directory == folder.id) listOf(a, b) else listOf(folder)
        }
        override suspend fun create(parent: DocumentId, name: String, directory: Boolean): FileEntry {
            mutations++
            return FileEntry(DocumentId(name), name, 0, directory).also { entries[it.id] = it }
        }
        override suspend fun rename(file: DocumentId, name: String): FileEntry {
            mutations++
            return entries.remove(file)!!.copy(id = DocumentId(name), name = name).also { entries[it.id] = it }
        }
        override suspend fun delete(file: DocumentId) { mutations++; entries.remove(file) }
    }

    private fun editor() = EditorViewModel(OpenDocument(fs, ContentFormatDetector()), FormatJsonDocument(),
        SaveDocumentCopy(fs), sessions, fs)
    private suspend fun ready(model: EditorViewModel) { model.state.first { !it.loading } }
    private suspend fun open(model: EditorViewModel, file: FileEntry) {
        model.open(file); model.state.first { !it.loading && it.entry?.id == file.id }
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun newViewModelReloadsDiskAndRestoresOrderedTabsSelectionScrollAndActiveTab() = runTest(dispatcher) {
        val first = editor(); ready(first)
        open(first, a)
        first.edit(TextFieldValue("unsaved", TextRange(2, 5)))
        first.state.value.viewState.scrollX = 41f
        first.state.value.viewState.scrollY = 85f
        open(first, b)
        first.selectDocument(a.id)
        first.checkpointSession()
        val second = editor(); ready(second)
        assertEquals(listOf(a.id, b.id), second.state.value.tabs.map { it.documentId })
        assertEquals(a.id, second.state.value.entry?.id)
        assertEquals("one", second.state.value.value.text)
        assertEquals(TextRange(2, 3), second.state.value.value.selection)
        assertEquals(41f, second.state.value.viewState.scrollX)
        assertEquals(85f, second.state.value.viewState.scrollY)
        assertFalse(second.state.value.dirty)
        assertFalse(second.state.value.canUndo)
    }

    @Test fun missingActiveFileDoesNotPreventRestoringRemainingTabs() = runTest(dispatcher) {
        val missing = DocumentId("missing")
        sessions.value = EditorSession(listOf(SessionTab(missing, 0, 0, 0f, 0f),
            SessionTab(b.id, 99, 99, 0f, 0f)), missing)
        val model = editor(); ready(model)
        assertEquals(b.id, model.state.value.entry?.id)
        assertEquals(TextRange(3), model.state.value.value.selection)
        assertTrue(model.state.value.message!!.contains("1 archivos"))
        assertEquals(listOf(b.id), sessions.value.tabs.map { it.id })
    }

    @Test fun scrollOnlyChangesAndClosingLastTabUpdateMetadata() = runTest(dispatcher) {
        val model = editor(); ready(model); open(model, a)
        model.state.value.viewState.scrollY = 123f
        advanceUntilIdle()
        assertEquals(123f, sessions.value.tabs.single().scrollY)
        model.requestClose(a.id)
        advanceUntilIdle()
        assertTrue(sessions.value.tabs.isEmpty())
        assertNull(sessions.value.active)
    }

    @Test fun workspaceRestoresRootAndLoadsChildrenOnlyWhenExpanded() = runTest(dispatcher) {
        val root = DocumentId("workspace")
        settings.root = root
        val browser = BrowserViewModel(ListDirectory(fs), settings, fs)
        runCurrent()
        assertEquals(root, browser.state.value.workspace?.root?.id)
        assertEquals(listOf(root), fs.lists)
        browser.toggleFolder(folder); runCurrent()
        assertEquals(listOf(root, folder.id), fs.lists)
        assertEquals(listOf(a, b), browser.state.value.children[folder.id])
        browser.toggleFolder(folder)
        assertTrue(browser.state.value.expanded.isEmpty())
        assertEquals(2, fs.lists.size)
    }

    @Test fun breadcrumbsShowSelectedRootAndNestedFolderNames() = runTest(dispatcher) {
        val browser = BrowserViewModel(ListDirectory(fs), settings, fs)
        browser.selectFolder(folder.id)
        runCurrent()
        assertEquals(listOf("root"), browser.state.value.breadcrumbs)
        val nested = folder.copy(id = DocumentId("nested"), name = "Proyecto")
        browser.enter(nested)
        runCurrent()
        assertEquals(listOf("root", "Proyecto"), browser.state.value.breadcrumbs)
        assertTrue(browser.back())
        runCurrent()
        assertEquals(listOf("root"), browser.state.value.breadcrumbs)
    }

    @Test fun mutationsRefreshAndProtectOpenFilesAndDirectories() = runTest(dispatcher) {
        val browser = BrowserViewModel(ListDirectory(fs), settings, fs)
        browser.selectFolder(folder.id); runCurrent()
        browser.create("new.txt", false); runCurrent()
        assertTrue(DocumentId("new.txt") in fs.entries)
        browser.rename(b, "renamed"); runCurrent()
        assertFalse(b.id in fs.entries)
        browser.delete(fs.entries.getValue(DocumentId("renamed"))); runCurrent()
        assertEquals(3, fs.mutations)
        browser.setOpenDocuments(listOf(a.id))
        browser.rename(a, "blocked")
        browser.delete(folder); runCurrent()
        assertEquals(3, fs.mutations)
        assertNotNull(browser.state.value.error)
    }
}
