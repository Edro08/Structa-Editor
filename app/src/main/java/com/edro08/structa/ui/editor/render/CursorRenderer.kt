package com.edro08.structa.ui.editor.render

import android.graphics.Canvas
import android.graphics.Paint
import com.edro08.structa.ui.editor.model.EditorLine

internal class CursorRenderer {
    private val paint = Paint()

    fun draw(canvas: Canvas, line: EditorLine, offset: Int, metrics: EditorMetrics,
        scrollX: Float, top: Float, color: Int) {
        val x = metrics.textX(line.columnAt(offset - line.startOffset), scrollX)
        paint.color = color
        canvas.drawRect(x, top + 2f, x + metrics.cursorWidth, top + metrics.lineHeight - 2f, paint)
    }
}
