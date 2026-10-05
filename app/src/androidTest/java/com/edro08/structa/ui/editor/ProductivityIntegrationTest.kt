package com.edro08.structa.ui.editor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import com.edro08.structa.application.browser.ListDirectory
import com.edro08.structa.application.document.*
import com.edro08.structa.application.editor.*
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.*
import com.edro08.structa.ui.screen.editor.*
import com.edro08.structa.ui.theme.StructaTheme
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ProductivityIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val a = FileEntry(DocumentId("a"), "a.txt", 11, false)
    private val b = FileEntry(DocumentId("b"), "MainActivity.kt", 3, false)
    private lateinit var model: EditorViewModel
    private lateinit var quick: QuickOpenViewModel

    private fun launch() {
        compose.runOnIdle {
            model = EditorViewModel(OpenDocument(object : FileReader {
                override suspend fun read(file: FileEntry) = if (file.id == a.id) "cat\ncat CAT" else "two"
            }, ContentFormatDetector()), FormatJsonDocument(), SaveDocumentCopy(object : FileWriter {
                override suspend fun write(destination: DocumentId, content: String) = Unit
            }))
            quick = QuickOpenViewModel(ListDirectory(object : DirectoryReader {
                override suspend fun list(directory: DocumentId) = listOf(a, b)
            }))
            quick.setRoot(DocumentId("root"))
            model.open(a)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            val quickState by quick.state.collectAsState()
            StructaTheme {
                EditorScreen(state, {}, {}, model::setMode, model::setSearch, model::format,
                    model::undo, model::redo, model::goToLine, {}, model::showMessage, model::save,
                    model::selectDocument, model::requestClose, model::cancelClose, model::discardAndClose,
                    model::saveAndClose, model::setSearchOptions, model::setReplacement, model::findNext,
                    model::replace, quick::open)
                if (quickState.visible) ProductivityPicker("Abrir", quickState.query, quick::setQuery,
                    quickState.files.map { it.entry.name to it.relativePath }, quickState.message, quickState.indexing,
                    onChoose = { model.open(quickState.files[it].entry); quick.close() }, onDismiss = quick::close)
            }
        }
        compose.waitUntil(5_000) { model.state.value.entry != null && !model.state.value.loading }
    }

    @After fun stop() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel(); quick.viewModelScope.cancel() }
    }

    @Test fun searchReplaceAllAndEditMenuUndoUseTheSameDocumentHistory() {
        launch()
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Reemplazar", substring = false).performClick()
        assertTrue("Reemplazar todo debe verse sin desplazamiento horizontal",
            compose.onNodeWithText("Reemplazar todo").fetchSemanticsNode().boundsInWindow.right <=
                compose.onRoot().fetchSemanticsNode().boundsInWindow.right)
        compose.onNodeWithText("Buscar dentro del archivo").performTextInput("cat")
        compose.waitUntil(5_000) { !model.state.value.searching && model.state.value.occurrences == 3 }
        compose.onNodeWithText("Reemplazar por...").performTextInput("dog")
        compose.onNodeWithText("Reemplazar todo").performClick()
        compose.waitUntil(5_000) { model.state.value.value.text == "dog\ndog dog" }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Deshacer").performClick()
        compose.runOnIdle {
            assertEquals("cat\ncat CAT", model.state.value.value.text)
            assertFalse(model.state.value.dirty)
        }
    }

    @Test fun quickOpenShortcutFuzzyQueryAndEnterOpenAndReuseTabs() {
        launch()
        compose.runOnIdle { model.edit(TextFieldValue("unsaved")) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Buscar", substring = false).performClick()
        compose.onNodeWithText("Buscar dentro del archivo").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.P); keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithText("Filtrar").performTextInput("mact")
        compose.waitUntil(5_000) { quick.state.value.files.singleOrNull()?.entry?.id == b.id }
        compose.onNodeWithText("Filtrar").performKeyInput { pressKey(Key.Enter) }
        compose.waitUntil(5_000) { model.state.value.entry?.id == b.id }
        compose.onNodeWithText("Archivo").performClick()
        compose.onNodeWithText("Abrir").performClick()
        compose.onNodeWithText("Filtrar").performTextInput("a.txt")
        compose.waitUntil(5_000) { quick.state.value.files.singleOrNull()?.entry?.id == a.id }
        compose.onNodeWithText("Filtrar").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle {
            assertEquals("unsaved", model.state.value.value.text)
            assertEquals(2, model.state.value.tabs.size)
        }
    }

    @Test fun editMenuShortcutGoToLineAndFormatSelectionWork() {
        launch()
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Buscar", substring = false).performClick()
        compose.onNodeWithText("Buscar dentro del archivo").performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.ShiftLeft); pressKey(Key.P); keyUp(Key.ShiftLeft); keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithText("Ir a línea").performClick()
         compose.onNodeWithText("Número de línea").performTextInput("2")
         compose.onNodeWithText("Número de línea").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(TextRange(4), model.state.value.value.selection) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Lenguaje", substring = false).performClick()
        compose.onNodeWithText("Lenguaje del archivo").assertExists()
        compose.onNodeWithText("Texto").assertExists()
        compose.onNodeWithText("JSON", substring = false).assertExists()
        compose.onNodeWithText("YAML").performClick()
        compose.runOnIdle { assertEquals(FileMode.YAML, model.state.value.mode) }
        compose.runOnIdle { assertFalse(model.state.value.dirty) }
    }
}
