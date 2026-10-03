package com.edro08.structa.ui.editor.render

/** ARGB colors, with a monospace text size expressed in sp. */
data class EditorStyle(
    val background: Int = 0xFF101114.toInt(),
    val foreground: Int = 0xFFE6E1E5.toInt(),
    val gutterBackground: Int = 0xFF191A1E.toInt(),
    val gutterForeground: Int = 0xFF99959F.toInt(),
    val currentLine: Int = 0xFF202127.toInt(),
    val selection: Int = 0xFF414568.toInt(),
    val cursor: Int = 0xFFD0BCFF.toInt(),
    val textSizeSp: Float = 14f
) {
    init { require(textSizeSp.isFinite() && textSizeSp > 0f) }
}

data class EditorMetrics(
    val characterWidth: Float,
    val lineHeight: Float,
    val baselineOffset: Float,
    val gutterWidth: Float,
    val textPadding: Float,
    val cursorWidth: Float
) {
    fun textX(column: Int, scrollX: Float): Float =
        gutterWidth + textPadding + column * characterWidth - scrollX
}
