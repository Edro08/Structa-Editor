package com.edro08.structa.ui.editor.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.buffer.TextBuffer
import com.edro08.structa.domain.editor.cursor.TextRange
import com.edro08.structa.ui.editor.model.EditorLine
import com.edro08.structa.ui.editor.model.EditorViewport

/** Read-only renderer; retains layouts only for the viewport plus two lines of margin. */
class EditorRenderer(private val density: Float, private val textSizeToPixels: (Float) -> Float) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val backgroundPaint = Paint()
    private val text = TextRenderer()
    private val cursor = CursorRenderer()
    private val selection = SelectionRenderer()
    private val gutter = GutterRenderer()
    private val lineCache = mutableMapOf<Int, EditorLine>()
    private var widestColumns = 0

    fun invalidateContent() {
        lineCache.clear()
        widestColumns = 0
    }

    fun metrics(lineCount: Int, style: EditorStyle): EditorMetrics {
        textPaint.textSize = textSizeToPixels(style.textSizeSp)
        val font = textPaint.fontMetrics
        val characterWidth = textPaint.measureText("M").coerceAtLeast(1f)
        val lineHeight = (font.descent - font.ascent) * 1.2f
        val padding = 8f * density
        return EditorMetrics(characterWidth, lineHeight,
            -font.ascent + (lineHeight - (font.descent - font.ascent)) / 2f,
            maxOf(2, lineCount.toString().length) * characterWidth + padding * 2,
            padding, (2f * density).coerceAtLeast(1f))
    }

    fun prepare(buffer: TextBuffer, viewport: EditorViewport): List<EditorLine> {
        val range = viewport.firstVisibleLine..viewport.lastVisibleLine
        lineCache.keys.retainAll(range.toSet())
        return range.map { number ->
            lineCache.getOrPut(number) {
                EditorLine(number, buffer.getLineStart(number), buffer.getLine(number).toString())
            }.also { widestColumns = maxOf(widestColumns, it.columnCount) }
        }
    }

    /** Width grows as lines are visited; opening a document never scans every line. */
    fun maxScrollX(width: Float, metrics: EditorMetrics): Float =
        (widestColumns * metrics.characterWidth + metrics.textPadding * 2 + metrics.cursorWidth -
            (width - metrics.gutterWidth).coerceAtLeast(0f)).coerceAtLeast(0f)

    fun draw(canvas: Canvas, engine: EditorEngine, lines: List<EditorLine>, viewport: EditorViewport,
        metrics: EditorMetrics, width: Float, height: Float, style: EditorStyle, showCursor: Boolean,
        composing: TextRange? = null) {
        canvas.drawColor(style.background)
        val currentLine = engine.document.buffer.getLineForOffset(engine.cursor.offset.value)
        val saved = canvas.save()
        canvas.clipRect(metrics.gutterWidth, 0f, width, height)
        backgroundPaint.color = style.currentLine
        if (currentLine in viewport.firstVisibleLine..viewport.lastVisibleLine) {
            val top = currentLine * metrics.lineHeight - viewport.scrollY
            canvas.drawRect(metrics.gutterWidth, top, width, top + metrics.lineHeight, backgroundPaint)
        }
        for (line in lines) {
            val top = line.number * metrics.lineHeight - viewport.scrollY
            selection.draw(canvas, line, engine.selection, line.number < engine.document.buffer.lineCount - 1,
                metrics, viewport.scrollX, top, style.selection)
        }
        textPaint.color = style.foreground
        for (line in lines) {
            val top = line.number * metrics.lineHeight - viewport.scrollY
            text.draw(canvas, line, textPaint, metrics, viewport.scrollX, top, width)
            if (composing != null) {
                val columns = line.selectionColumns(composing.start.value, composing.end.value,
                    line.number < engine.document.buffer.lineCount - 1)
                if (columns != null) {
                    backgroundPaint.color = style.cursor
                    canvas.drawRect(metrics.textX(columns.first, viewport.scrollX), top + metrics.lineHeight - density * 2,
                        metrics.textX(columns.last + 1, viewport.scrollX), top + metrics.lineHeight - density, backgroundPaint)
                }
            }
        }
        if (showCursor) lines.firstOrNull { it.number == currentLine }?.let { line ->
            cursor.draw(canvas, line, engine.cursor.offset.value, metrics, viewport.scrollX,
                line.number * metrics.lineHeight - viewport.scrollY, style.cursor)
        }
        canvas.restoreToCount(saved)
        gutter.draw(canvas, lines, viewport, metrics, height, style, textPaint)
    }
}
