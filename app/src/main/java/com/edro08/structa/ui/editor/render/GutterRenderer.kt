package com.edro08.structa.ui.editor.render

import android.graphics.Canvas
import android.graphics.Paint
import com.edro08.structa.ui.editor.model.EditorLine
import com.edro08.structa.ui.editor.model.EditorViewport
import com.edro08.structa.ui.editor.model.WrappedLayout

internal class GutterRenderer {
    private val background = Paint()

    fun draw(canvas: Canvas, lines: List<EditorLine>, viewport: EditorViewport,
        metrics: EditorMetrics, height: Float, style: EditorStyle, textPaint: Paint) {
        background.color = style.gutterBackground
        canvas.drawRect(0f, 0f, metrics.gutterWidth, height, background)
        textPaint.color = style.gutterForeground
        for (line in lines) {
            val label = (line.number + 1).toString()
            canvas.drawText(label, metrics.gutterWidth - metrics.textPadding - textPaint.measureText(label),
                line.number * metrics.lineHeight - viewport.scrollY + metrics.baselineOffset, textPaint)
        }
    }

    fun drawWrapped(canvas: Canvas, layout: WrappedLayout, viewport: EditorViewport,
        metrics: EditorMetrics, height: Float, style: EditorStyle, textPaint: Paint) {
        background.color = style.gutterBackground
        canvas.drawRect(0f, 0f, metrics.gutterWidth, height, background)
        textPaint.color = style.gutterForeground
        for (row in viewport.firstVisibleLine..viewport.lastVisibleLine) {
            val number = layout.lineAt(row)
            if (row != layout.firstRow(number)) continue
            val label = (number + 1).toString()
            canvas.drawText(label, metrics.gutterWidth - metrics.textPadding - textPaint.measureText(label),
                row * metrics.lineHeight - viewport.scrollY + metrics.baselineOffset, textPaint)
        }
    }
}
