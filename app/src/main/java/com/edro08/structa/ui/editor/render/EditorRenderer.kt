package com.edro08.structa.ui.editor.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.buffer.TextBuffer
import com.edro08.structa.domain.editor.cursor.TextRange
import com.edro08.structa.ui.editor.model.EditorLine
import com.edro08.structa.ui.editor.model.EditorViewport
import com.edro08.structa.ui.editor.model.WrappedLayout
import com.edro08.structa.domain.editor.syntax.SyntaxSnapshot
import com.edro08.structa.domain.editor.syntax.SyntaxWindow
import com.edro08.structa.domain.editor.decoration.*

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
        textPaint.typeface = Typeface.create(style.font.family, Typeface.NORMAL)
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
        composing: TextRange? = null, syntax: SyntaxSnapshot? = null, decorations: DecorationSet = DecorationSet(),
        syntaxWindow: SyntaxWindow? = null) {
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
            for (decoration in decorations.intersecting(line.startOffset, line.startOffset + line.length + 1)) {
                val columns = line.selectionColumns(decoration.range.start.value, decoration.range.end.value,
                    line.number < engine.document.buffer.lineCount - 1)
                val from = columns?.first ?: line.columnAt(decoration.range.start.value - line.startOffset)
                val to = columns?.let { it.last + 1 } ?: from + 1
                val underline = decoration.type == DecorationType.ERROR || decoration.type == DecorationType.WARNING
                backgroundPaint.color = when (decoration.type) {
                    DecorationType.SEARCH_MATCH -> style.searchMatch
                    DecorationType.SELECTED_OCCURRENCE -> style.selectedOccurrence
                    DecorationType.ERROR -> style.error
                    DecorationType.WARNING -> style.warning
                    DecorationType.BRACKET_MATCH -> style.selection
                    else -> continue // Reserved layers have no producers yet.
                }
                canvas.drawRect(metrics.textX(from, viewport.scrollX),
                    if (underline) top + metrics.lineHeight - 2 * density else top,
                    metrics.textX(to, viewport.scrollX), top + metrics.lineHeight, backgroundPaint)
            }
            selection.draw(canvas, line, engine.selection, line.number < engine.document.buffer.lineCount - 1,
                metrics, viewport.scrollX, top, style.selection)
        }
        textPaint.color = style.foreground
        for (line in lines) {
            val top = line.number * metrics.lineHeight - viewport.scrollY
            text.draw(canvas, line, textPaint, metrics, viewport.scrollX, top, width,
                syntaxWindow?.spansAt(line.number) ?: syntax?.lines?.getOrNull(line.number)?.result?.spans.orEmpty(), style)
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

    fun drawWrapped(canvas: Canvas, engine: EditorEngine, lines: List<EditorLine>, viewport: EditorViewport,
        layout: WrappedLayout, metrics: EditorMetrics, width: Float, height: Float, style: EditorStyle,
        showCursor: Boolean, composing: TextRange?, syntax: SyntaxSnapshot?, decorations: DecorationSet,
        syntaxWindow: SyntaxWindow?) {
        canvas.drawColor(style.background)
        val byNumber = lines.associateBy { it.number }
        val cursorOffset = engine.cursor.offset.value
        val cursorLine = engine.document.buffer.getLineForOffset(cursorOffset)
        val saved = canvas.save()
        canvas.clipRect(metrics.gutterWidth, 0f, width, height)
        for (row in viewport.firstVisibleLine..viewport.lastVisibleLine) {
            val number = layout.lineAt(row)
            val line = byNumber[number] ?: continue
            val start = layout.segmentStart(row, number)
            val end = minOf(start + layout.columns, line.columnCount)
            val scrollX = start * metrics.characterWidth
            val top = row * metrics.lineHeight - viewport.scrollY
            if (cursorLine == number && layout.rowFor(number, line.columnAt(cursorOffset - line.startOffset)) == row) {
                backgroundPaint.color = style.currentLine
                canvas.drawRect(metrics.gutterWidth, top, width, top + metrics.lineHeight, backgroundPaint)
            }
            fun range(from: Int, to: Int, color: Int, underline: Boolean = false) {
                val left = maxOf(from, start)
                val right = minOf(to, start + layout.columns)
                if (right <= left) return
                backgroundPaint.color = color
                canvas.drawRect(metrics.textX(left, scrollX),
                    if (underline) top + metrics.lineHeight - 2 * density else top,
                    metrics.textX(right, scrollX), top + metrics.lineHeight, backgroundPaint)
            }
            for (decoration in decorations.intersecting(line.startOffset, line.startOffset + line.length + 1)) {
                val columns = line.selectionColumns(decoration.range.start.value, decoration.range.end.value,
                    number < engine.document.buffer.lineCount - 1)
                val from = columns?.first ?: line.columnAt(decoration.range.start.value - line.startOffset)
                val to = columns?.let { it.last + 1 } ?: from + 1
                val color = when (decoration.type) {
                    DecorationType.SEARCH_MATCH -> style.searchMatch
                    DecorationType.SELECTED_OCCURRENCE -> style.selectedOccurrence
                    DecorationType.ERROR -> style.error
                    DecorationType.WARNING -> style.warning
                    DecorationType.BRACKET_MATCH -> style.selection
                    else -> continue
                }
                range(from, to, color, decoration.type == DecorationType.ERROR || decoration.type == DecorationType.WARNING)
            }
            line.selectionColumns(engine.selection?.start?.value ?: -1, engine.selection?.end?.value ?: -1,
                number < engine.document.buffer.lineCount - 1)?.let { range(it.first, it.last + 1, style.selection) }
            text.draw(canvas, line, textPaint, metrics, scrollX, top, width,
                syntaxWindow?.spansAt(number) ?: syntax?.lines?.getOrNull(number)?.result?.spans.orEmpty(),
                style, start, end)
            composing?.let {
                line.selectionColumns(it.start.value, it.end.value, number < engine.document.buffer.lineCount - 1)
                    ?.let { columns -> range(columns.first, columns.last + 1, style.cursor, true) }
            }
            if (showCursor && cursorLine == number && layout.rowFor(number,
                    line.columnAt(cursorOffset - line.startOffset)) == row) {
                cursor.draw(canvas, line, cursorOffset, metrics, scrollX, top, style.cursor)
            }
        }
        canvas.restoreToCount(saved)
        gutter.drawWrapped(canvas, layout, viewport, metrics, height, style, textPaint)
    }
}
