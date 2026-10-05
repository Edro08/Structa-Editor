package com.edro08.structa.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.edro08.structa.application.document.*
import com.edro08.structa.application.editor.*
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.editor.search.SearchOptions
import com.edro08.structa.domain.filesystem.*
import com.edro08.structa.ui.screen.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductivityTest {
    private val dispatcher = StandardTestDispatcher()
    private val file = FileEntry(DocumentId("a"), "a.txt", 20, false)
    private fun model(text: String = "cat dog cat\nCAT") = EditorViewModel(
        OpenDocument(object : FileReader { override suspend fun read(file: FileEntry) = text }, ContentFormatDetector()),
        FormatJsonDocument(), SaveDocumentCopy(object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) = Unit
        }))
    private suspend fun open(model: EditorViewModel, entry: FileEntry = file) {
        model.open(entry); model.state.first { it.entry?.id == entry.id && !it.loading }
    }
    private suspend fun search(model: EditorViewModel, query: String) {
        model.setSearch(query); model.state.first { !it.searching }
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun navigationWrapsBothDirectionsAndPreservesUndoHistory() = runTest(dispatcher) {
        val model = model(); open(model); search(model, "cat")
        model.findNext(); assertEquals(TextRange(0, 3), model.state.value.value.selection)
        model.findNext(); assertEquals(TextRange(8, 11), model.state.value.value.selection)
        model.findNext(); assertEquals(TextRange(12, 15), model.state.value.value.selection)
        model.findNext(); assertEquals(TextRange(0, 3), model.state.value.value.selection)
        model.findNext(true); assertEquals(TextRange(12, 15), model.state.value.value.selection)
        assertFalse(model.state.value.canUndo)
        model.goToLine(2); assertEquals(TextRange(12), model.state.value.value.selection)
    }

    @Test fun replaceAllCommitsImeAndUndoRestoresTextSelectionAndCleanRevision() = runTest(dispatcher) {
        val model = model(); open(model); search(model, "cat")
        model.findNext()
        model.setReplacement("kitten")
        model.state.value.inputSession!!.setComposingRegion(0, 3)
        model.replace(true)
        model.state.first { !it.replacing && !it.searching }
        assertEquals("kitten dog kitten\nkitten", model.state.value.value.text)
        assertTrue(model.state.value.dirty)
        assertNull(model.state.value.inputSession!!.composition)
        model.undo()
        assertEquals("cat dog cat\nCAT", model.state.value.value.text)
        assertEquals(TextRange(0, 3), model.state.value.value.selection)
        assertFalse(model.state.value.dirty)
        assertFalse(model.state.value.canUndo)
        model.state.first { !it.searching }
    }

    @Test fun singleReplaceSelectsFirstThenEditsOnlySelectedMatch() = runTest(dispatcher) {
        val model = model(); open(model); search(model, "cat"); model.setReplacement("x")
        model.replace()
        assertEquals(TextRange(0, 3), model.state.value.value.selection)
        assertFalse(model.state.value.dirty)
        model.replace()
        model.state.first { !it.replacing && !it.searching }
        assertEquals("x dog cat\nCAT", model.state.value.value.text)
        assertEquals(2, model.state.value.occurrences)
    }

    @Test fun rapidQueriesEditsAndInvalidRegexNeverReuseStaleRanges() = runTest(dispatcher) {
        val model = model(); open(model)
        model.setSearch("cat"); model.setSearch("dog")
        model.edit(TextFieldValue("dog dog"))
        model.state.first { !it.searching }
        assertEquals(2, model.state.value.occurrences)
        model.setSearchOptions(SearchOptions(regex = true)); search(model, "[")
        assertNotNull(model.state.value.searchResult.error)
        model.setReplacement("bad"); model.replace(true)
        assertEquals("dog dog", model.state.value.value.text)
    }

    @Test fun switchingDocumentDuringReplacementCannotEditEitherTab() = runTest(dispatcher) {
        val model = model(); open(model)
        val other = file.copy(id = DocumentId("b"))
        open(model, other); model.selectDocument(file.id)
        search(model, "cat"); model.setReplacement("x"); model.replace(true)
        model.selectDocument(other.id)
        model.selectDocument(file.id)
        model.state.first { !it.replacing }
        assertEquals("cat dog cat\nCAT", model.state.value.value.text)
        assertFalse(model.state.value.dirty)
    }

    @Test fun largeFileMetadataDoesNotBlockFindOrReplace() = runTest(dispatcher) {
        val model = model(); open(model, file.copy(sizeBytes = 2 * 1024 * 1024))
        search(model, "cat")
        model.findNext(); assertEquals(TextRange(0, 3), model.state.value.value.selection)
        model.setReplacement("x"); model.replace(true)
        model.state.first { !it.replacing && it.dirty }
        model.state.first { !it.searching }
        assertEquals("x dog x\nx", model.state.value.value.text)
    }
}
