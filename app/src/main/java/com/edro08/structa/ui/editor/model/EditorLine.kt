package com.edro08.structa.ui.editor.model

/**
 * Fixed-cell layout for one logical line. Offsets remain UTF-16; tabs use tab stops.
 * CR occupies a blank cell, matching the core's LF-only line-ending contract.
 * Advanced grapheme/bidi shaping is outside this first monospace renderer.
 */
class EditorLine(val number: Int, val startOffset: Int, private val text: String, private val tabSize: Int = 4) {
    val length: Int = text.length
    // Sparse checkpoints avoid a per-character array or expanding a huge tab-filled line.
    private val checkpoints = IntArray(text.length / CHECKPOINT_SIZE + 1)
    val columnCount: Int

    init {
        require(tabSize > 0)
        var column = 0
        text.forEachIndexed { index, char ->
            if (index % CHECKPOINT_SIZE == 0) checkpoints[index / CHECKPOINT_SIZE] = column
            column += cellWidth(char, column)
        }
        if (text.length % CHECKPOINT_SIZE == 0) checkpoints[text.length / CHECKPOINT_SIZE] = column
        columnCount = column
    }

    fun columnAt(offsetInLine: Int): Int {
        val offset = offsetInLine.coerceIn(0, length)
        val checkpoint = offset / CHECKPOINT_SIZE
        var column = checkpoints[checkpoint]
        for (index in checkpoint * CHECKPOINT_SIZE until offset) column += cellWidth(text[index], column)
        return column
    }

    /** Nearest UTF-16 insertion offset, including the midpoint of a tab's cells. */
    fun offsetAtColumn(column: Float): Int {
        val target = column.coerceIn(0f, columnCount.toFloat())
        var low = 0
        var high = length
        while (low < high) {
            val middle = low + (high - low) / 2
            if (columnAt(middle) < target) low = middle + 1 else high = middle
        }
        if (low == 0) return 0
        val previous = columnAt(low - 1)
        return if (target - previous < columnAt(low) - target) low - 1 else low
    }

    /** Expands only the requested horizontal slice, including partial tabs at its edges. */
    fun textInColumns(start: Int, end: Int): String {
        require(start >= 0 && end >= start && end <= columnCount)
        if (start == end) return ""
        var low = 0
        var high = checkpoints.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (checkpoints[middle] <= start) low = middle + 1 else high = middle
        }
        val checkpoint = (low - 1).coerceAtLeast(0)
        var index = checkpoint * CHECKPOINT_SIZE
        var column = checkpoints[checkpoint]
        return buildString(end - start) {
            while (index < text.length && column < end) {
                val char = text[index++]
                val next = column + cellWidth(char, column)
                if (next > start) {
                    if (char == '\t' || char == '\r') {
                        repeat(minOf(next, end) - maxOf(column, start)) { append(' ') }
                    } else append(char)
                }
                column = next
            }
        }
    }

    private fun cellWidth(char: Char, column: Int): Int = if (char == '\t') tabSize - column % tabSize else 1

    /** Half-open display columns; selection of LF adds one visible cell. */
    fun selectionColumns(start: Int, end: Int, hasLineBreak: Boolean): IntRange? {
        val lineEnd = startOffset + length
        if (start >= end || end <= startOffset || start > lineEnd ||
            (start == lineEnd && !hasLineBreak)) return null
        val from = columnAt((start - startOffset).coerceAtLeast(0))
        val to = if (end > lineEnd && hasLineBreak) columnCount + 1
            else columnAt((end - startOffset).coerceAtLeast(0))
        return if (to > from) from until to else null
    }

    private companion object { const val CHECKPOINT_SIZE = 256 }
}
