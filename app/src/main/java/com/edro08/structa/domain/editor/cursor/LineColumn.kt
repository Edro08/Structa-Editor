package com.edro08.structa.domain.editor.cursor

/** Zero-based logical line and UTF-16 column, not a visual or grapheme position. */
data class LineColumn(val line: Int, val column: Int) {
    init {
        require(line >= 0) { "Line must be nonnegative: $line" }
        require(column >= 0) { "Column must be nonnegative: $column" }
    }
}
