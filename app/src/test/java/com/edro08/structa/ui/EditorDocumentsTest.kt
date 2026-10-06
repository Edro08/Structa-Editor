package com.edro08.structa.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.edro08.structa.application.document.OpenDocument
import com.edro08.structa.application.document.SaveDocumentCopy
import com.edro08.structa.application.editor.ContentFormatDetector
import com.edro08.structa.application.editor.FormatJsonDocument
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.*
import com.edro08.structa.ui.screen.editor.EditorViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorDocumentsTest {
    private val dispatcher = StandardTestDispatcher()
    private val a = FileEntry(DocumentId("a"), "a.txt", 3, false)
    private val b = a.copy(id = DocumentId("b"), name = "b.txt")
    private val c = a.copy(id = DocumentId("c"), name = "c.txt")
    private val disk = mutableMapOf(a.id to "one", b.id to "two", c.id to "three")
    private var reads = 0
    private val reader = object : FileReader {
        override suspend fun read(file: FileEntry): String { reads++; return disk.getValue(file.id) }
    }
    private val writer = object : FileWriter {
        override suspend fun write(destination: DocumentId, content: String) { disk[destination] = content }
    }
    private fun model(writer: FileWriter = this.writer) = EditorViewModel(
        OpenDocument(reader, ContentFormatDetector()), FormatJsonDocument(), SaveDocumentCopy(writer))

    private suspend fun open(model: EditorViewModel, file: FileEntry) {
        model.open(file)
        model.state.first { it.entry?.id == file.id && !it.loading }
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun largeDocumentSupportsEditingReplacementHistoryAndBothSavePaths() = runTest(dispatcher) {
        val original = "unique-marker\n" + "large document line with some content\n".repeat(60_000)
        val file = a.copy(sizeBytes = original.length.toLong())
        disk[a.id] = original
        val model = model()
        open(model, file)
        model.edit(TextFieldValue("!$original"))
        assertTrue(model.state.value.dirty)
        model.undo()
        assertEquals(original, model.state.value.value.text)
        model.redo()
        assertEquals("!$original", model.state.value.value.text)
        model.setSearch("unique-marker")
        model.state.first { !it.searching && it.occurrences == 1 }
        model.setReplacement("replacement")
        model.replace(all = true)
        val expected = "!" + original.replace("unique-marker", "replacement")
        model.state.first { !it.replacing && it.value.text == expected }
        model.state.first { !it.searching }
        model.prepareSave()
        model.completeSave(DocumentId("copy"))
        model.state.first { !it.saving }
        assertEquals(expected, disk[DocumentId("copy")])
        assertTrue(model.state.value.dirty)
        model.save()
        model.state.first { !it.saving }
        assertEquals(expected, disk[a.id])
        assertFalse(model.state.value.dirty)
    }

    @Test fun switchingTabsPreservesHistorySelectionModeSearchAndViewportWithoutReloading() = runTest(dispatcher) {
        val model = model()
        open(model, a)
        val input = model.state.value.inputSession
        model.edit(TextFieldValue("changed", TextRange(1, 4)))
        model.setMode(FileMode.YAML)
        model.setSearch("change")
        model.state.value.viewState.scrollY = 120f
        open(model, b)
        assertFalse(model.state.value.dirty)
        assertFalse(model.state.value.canUndo)
        open(model, a)
        assertEquals(2, reads)
        assertEquals(2, model.state.value.tabs.size)
        assertSame(input, model.state.value.inputSession)
        assertEquals(TextRange(1, 4), model.state.value.value.selection)
        assertEquals(FileMode.YAML, model.state.value.mode)
        assertEquals("change", model.state.value.search)
        assertEquals(120f, model.state.value.viewState.scrollY)
        assertTrue(model.state.value.dirty)
        model.undo()
        assertEquals("one", model.state.value.value.text)
        assertFalse(model.state.value.dirty)
        model.redo()
        assertTrue(model.state.value.dirty)
        model.state.first { !it.searching }
    }

    @Test fun switchingCommitsCompositionAndUndoStillGroupsIt() = runTest(dispatcher) {
        val model = model()
        open(model, a)
        val input = model.state.value.inputSession!!
        input.setComposingText("x", 1)
        input.setComposingText("xy", 1)
        open(model, b)
        assertNull(input.composition)
        model.selectDocument(a.id)
        model.undo()
        assertEquals("one", model.state.value.value.text)
        assertFalse(model.state.value.dirty)
    }

    @Test fun saveCloseReopenUsesDiskAndCopyDoesNotMarkSourceClean() = runTest(dispatcher) {
        val model = model()
        open(model, a)
        model.edit(TextFieldValue("persisted"))
        model.prepareSave()
        model.completeSave(DocumentId("copy")); runCurrent()
        assertEquals("persisted", disk[DocumentId("copy")])
        assertTrue(model.state.value.dirty)
        model.requestClose(a.id)
        assertEquals(a.id, model.state.value.pendingClose)
        model.cancelClose()
        assertEquals(1, model.state.value.tabs.size)
        model.requestClose(a.id)
        model.saveAndClose(); runCurrent()
        assertTrue(model.state.value.tabs.isEmpty())
        assertNull(model.state.value.entry)
        assertEquals("persisted", disk[a.id])
        open(model, a)
        assertEquals("persisted", model.state.value.value.text)
        assertFalse(model.state.value.dirty)
        assertFalse(model.state.value.canUndo)
    }

    @Test fun delayedSaveMarksOnlyCapturedRevisionOfTheSourceTab() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val model = model(object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) {
                release.await(); disk[destination] = content
            }
        })
        open(model, a)
        model.edit(TextFieldValue("snapshot"))
        model.save(); runCurrent()
        model.edit(TextFieldValue("newer"))
        open(model, b)
        release.complete(Unit); runCurrent()
        assertFalse(model.state.value.dirty)
        assertTrue(model.state.value.tabs.first { it.documentId == a.id }.dirty)
        assertEquals("snapshot", disk[a.id])
        model.selectDocument(a.id)
        assertTrue(model.state.value.dirty)
        model.undo()
        assertEquals("snapshot", model.state.value.value.text)
        assertFalse(model.state.value.dirty)
    }

    @Test fun saveAllWritesOnlyDirtyTabsAndKeepsTheActiveTab() = runTest(dispatcher) {
        val written = mutableListOf<DocumentId>()
        val model = model(object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) {
                written += destination
                disk[destination] = content
            }
        })
        open(model, a)
        model.edit(TextFieldValue("edited a"))
        open(model, b)
        open(model, c)
        model.edit(TextFieldValue("edited c"))

        model.saveAll(); runCurrent()
        assertEquals(listOf(a.id, c.id), written)
        assertEquals("edited a", disk[a.id])
        assertEquals("two", disk[b.id])
        assertEquals("edited c", disk[c.id])
        assertEquals(c.id, model.state.value.entry?.id)
        assertTrue(model.state.value.tabs.none { it.dirty })
        model.undo()
        assertEquals("three", model.state.value.value.text)
        assertTrue(model.state.value.dirty)
    }

    @Test fun saveAllContinuesAfterFailureAndLeavesFailedTabDirty() = runTest(dispatcher) {
        val model = model(object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) {
                if (destination == a.id) error("denied")
                disk[destination] = content
            }
        })
        open(model, a)
        model.edit(TextFieldValue("unsaved a"))
        open(model, b)
        model.edit(TextFieldValue("saved b"))

        model.saveAll(); runCurrent()
        assertEquals("one", disk[a.id])
        assertEquals("saved b", disk[b.id])
        assertEquals(listOf(true, false), model.state.value.tabs.map { it.dirty })
        assertTrue(model.state.value.message!!.contains("denied"))
        assertFalse(model.state.value.saving)
    }

    @Test fun saveAllPreservesEditsMadeWhileWritingAndRejectsConcurrentSave() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val written = mutableListOf<DocumentId>()
        val model = model(object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) {
                if (destination == a.id) release.await()
                written += destination
                disk[destination] = content
            }
        })
        open(model, a)
        model.edit(TextFieldValue("snapshot"))
        open(model, b)
        model.edit(TextFieldValue("edited b"))
        model.saveAll(); runCurrent()
        assertTrue(model.state.value.saving)
        model.selectDocument(a.id)
        model.edit(TextFieldValue("newer"))
        model.saveAll()
        model.requestClose(b.id)
        assertEquals(2, model.state.value.tabs.size)

        release.complete(Unit); runCurrent()
        assertEquals(listOf(a.id, b.id), written)
        assertEquals("snapshot", disk[a.id])
        assertEquals("edited b", disk[b.id])
        assertEquals(a.id, model.state.value.entry?.id)
        assertEquals(listOf(true, false), model.state.value.tabs.map { it.dirty })
        assertTrue(model.state.value.dirty)
    }

    @Test fun saveFailureKeepsDirtyTabAndDiscardClosesOnlyRequestedDocument() = runTest(dispatcher) {
        val model = model(object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) { error("denied") }
        })
        open(model, a)
        model.edit(TextFieldValue("unsaved"))
        open(model, b)
        model.requestClose(a.id)
        model.saveAndClose(); runCurrent()
        assertEquals(2, model.state.value.tabs.size)
        assertTrue(model.state.value.tabs.first().dirty)
        assertTrue(model.state.value.message!!.contains("denied"))
        model.discardAndClose()
        assertEquals(b.id, model.state.value.entry?.id)
        assertEquals(1, model.state.value.tabs.size)
        assertEquals("one", disk[a.id])
        model.requestClose(b.id)
        assertNull(model.state.value.entry)
    }

    @Test fun editingDuringSaveAndCloseKeepsNewChangesOpen() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val model = model(object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) { release.await() }
        })
        open(model, a)
        model.edit(TextFieldValue("snapshot"))
        model.requestClose(a.id)
        model.saveAndClose(); runCurrent()
        model.edit(TextFieldValue("newer"))
        release.complete(Unit); runCurrent()
        assertEquals(a.id, model.state.value.entry?.id)
        assertEquals(a.id, model.state.value.pendingClose)
        assertTrue(model.state.value.dirty)
    }

    @Test fun closeOthersPromptsForEachDirtyTabAndKeepsSelectedDocument() = runTest(dispatcher) {
        val model = model()
        open(model, a)
        model.edit(TextFieldValue("saved a"))
        open(model, b)
        model.edit(TextFieldValue("discarded b"))
        open(model, c)

        model.requestCloseOthers()
        assertEquals(a.id, model.state.value.pendingClose)
        model.saveAndClose(); runCurrent()
        assertEquals(b.id, model.state.value.pendingClose)
        model.discardAndClose()

        assertNull(model.state.value.pendingClose)
        assertEquals(listOf(c.id), model.state.value.tabs.map { it.documentId })
        assertEquals(c.id, model.state.value.entry?.id)
        assertEquals("saved a", disk[a.id])
        assertEquals("two", disk[b.id])
    }

    @Test fun closeAllStopsOnCancelAndCanBeRestarted() = runTest(dispatcher) {
        val model = model()
        open(model, a)
        open(model, b)
        model.edit(TextFieldValue("unsaved b"))
        open(model, c)

        model.requestCloseAll()
        assertEquals(listOf(b.id, c.id), model.state.value.tabs.map { it.documentId })
        assertEquals(b.id, model.state.value.pendingClose)
        model.cancelClose()
        assertNull(model.state.value.pendingClose)
        assertEquals(listOf(b.id, c.id), model.state.value.tabs.map { it.documentId })

        model.requestCloseAll()
        model.discardAndClose()
        assertTrue(model.state.value.tabs.isEmpty())
        assertNull(model.state.value.entry)
        assertEquals("two", disk[b.id])
    }

    @Test fun failedSaveStopsCloseAllBeforeOtherDirtyDocuments() = runTest(dispatcher) {
        val model = model(object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) { error("denied") }
        })
        open(model, a)
        model.edit(TextFieldValue("unsaved a"))
        open(model, b)
        model.edit(TextFieldValue("unsaved b"))
        open(model, c)

        model.requestCloseAll()
        model.saveAndClose(); runCurrent()
        assertEquals(a.id, model.state.value.pendingClose)
        assertEquals(3, model.state.value.tabs.size)
        model.discardAndClose()
        assertEquals(listOf(b.id, c.id), model.state.value.tabs.map { it.documentId })
        assertNull(model.state.value.pendingClose)
        assertTrue(model.state.value.tabs.first().dirty)
    }
}
