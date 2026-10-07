package com.edro08.structa.ui.editor.model

import kotlin.math.ceil
import kotlin.math.floor

/** Pixel scroll positions belong to the view, not to EditorDocument. */
data class EditorViewport(
    val firstVisibleLine: Int,
    val lastVisibleLine: Int,
    val scrollX: Float,
    val scrollY: Float
) {
    companion object {
        fun calculate(lineCount: Int, height: Float, lineHeight: Float,
            scrollX: Float, scrollY: Float, overscan: Int = 2): EditorViewport {
            require(lineCount > 0 && height.isFinite() && height >= 0)
            require(lineHeight.isFinite() && lineHeight > 0 && overscan >= 0)
            require(scrollX.isFinite() && scrollY.isFinite())
            val y = scrollY.coerceIn(0f, maxScrollY(lineCount, height, lineHeight))
            if (height == 0f) return EditorViewport(0, -1, scrollX.coerceAtLeast(0f), y)
            val first = (floor(y / lineHeight).toInt() - overscan).coerceAtLeast(0)
            val last = (ceil((y + height).toDouble() / lineHeight).toLong() - 1 + overscan)
                .coerceIn(0, (lineCount - 1).toLong()).toInt()
            return EditorViewport(first, last, scrollX.coerceAtLeast(0f), y)
        }

        fun maxScrollY(lineCount: Int, height: Float, lineHeight: Float): Float =
            (lineCount.toFloat() * lineHeight - height).coerceAtLeast(0f)
    }
}
