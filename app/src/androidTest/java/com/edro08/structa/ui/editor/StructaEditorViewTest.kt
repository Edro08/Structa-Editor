package com.edro08.structa.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.buffer.TextBuffer
import com.edro08.structa.domain.editor.command.InsertTextCommand
import com.edro08.structa.domain.editor.command.SetSelectionCommand
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.document.EditorDocument
import com.edro08.structa.ui.editor.render.EditorRenderer
import com.edro08.structa.ui.editor.render.EditorStyle
import com.edro08.structa.ui.editor.view.StructaEditorView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StructaEditorViewTest {
    @Test
    fun scrollingReadsOnlyViewportAndNeverMutatesBuffer() = onMain {
        val original = PieceTableBuffer("line\n".repeat(99_999) + "end")
        val reads = mutableListOf<Int>()
        val buffer = object : TextBuffer by original {
            override fun getLine(line: Int): CharSequence { reads.add(line); return original.getLine(line) }
            override fun replace(start: Int, end: Int, text: CharSequence) = error("Renderer must be read-only")
            override fun insert(offset: Int, text: CharSequence) = error("Renderer must be read-only")
            override fun delete(start: Int, end: Int) = error("Renderer must be read-only")
        }
        val editor = EditorEngine(EditorDocument(buffer))
        val view = view(editor)
        draw(view)
        assertTrue(reads.size in 1..40)
        reads.clear()
        draw(view)
        assertTrue("Unchanged frames reuse line layouts", reads.isEmpty())
        view.scrollToPosition(0f, 50_000f)
        draw(view)
        assertTrue(reads.size in 1..40)
        assertTrue(view.viewport.firstVisibleLine > 100)
        assertTrue(reads.all { it in view.viewport.firstVisibleLine..view.viewport.lastVisibleLine })
        assertEquals(100_000, buffer.lineCount)
        assertEquals(TextOffset(0), editor.cursor.offset)
        assertFalse(editor.canUndo)
    }

    @Test
    fun canvasDrawsSelectionCursorAndFixedGutter() = onMain {
        val editor = EditorEngine(EditorDocument(PieceTableBuffer("    \n\ncode")))
        editor.execute(SetSelectionCommand(TextOffset(1), TextOffset(3)))
        val style = EditorStyle()
        val renderer = EditorRenderer(1f) { it }
        val metrics = renderer.metrics(3, style)
        val viewport = com.edro08.structa.ui.editor.model.EditorViewport.calculate(3, 100f,
            metrics.lineHeight, 0f, 0f)
        val bitmap = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888)
        try {
            renderer.draw(Canvas(bitmap), editor, renderer.prepare(editor.document.buffer, viewport), viewport,
                metrics, 400f, 100f, style, true)
            val y = (metrics.lineHeight / 2).toInt()
            assertEquals(style.gutterBackground, bitmap.getPixel(1, y))
            assertEquals(style.selection, bitmap.getPixel(metrics.textX(2, 0f).toInt(), y))
            assertEquals(style.cursor, bitmap.getPixel(metrics.textX(3, 0f).toInt() + 1, y))
            assertEquals(style.background, bitmap.getPixel(390, 90))
            var textPixels = 0
            for (row in (metrics.lineHeight * 2).toInt() until (metrics.lineHeight * 3).toInt()) {
                for (column in metrics.gutterWidth.toInt() until 150) {
                    if (bitmap.getPixel(column, row) == style.foreground) textPixels++
                }
            }
            assertTrue("Canvas must draw actual text glyphs", textPixels > 0)
        } finally { bitmap.recycle() }
    }

    @Test
    fun refreshAfterCommandsUpdatesLayoutAndClampsScroll() = onMain {
        val editor = EditorEngine(EditorDocument(PieceTableBuffer("long ".repeat(100) + "\nline\n")))
        val view = view(editor)
        draw(view)
        view.scrollToPosition(500f, 0f)
        assertTrue(view.viewport.scrollX > 0f)
        editor.execute(SetSelectionCommand(TextOffset(0), TextOffset(editor.document.buffer.length)))
        editor.execute(InsertTextCommand("short"))
        view.bind(editor, version = 1L)
        draw(view)
        assertEquals(0f, view.viewport.scrollX, 0f)
        assertEquals(0f, view.viewport.scrollY, 0f)
        assertEquals(0, view.viewport.lastVisibleLine)
        assertTrue(editor.canUndo)
    }

    @Test
    fun rebindingSameEngineKeepsScrollAndDifferentDocumentResetsIt() = onMain {
        val editor = EditorEngine(EditorDocument(PieceTableBuffer("x\n".repeat(1000))))
        val view = view(editor)
        draw(view)
        view.scrollToPosition(0f, 400f)
        view.bind(editor)
        draw(view)
        assertEquals(400f, view.viewport.scrollY, 0f)
        view.bind(EditorEngine(EditorDocument(PieceTableBuffer("other"))))
        draw(view)
        assertEquals(0f, view.viewport.scrollY, 0f)
    }

    @Test
    fun dragScrollsBothAxesWithoutMovingCursor() = onMain {
        val editor = EditorEngine(EditorDocument(PieceTableBuffer(("x".repeat(200) + "\n").repeat(100))))
        val view = view(editor)
        draw(view)
        val time = SystemClock.uptimeMillis()
        fun send(action: Int, elapsed: Long, x: Float, y: Float) {
            val event = MotionEvent.obtain(time, time + elapsed, action, x, y, 0)
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 0, 400f, 300f)
        send(MotionEvent.ACTION_MOVE, 32, 150f, 80f)
        send(MotionEvent.ACTION_UP, 64, 150f, 80f)
        draw(view)
        assertTrue(view.viewport.scrollX > 0f)
        assertTrue(view.viewport.scrollY > 0f)
        assertEquals(TextOffset(0), editor.cursor.offset)
        assertFalse(editor.canUndo)
    }

    private fun view(editor: EditorEngine): StructaEditorView =
        StructaEditorView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            bind(editor)
            layout(0, 0, 600, 400)
        }

    private fun draw(view: StructaEditorView) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try { view.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
