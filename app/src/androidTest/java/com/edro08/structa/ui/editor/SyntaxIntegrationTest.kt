package com.edro08.structa.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.command.*
import com.edro08.structa.domain.editor.document.EditorDocument
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange
import com.edro08.structa.domain.editor.syntax.*
import com.edro08.structa.domain.editor.decoration.*
import com.edro08.structa.ui.editor.input.EditorInputSession
import com.edro08.structa.ui.editor.model.EditorViewState
import com.edro08.structa.ui.editor.model.EditorViewport
import com.edro08.structa.ui.editor.render.EditorRenderer
import com.edro08.structa.ui.editor.render.EditorStyle
import com.edro08.structa.ui.editor.view.StructaEditorView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SyntaxIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val kotlin = LanguageRegistry.forFileName("a.kt")
    private lateinit var view: StructaEditorView
    private fun input(text: String) = EditorInputSession(EditorEngine(EditorDocument(PieceTableBuffer(text))))
    private fun launch(input: EditorInputSession, state: EditorViewState, language: Language = kotlin) {
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                StructaEditorView(context).also {
                    view = it
                    it.bind(input.engine, inputSession = input, savedViewState = state, language = language)
                }
            })
        }
    }
    private fun awaitText(text: String) {
        compose.waitUntil(5_000) {
            var ready = false
            compose.runOnIdle { ready = view.syntaxSnapshot?.text == text }
            ready
        }
    }

    @Test fun attachedViewHighlightsEditsUndoAndSwitchesIndependentDocuments() {
        val original = "val a = 1\n".repeat(100)
        val first = input(original)
        val saved = EditorViewState()
        launch(first, saved)
        awaitText(original)
        compose.runOnIdle {
            view.scrollToPosition(0f, 100f)
            first.execute(InsertTextCommand("/*"))
            assertTrue("Only absent or already updated spans may be drawn",
                view.syntaxSnapshot == null || view.syntaxSnapshot!!.text == "/*$original")
        }
        awaitText("/*$original")
        compose.runOnIdle {
            assertEquals(SyntaxStyle.COMMENT, view.syntaxSnapshot!!.lines.last { it.text.isNotEmpty() }.result.spans.single().style)
            first.execute(UndoCommand)
        }
        awaitText(original)
        compose.runOnIdle {
            assertFalse(first.engine.document.dirty)
            val second = input("{\"key\": true}")
            view.bind(second.engine, inputSession = second, savedViewState = EditorViewState(), language = LanguageRegistry.forFileName("b.json"))
        }
        awaitText("{\"key\": true}")
        compose.runOnIdle {
            assertEquals("json", view.syntaxSnapshot!!.languageId)
            view.bind(first.engine, inputSession = first, savedViewState = saved, language = kotlin)
            assertSame(saved.syntax, view.syntaxSnapshot)
            assertEquals(original, first.buffer.getText(0, first.buffer.length).toString())
            assertFalse(first.engine.document.dirty)
        }
    }

    @Test fun cancelledOldDocumentWorkerCannotPublishIntoNewDocument() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val slow = Language("slow", "Slow fixture", emptySet(), object : LanguageTokenizer {
            override fun tokenize(text: CharSequence, state: TokenizerState, checkCancelled: () -> Unit): TokenizationResult {
                started.countDown()
                try { check(release.await(5, TimeUnit.SECONDS)) } finally { finished.countDown() }
                return TokenizationResult(listOf(SyntaxSpan(0, text.length, SyntaxStyle.COMMENT)), TokenizerState())
            }
        })
        launch(input("old"), EditorViewState(), slow)
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            compose.runOnIdle {
                val next = input("val current = 1")
                view.bind(next.engine, inputSession = next, savedViewState = EditorViewState(), language = kotlin)
            }
            awaitText("val current = 1")
        } finally { release.countDown() }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        compose.runOnIdle { assertEquals("kotlin", view.syntaxSnapshot!!.languageId) }
    }

    @Test fun canvasColorsSyntaxAfterTabsAndDrawsSearchAndDiagnosticsWithoutMutatingText() {
        compose.runOnIdle {
            val content = "\tval x = 123\n    "
            val editor = input(content).engine
            val style = EditorStyle(textSizeSp = 24f)
            val renderer = EditorRenderer(1f) { it }
            val metrics = renderer.metrics(2, style)
            val viewport = EditorViewport.calculate(2, 120f, metrics.lineHeight, 0f, 0f)
            val bitmap = Bitmap.createBitmap(600, 120, Bitmap.Config.ARGB_8888)
            try {
                val decorations = DecorationSet(listOf(
                    Decoration(TextRange(TextOffset(13), TextOffset(15)), DecorationType.SEARCH_MATCH),
                    Decoration(TextRange(TextOffset(16), TextOffset(17)), DecorationType.ERROR)))
                renderer.draw(Canvas(bitmap), editor, renderer.prepare(editor.document.buffer, viewport), viewport,
                    metrics, 600f, 120f, style, false, syntax = IncrementalHighlighter.highlight(content, kotlin), decorations = decorations)
                var keywordPixels = 0
                for (y in 0 until metrics.lineHeight.toInt()) {
                    for (x in metrics.textX(4, 0f).toInt() until metrics.textX(7, 0f).toInt()) {
                        if (bitmap.getPixel(x, y) == style.keyword) keywordPixels++
                    }
                }
                assertTrue(keywordPixels > 0)
                assertEquals(style.error, bitmap.getPixel(metrics.textX(3, 0f).toInt() + 1, (metrics.lineHeight * 2 - 1).toInt()))
                assertNotEquals(style.background, bitmap.getPixel(metrics.textX(1, 0f).toInt(), (metrics.lineHeight * 1.5).toInt()))
                assertEquals(content, editor.document.buffer.getText(0, editor.document.buffer.length).toString())
                assertFalse(editor.canUndo)
            } finally { bitmap.recycle() }
        }
    }
}
