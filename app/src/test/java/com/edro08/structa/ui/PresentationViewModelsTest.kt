package com.edro08.structa.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.edro08.structa.application.browser.ListDirectory
import com.edro08.structa.application.document.OpenDocument
import com.edro08.structa.application.document.SaveDocumentCopy
import com.edro08.structa.application.editor.ContentFormatDetector
import com.edro08.structa.application.editor.FormatJsonDocument
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.FileMode
import com.edro08.structa.domain.filesystem.DirectoryReader
import com.edro08.structa.domain.filesystem.FileReader
import com.edro08.structa.domain.filesystem.FileWriter
import com.edro08.structa.domain.settings.SettingsRepository
import com.edro08.structa.domain.settings.EditorFont
import com.edro08.structa.ui.screen.browser.BrowserViewModel
import com.edro08.structa.ui.screen.editor.EditorViewModel
import com.edro08.structa.ui.screen.home.HomeViewModel
import com.edro08.structa.ui.screen.settings.SettingsViewModel
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val file = FileEntry(DocumentId("file"), "archivo.json", 10, false)
    private val detector = object : ContentFormatDetector() {
        override fun detect(content: String) = FileMode.JSON
    }
    private val formatter = object : FormatJsonDocument() {
        override suspend fun invoke(content: String, mode: FileMode) = "formatted"
    }
    private val settings = object : SettingsRepository {
        var folder: DocumentId? = null
        var dark = true
        var font = EditorFont.MONOSPACE
        var size = 14
        override fun lastFolder() = folder
        override fun setLastFolder(folder: DocumentId) { this.folder = folder }
        override fun darkTheme() = dark
        override fun setDarkTheme(dark: Boolean) { this.dark = dark }
        override fun editorFont() = font
        override fun setEditorFont(font: EditorFont) { this.font = font }
        override fun editorFontSize() = size
        override fun setEditorFontSize(size: Int) { this.size = size }
    }
    private val reader = object : FileReader {
        override suspend fun read(file: FileEntry) = "uno\ndos uno"
    }
    private val writer = object : FileWriter {
        override suspend fun write(destination: DocumentId, content: String) = Unit
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun editorKeepsTextSelectionModeAndHistoryAndDerivesHelpers() = runTest(dispatcher) {
        val model = EditorViewModel(OpenDocument(reader, detector), formatter, SaveDocumentCopy(writer))
        model.open(file)
        model.state.first { it.entry != null && !it.loading }
        model.setMode(FileMode.YAML)
        model.edit(TextFieldValue("uno\nuno\ntres", TextRange(4)))
        model.setSearch("UNO")
        model.state.first { !it.searching }
        assertEquals(2, model.state.value.occurrences)
        assertEquals("1\n2\n3", model.state.value.lineNumbers)
        assertEquals(TextRange(4), model.state.value.value.selection)
        model.goToLine(3)
        assertEquals(TextRange(8), model.state.value.value.selection)
        model.undo()
        assertEquals("uno\ndos uno", model.state.value.value.text)
        model.redo()
        assertEquals("uno\nuno\ntres", model.state.value.value.text)
        assertEquals(FileMode.YAML, model.state.value.mode)
        model.goToLine(999)
        assertEquals(TextRange(12), model.state.value.value.selection)
        model.state.first { !it.searching }
    }

    @Test fun formattingUpdatesDerivedStateAndCanBeUndone() = runTest(dispatcher) {
        val model = EditorViewModel(OpenDocument(reader, detector), formatter, SaveDocumentCopy(writer))
        model.open(file)
        model.state.first { it.entry != null && !it.loading }
        model.setSearch("formatted")
        model.format()
        model.state.first { it.value.text == "formatted" && !it.formatting && !it.searching }
        assertEquals(1, model.state.value.occurrences)
        assertEquals("1", model.state.value.lineNumbers)
        model.undo()
        assertEquals("uno\ndos uno", model.state.value.value.text)
        assertEquals(0, model.state.value.occurrences)
        model.state.first { !it.searching }
    }

    @Test fun imeAndToolbarShareOneOperationHistoryAndSaveCommittedComposition() = runTest(dispatcher) {
        val model = EditorViewModel(OpenDocument(reader, detector), formatter, SaveDocumentCopy(writer))
        model.open(file)
        model.state.first { it.entry != null && !it.loading }
        val input = model.state.value.inputSession!!
        input.setSelection(0, 3)
        input.setComposingText("h", 1)
        input.setComposingText("hello", 1)
        assertEquals("hello\ndos uno", model.state.value.value.text)
        assertTrue(model.state.value.canUndo)
        assertNotNull(model.state.value.value.composition)
        val saved = model.prepareSave()
        assertEquals("hello\ndos uno", saved?.content)
        assertNull(input.composition)
        model.undo()
        assertEquals("uno\ndos uno", model.state.value.value.text)
        assertFalse(model.state.value.canUndo)
        assertTrue(model.state.value.canRedo)
        model.redo()
        assertEquals("hello\ndos uno", model.state.value.value.text)
    }

    @Test fun saveUsesLaunchSnapshotEvenAfterAnotherDocumentOpens() = runTest(dispatcher) {
        var saved: Pair<DocumentId, String>? = null
        val recordingWriter = object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) { saved = destination to content }
        }
        val model = EditorViewModel(OpenDocument(reader, detector), formatter, SaveDocumentCopy(recordingWriter))
        model.open(file)
        model.state.first { it.entry != null && !it.loading }
        model.edit(TextFieldValue("snapshot"))
        assertEquals("archivo.json", model.prepareSave()?.name)
        model.edit(TextFieldValue("later edit"))
        model.open(file.copy(id = DocumentId("other"), name = "otro"))
        model.state.first { it.entry?.id == DocumentId("other") && !it.loading }
        model.completeSave(DocumentId("copy"))
        runCurrent()
        assertEquals(DocumentId("copy") to "snapshot", saved)
        assertNull(model.state.value.pendingSave)
        assertFalse(model.state.value.saving)
    }

    @Test fun openAndSaveErrorsAreVisibleAndCancelledPickerDoesNotWrite() = runTest(dispatcher) {
        val failingReader = object : FileReader {
            override suspend fun read(file: FileEntry): String = throw IOException("lectura")
        }
        val failingWriter = object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String): Unit = throw IOException("escritura")
        }
        val model = EditorViewModel(OpenDocument(failingReader, detector), formatter, SaveDocumentCopy(writer))
        model.open(file)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertTrue(model.state.value.message!!.contains("lectura"))
        val editable = EditorViewModel(OpenDocument(reader, detector), formatter, SaveDocumentCopy(failingWriter))
        editable.open(file)
        editable.state.first { it.entry != null && !it.loading }
        editable.prepareSave()
        editable.completeSave(null)
        assertNull(editable.state.value.pendingSave)
        assertNull(editable.state.value.message)
        editable.prepareSave()
        editable.completeSave(DocumentId("copy"))
        runCurrent()
        assertTrue(editable.state.value.message!!.contains("escritura"))
        assertFalse(editable.state.value.saving)
    }

    @Test fun staleOpenCannotReplaceLatestDocument() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delayed = object : FileReader {
            override suspend fun read(file: FileEntry): String {
                if (file.id == DocumentId("file")) {
                    started.complete(Unit)
                    withContext(NonCancellable) { release.await() }
                }
                return file.name
            }
        }
        val model = EditorViewModel(OpenDocument(delayed, detector), formatter, SaveDocumentCopy(writer))
        model.open(file)
        started.await()
        model.open(file.copy(id = DocumentId("latest"), name = "latest"))
        model.state.first { it.entry?.id == DocumentId("latest") }
        release.complete(Unit)
        runCurrent()
        assertEquals("latest", model.state.value.value.text)
    }

    @Test fun formattingDoesNotOverwriteEditsMadeWhileItRuns() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blockingFormatter = object : FormatJsonDocument() {
            override suspend fun invoke(content: String, mode: FileMode): String {
                assertEquals("uno\ndos uno", content)
                assertEquals(FileMode.JSON, mode)
                started.complete(Unit)
                // Model work that finishes despite cancellation without blocking the test scheduler.
                withContext(NonCancellable) { release.await() }
                finished.complete(Unit)
                return "stale"
            }
        }
        val model = EditorViewModel(OpenDocument(reader, detector), blockingFormatter, SaveDocumentCopy(writer))
        model.open(file)
        model.state.first { it.entry != null && !it.loading }
        model.format()
        try {
            started.await()
            assertTrue(model.state.value.formatting)
            model.edit(TextFieldValue("new edit"))
        } finally {
            release.complete(Unit)
        }
        finished.await()
        runCurrent()
        assertEquals("new edit", model.state.value.value.text)
        assertFalse(model.state.value.formatting)
    }

    @Test fun browserUsesMetadataStackFilteringAndLastFolderPreferences() = runTest(dispatcher) {
        val folder = file.copy(id = DocumentId("child"), name = "Carpeta", isDirectory = true)
        val directoryReader = object : DirectoryReader {
            override suspend fun list(directory: DocumentId) = listOf(file, folder)
        }
        val model = BrowserViewModel(ListDirectory(directoryReader), settings)
        val home = HomeViewModel(settings)
        model.selectFolder(DocumentId("root"))
        runCurrent()
        home.refresh()
        assertEquals(DocumentId("root"), home.state.value.lastFolder)
        assertEquals(listOf(folder, file), model.state.value.entries)
        model.setQuery("JSON")
        assertEquals(listOf(file), model.state.value.entries)
        model.enter(folder)
        runCurrent()
        assertEquals(listOf(DocumentId("root"), folder.id), model.state.value.stack)
        assertEquals("Carpeta", model.state.value.title)
        assertTrue(model.back())
        runCurrent()
        assertFalse(model.back())
        assertEquals(DocumentId("root"), settings.lastFolder())
    }

    @Test fun browserDiscardsStaleListingAndReportsErrors() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val directoryReader = object : DirectoryReader {
            override suspend fun list(directory: DocumentId): List<FileEntry> {
                if (directory.value == "slow") withContext(NonCancellable) { release.await() }
                if (directory.value == "bad") throw IOException("sin permiso")
                return listOf(file.copy(name = directory.value))
            }
        }
        val model = BrowserViewModel(ListDirectory(directoryReader), settings)
        model.selectFolder(DocumentId("slow"))
        runCurrent()
        model.selectFolder(DocumentId("fast"))
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertEquals("fast", model.state.value.entries.single().name)
        model.selectFolder(DocumentId("bad"))
        runCurrent()
        assertTrue(model.state.value.error!!.contains("sin permiso"))
        assertFalse(model.state.value.loading)
    }

    @Test fun shizukuSelectionIsOnlyPresentationState() {
        val model = SettingsViewModel(settings)
        model.selectProvider("Shizuku")
        assertEquals("Shizuku", model.state.value.provider)
        assertNull(settings.lastFolder())
    }

    @Test fun appearancePreferencesSurviveSettingsViewModelRecreation() {
        val model = SettingsViewModel(settings)
        assertTrue(model.state.value.darkTheme)
        assertEquals(14, model.state.value.editorFontSize)
        model.selectTheme(false)
        model.selectFont(EditorFont.SANS_MONOSPACE)
        model.selectFontSize(20)
        model.selectFontSize(100)
        val restored = SettingsViewModel(settings).state.value
        assertFalse(restored.darkTheme)
        assertEquals(EditorFont.SANS_MONOSPACE, restored.editorFont)
        assertEquals(20, restored.editorFontSize)
        assertEquals("SAF", restored.provider)
    }
}
