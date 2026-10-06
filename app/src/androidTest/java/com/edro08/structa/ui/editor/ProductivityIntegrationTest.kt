package com.edro08.structa.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.edro08.structa.domain.editor.syntax.SyntaxStyle
import com.edro08.structa.ui.editor.view.StructaEditorView
import com.edro08.structa.ui.editor.model.WordWrapMode
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
                 EditorScreen(state, {}, {}, model::setLanguage, model::setSearch, model::format,
                    model::undo, model::redo, model::goToLine, {}, model::showMessage, model::save,
                    model::selectDocument, model::requestClose, model::cancelClose, model::discardAndClose,
                    model::saveAndClose, model::setSearchOptions, model::setReplacement, model::findNext,
                    model::replace, quick::open, onGoToStart = model::goToStart,
                    onGoToEnd = model::goToEnd)
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

    @Test fun wordWrapMenuKeepsModePerTabAndDoesNotEditTheDocument() {
        launch()
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Ajuste de línea").performClick()
        compose.runOnIdle {
            assertEquals(WordWrapMode.VIEWPORT, model.state.value.viewState.wordWrapMode)
            assertEquals("cat\ncat CAT", model.state.value.value.text)
            assertFalse(model.state.value.dirty)
        }
        onView(isAssignableFrom(StructaEditorView::class.java)).check { raw, error ->
            if (error != null) throw error
            assertFalse((raw as StructaEditorView).isHorizontalScrollBarEnabled)
        }
        compose.runOnIdle { model.open(b) }
        compose.waitUntil(5_000) { model.state.value.entry?.id == b.id }
        compose.runOnIdle { assertEquals(WordWrapMode.OFF, model.state.value.viewState.wordWrapMode) }
        compose.runOnIdle { model.selectDocument(a.id) }
        compose.waitUntil(5_000) { model.state.value.entry?.id == a.id }
        compose.runOnIdle { assertEquals(WordWrapMode.VIEWPORT, model.state.value.viewState.wordWrapMode) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Ajuste de línea").performClick()
        compose.runOnIdle {
            assertEquals(WordWrapMode.OFF, model.state.value.viewState.wordWrapMode)
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
        compose.onNodeWithText("Ir a línea...").performClick()
         compose.onNodeWithText("Número de línea").performTextInput("2")
         compose.onNodeWithText("Número de línea").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(TextRange(4), model.state.value.value.selection) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Lenguaje", substring = false).performClick()
        compose.onNodeWithText("Lenguaje del archivo").assertExists()
         compose.onNodeWithText("Texto sin formato").assertExists()
        compose.onNodeWithText("JSON", substring = false).assertExists()
        compose.onNodeWithText("YAML").performClick()
        compose.runOnIdle { assertEquals(FileMode.YAML, model.state.value.mode) }
        compose.runOnIdle { assertFalse(model.state.value.dirty) }
    }

    @Test fun editMenuGoToStartAndEndRevealsCursorWithoutEditing() {
        launch()
        val text = "long line\n".repeat(200)
        compose.runOnIdle { model.edit(TextFieldValue(text)) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Ir al final").performClick()
        compose.waitUntil(5_000) { model.state.value.viewState.scrollY > 0f }
        compose.runOnIdle { assertEquals(TextRange(text.length), model.state.value.value.selection) }

        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Ir al inicio").performClick()
        compose.waitUntil(5_000) { model.state.value.viewState.scrollY == 0f }
        compose.runOnIdle {
            assertEquals(TextRange(0), model.state.value.value.selection)
            assertEquals(text, model.state.value.value.text)
        }
    }

    @Test fun selectingJsonLanguageHighlightsKeysAndSwitchingToTextClearsSpans() {
        launch()
        val json = "{\"name\": true, \"count\": 4"
        compose.runOnIdle { model.edit(TextFieldValue(json)) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Lenguaje", substring = false).performClick()
        compose.onNodeWithText("JSON", substring = false).performClick()
        compose.waitUntil(5_000) { model.state.value.viewState.syntax?.languageId == "json" }
        compose.runOnIdle {
            val snapshot = model.state.value.viewState.syntax!!
            assertTrue(snapshot.lines.single().result.spans.any { it.style == SyntaxStyle.KEY })
            assertTrue(snapshot.lines.single().result.spans.any { it.style == SyntaxStyle.NUMBER })
            assertEquals(json, model.state.value.value.text)
        }
        onView(isAssignableFrom(StructaEditorView::class.java)).check { raw, error ->
            if (error != null) throw error
            val view = raw as StructaEditorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val hasColoredKey = (0 until minOf(view.height, 160)).any { y ->
                    (0 until view.width).any { x -> bitmap.getPixel(x, y) == view.style.key }
                }
                assertTrue("La clave JSON debe pintarse con el color de sintaxis", hasColoredKey)
            } finally { bitmap.recycle() }
        }

        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Lenguaje", substring = false).performClick()
         compose.onNodeWithText("Texto sin formato").performClick()
        compose.waitUntil(5_000) { model.state.value.viewState.syntax == null }
        compose.runOnIdle {
            assertEquals("text", model.state.value.highlightLanguage().id)
            assertEquals(json, model.state.value.value.text)
        }
    }

    @Test fun languageDialogSelectsXmlAndGoForSyntaxWithoutChangingDocument() {
        launch()
        val xml = "<item name=\"x\">value</item>"
        compose.runOnIdle { model.edit(TextFieldValue(xml)) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Lenguaje", substring = false).performClick()
        compose.onNodeWithText("Go").assertExists()
        compose.onNodeWithText("Markdown").assertExists()
        compose.onNodeWithText("XML").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.state.value.viewState.syntax?.languageId == "xml" }
        compose.runOnIdle {
            assertEquals(FileMode.TEXT, model.state.value.mode)
            assertEquals(xml, model.state.value.value.text)
            assertTrue(model.state.value.viewState.syntax!!.lines[0].result.spans.any { it.style == SyntaxStyle.KEY })
        }
        val go = "var name = `hello`"
        compose.runOnIdle { model.edit(TextFieldValue(go)) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Lenguaje", substring = false).performClick()
        compose.onNodeWithText("Go").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.state.value.viewState.syntax?.languageId == "go" }
        compose.runOnIdle {
            assertEquals(FileMode.TEXT, model.state.value.mode)
            assertEquals(go, model.state.value.value.text)
            assertTrue(model.state.value.viewState.syntax!!.lines[0].result.spans.any { it.style == SyntaxStyle.KEYWORD })
            assertTrue(model.state.value.viewState.syntax!!.lines[0].result.spans.any { it.style == SyntaxStyle.STRING })
        }
    }

    @Test fun formatMenuFormatsYamlButDisablesUnsupportedLanguages() {
        launch()
        val original = "root:\n    title: 'keep' # note\n    items:\n        - one"
        compose.runOnIdle { model.edit(TextFieldValue(original)) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Lenguaje", substring = false).performClick()
        compose.onNodeWithText("YAML", substring = false).performClick()
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Formatear", substring = false).assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            model.state.value.value.text == "root:\n  title: 'keep' # note\n  items:\n    - one"
        }
        compose.runOnIdle { model.undo(); assertEquals(original, model.state.value.value.text) }
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Lenguaje", substring = false).performClick()
        compose.onNodeWithText("Go").performClick()
        compose.onNodeWithContentDescription("Menú Editor").performClick()
        compose.onNodeWithText("Formatear", substring = false).assertIsNotEnabled()
    }
}
