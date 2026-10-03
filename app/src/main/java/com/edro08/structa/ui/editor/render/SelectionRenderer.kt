package com.edro08.structa.ui.editor.render

import android.graphics.Canvas
import android.graphics.Paint
import com.edro08.structa.domain.editor.cursor.Selection
import com.edro08.structa.ui.editor.model.EditorLine

internal class SelectionRenderer {
    private val paint = Paint()

    fun draw(canvas: Canvas, line: EditorLine, selection: Selection?, hasLineBreak: Boolean,
        metrics: EditorMetrics, scrollX: Float, top: Float, color: Int) {
        if (selection == null) return
        val columns = line.selectionColumns(selection.start.value, selection.end.value, hasLineBreak) ?: return
        paint.color = color
        canvas.drawRect(metrics.textX(columns.first, scrollX), top,
            metrics.textX(columns.last + 1, scrollX), top + metrics.lineHeight, paint)
    }
}
