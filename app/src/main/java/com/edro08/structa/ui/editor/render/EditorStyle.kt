package com.edro08.structa.ui.editor.render

import com.edro08.structa.domain.settings.EditorFont

/** ARGB colors, with a monospace text size expressed in sp. */
data class EditorStyle(
    val background: Int = 0xFF101114.toInt(),
    val foreground: Int = 0xFFE6E1E5.toInt(),
    val gutterBackground: Int = 0xFF191A1E.toInt(),
    val gutterForeground: Int = 0xFF99959F.toInt(),
    val currentLine: Int = 0xFF202127.toInt(),
    val selection: Int = 0xFF414568.toInt(),
    val cursor: Int = 0xFFD0BCFF.toInt(),
    val textSizeSp: Float = 14f,
    val font: EditorFont = EditorFont.MONOSPACE,
    val keyword: Int = 0xFFC792EA.toInt(),
    val string: Int = 0xFFC3E88D.toInt(),
    val number: Int = 0xFFF78C6C.toInt(),
    val comment: Int = 0xFF9CA7B0.toInt(),
    val operator: Int = 0xFF89DDFF.toInt(),
    val key: Int = 0xFFFFCB6B.toInt(),
    val heading: Int = 0xFF82AAFF.toInt(),
    val searchMatch: Int = 0x665E9DFF,
    val selectedOccurrence: Int = 0x999B7A00.toInt(),
    val error: Int = 0xFFFF5370.toInt(),
    val warning: Int = 0xFFFFCB6B.toInt()
) {
    init { require(textSizeSp.isFinite() && textSizeSp > 0f) }
    fun syntaxColor(style: com.edro08.structa.domain.editor.syntax.SyntaxStyle): Int = when (style) {
        com.edro08.structa.domain.editor.syntax.SyntaxStyle.KEYWORD -> keyword
        com.edro08.structa.domain.editor.syntax.SyntaxStyle.STRING -> string
        com.edro08.structa.domain.editor.syntax.SyntaxStyle.NUMBER -> number
        com.edro08.structa.domain.editor.syntax.SyntaxStyle.COMMENT -> comment
        com.edro08.structa.domain.editor.syntax.SyntaxStyle.OPERATOR -> operator
        com.edro08.structa.domain.editor.syntax.SyntaxStyle.KEY -> key
        com.edro08.structa.domain.editor.syntax.SyntaxStyle.HEADING -> heading
    }
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
