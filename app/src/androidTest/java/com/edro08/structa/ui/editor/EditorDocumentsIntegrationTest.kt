package com.edro08.structa.ui.editor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import com.edro08.structa.application.document.OpenDocument
import com.edro08.structa.application.document.SaveDocumentCopy
import com.edro08.structa.application.editor.ContentFormatDetector
import com.edro08.structa.application.editor.FormatJsonDocument
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.*
import com.edro08.structa.ui.screen.editor.EditorScreen
import com.edro08.structa.ui.screen.editor.EditorViewModel
import com.edro08.structa.ui.theme.StructaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.view.inputmethod.EditorInfo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.edro08.structa.ui.editor.view.StructaEditorView

class EditorDocumentsIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val a = FileEntry(DocumentId("a"), "a.txt", 3, false)
    private val b = a.copy(id = DocumentId("b"), name = "b.txt")
    private val disk = mutableMapOf(a.id to "one", b.id to "two")
    private lateinit var model: EditorViewModel

    private fun launch() {
        compose.runOnIdle {
            model = EditorViewModel(OpenDocument(object : FileReader {
                override suspend fun read(file: FileEntry) = disk.getValue(file.id)
            }, ContentFormatDetector()), FormatJsonDocument(), SaveDocumentCopy(object : FileWriter {
                override suspend fun write(destination: DocumentId, content: String) { disk[destination] = content }
            }))
        }
        compose.setContent {
            val state by model.state.collectAsState()
            StructaTheme {
                 EditorScreen(state, {}, {}, model::setLanguage, model::setSearch, model::format,
                    model::undo, model::redo, model::goToLine, {}, model::showMessage, model::save,
                    model::selectDocument, model::requestClose, model::cancelClose, model::discardAndClose,
                     model::saveAndClose, onSaveAll = model::saveAll, onCloseAll = model::requestCloseAll,
                    onCloseOthers = model::requestCloseOthers)
            }
        }
        open(a)
    }

    private fun open(file: FileEntry) {
        compose.runOnIdle { model.open(file) }
        compose.waitUntil(5_000) { model.state.value.entry?.id == file.id && !model.state.value.loading }
    }

    @Test fun tabsShowDirtyStateAndCloseDialogCanCancelOrDiscard() {
        launch()
        compose.runOnIdle { model.edit(TextFieldValue("unsaved")) }
        open(b)
        compose.onNodeWithContentDescription("a.txt ●").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals("unsaved", model.state.value.value.text) }
        compose.onNodeWithContentDescription("a.txt ●").assertIsSelected()
        compose.onNodeWithContentDescription("Cerrar a.txt").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithContentDescription("a.txt ●").assertExists()
        compose.onNodeWithContentDescription("Cerrar a.txt").performClick()
        compose.onNodeWithText("Descartar").performClick()
        compose.onNodeWithContentDescription("a.txt ●").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(b.id, model.state.value.entry?.id)
            assertEquals("one", disk[a.id])
        }
    }

    @Test fun saveAndCloseFromDialogPersistsAndReopeningIsClean() {
        launch()
        compose.runOnIdle { model.edit(TextFieldValue("saved")) }
        compose.onNodeWithText("Archivo").performClick()
        compose.onNodeWithText("Cerrar", substring = false).performClick()
        compose.onNodeWithText("Guardar y cerrar").performClick()
        compose.waitUntil(5_000) { model.state.value.tabs.isEmpty() }
        compose.onNodeWithText("No hay un archivo abierto").assertExists()
        open(a)
        compose.runOnIdle {
            assertEquals("saved", model.state.value.value.text)
            assertFalse(model.state.value.dirty)
        }
    }

    @Test fun activeTabRemainsReachableWhenMoreTabsThanFitOnScreen() {
        launch()
        val files = (0..5).map { index ->
            FileEntry(DocumentId("tab$index"), "file-$index.json", 3, false)
        }
        files.forEach { file ->
            disk[file.id] = "one"
            open(file)
        }
        compose.onNodeWithContentDescription("file-5.json").assertIsSelected()
        compose.onNodeWithContentDescription("Cerrar file-5.json").performClick()
        compose.runOnIdle { assertEquals(6, model.state.value.tabs.size) }
        compose.runOnIdle { model.selectDocument(files.first().id) }
        compose.onNodeWithContentDescription("file-0.json").assertIsSelected()
        compose.runOnIdle { assertEquals(files.first().id, model.state.value.entry?.id) }
    }

    @Test fun fileMenuClosesOtherTabsAndThenAllTabs() {
        launch()
        compose.runOnIdle { model.edit(TextFieldValue("unsaved")) }
        open(b)
        compose.onNodeWithText("Archivo").performClick()
        compose.onNodeWithText("Cerrar los demás").assertIsEnabled().performClick()
        compose.onNodeWithText("Descartar").performClick()
        compose.runOnIdle {
            assertEquals(listOf(b.id), model.state.value.tabs.map { it.documentId })
            assertEquals("one", disk[a.id])
        }
        open(a)
        compose.onNodeWithText("Archivo").performClick()
        compose.onNodeWithText("Cerrar todos").assertIsEnabled().performClick()
        compose.onNodeWithText("No hay un archivo abierto").assertExists()
        compose.runOnIdle { assertTrue(model.state.value.tabs.isEmpty()) }
    }

    @Test fun fileMenuSaveAllPersistsDirtyTabsWithoutSwitching() {
        launch()
        compose.onNodeWithText("Archivo").performClick()
        compose.onNodeWithText("Guardar todo").assertIsNotEnabled()
        compose.onNodeWithText("Guardar Como...").assertExists()
        compose.onNodeWithText("Guardar todo").assertExists()
        compose.runOnIdle { model.edit(TextFieldValue("edited a")) }
        open(b)
        compose.runOnIdle { model.edit(TextFieldValue("edited b")) }
        compose.onNodeWithText("Guardar todo").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { !model.state.value.saving && model.state.value.tabs.none { it.dirty } }
        compose.runOnIdle {
            assertEquals("edited a", disk[a.id])
            assertEquals("edited b", disk[b.id])
            assertEquals(b.id, model.state.value.entry?.id)
        }
    }

    @Test fun largeDocumentAcceptsImeEditsSelectionUndoRedoAndSave() {
        launch()
        val original = "large document line with some content\n".repeat(60_000)
        val file = b.copy(sizeBytes = original.length.toLong())
        disk[b.id] = original
        open(file)
        compose.onNodeWithText("Documento grande:", substring = true).assertDoesNotExist()
        onView(isAssignableFrom(StructaEditorView::class.java)).check { raw, error ->
            if (error != null) throw error
            val view = raw as StructaEditorView
            assertTrue(view.onCheckIsTextEditor())
            val connection = view.onCreateInputConnection(EditorInfo())!!
            assertTrue(connection.setSelection(original.length - 1, original.length))
            val start = android.os.SystemClock.elapsedRealtime()
            assertTrue(connection.commitText("!", 1))
            android.util.Log.i("LargeDocumentTest", "2 MB IME edit took ${android.os.SystemClock.elapsedRealtime() - start} ms")
            view.scrollToPosition(0f, 500f)
            connection.closeConnection()
        }
        compose.runOnIdle {
            assertEquals(original.dropLast(1) + "!", model.state.value.value.text)
            assertTrue(model.state.value.dirty)
            model.undo()
            assertEquals(original, model.state.value.value.text)
            model.redo()
        }
        compose.onNodeWithText("Archivo").performClick()
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { !model.state.value.saving && !model.state.value.dirty }
        compose.runOnIdle { assertEquals(original.dropLast(1) + "!", disk[b.id]) }
    }
}
