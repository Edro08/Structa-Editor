package com.edro08.structa.ui.editor.render

import android.graphics.Canvas
import android.graphics.Paint
import com.edro08.structa.ui.editor.model.EditorLine
import com.edro08.structa.domain.editor.syntax.SyntaxSpan
import kotlin.math.ceil
import kotlin.math.floor

internal class TextRenderer {
    fun draw(canvas: Canvas, line: EditorLine, paint: Paint, metrics: EditorMetrics,
        scrollX: Float, top: Float, width: Float, spans: List<SyntaxSpan> = emptyList(), style: EditorStyle = EditorStyle(),
        columnStart: Int = 0, columnEnd: Int = line.columnCount) {
        // Clip long lines horizontally as well as limiting the vertical viewport.
        val first = floor(((scrollX - metrics.textPadding) / metrics.characterWidth).toDouble())
            .toInt().coerceIn(columnStart, columnEnd.coerceAtLeast(columnStart))
        val last = ceil(((scrollX + width - metrics.gutterWidth) / metrics.characterWidth).toDouble())
            .toInt().coerceIn(first, columnEnd.coerceAtLeast(first))
        fun draw(from: Int, to: Int, color: Int) {
            val start = maxOf(first, from)
            val end = minOf(last, to)
            if (start < end) {
                paint.color = color
                canvas.drawText(line.textInColumns(start, end), metrics.textX(start, scrollX), top + metrics.baselineOffset, paint)
            }
        }
        var column = 0
        for (span in spans) {
            val start = line.columnAt(span.start)
            val end = line.columnAt(span.end)
            draw(column, start, style.foreground)
            draw(start, end, style.syntaxColor(span.style))
            column = end
            if (column >= last) break
        }
        draw(column, line.columnCount, style.foreground)
        paint.color = style.foreground
    }
}
