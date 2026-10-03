package com.edro08.structa.ui.editor.render

import android.graphics.Canvas
import android.graphics.Paint
import com.edro08.structa.ui.editor.model.EditorLine
import kotlin.math.ceil
import kotlin.math.floor

internal class TextRenderer {
    fun draw(canvas: Canvas, line: EditorLine, paint: Paint, metrics: EditorMetrics,
        scrollX: Float, top: Float, width: Float) {
        // Clip long lines horizontally as well as limiting the vertical viewport.
        val first = floor(((scrollX - metrics.textPadding) / metrics.characterWidth).toDouble())
            .toInt().coerceIn(0, line.columnCount)
        val last = ceil(((scrollX + width - metrics.gutterWidth) / metrics.characterWidth).toDouble())
            .toInt().coerceIn(first, line.columnCount)
        if (first < last) canvas.drawText(line.textInColumns(first, last),
            metrics.textX(first, scrollX), top + metrics.baselineOffset, paint)
    }
}
