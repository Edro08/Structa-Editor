package com.edro08.structa.ui.editor.model

import com.edro08.structa.domain.editor.buffer.TextBuffer

/** Visual rows only: every offset and every line number still refers to the original buffer. */
class WrappedLayout(buffer: TextBuffer, val columns: Int) {
    init { require(columns > 0) }

    private val starts = IntArray(buffer.lineCount + 1)

    init {
        for (line in 0 until buffer.lineCount) {
            var width = 0
            for (char in buffer.getLine(line)) width += if (char == '\t') 4 - width % 4 else 1
            // An exact-width line has a final empty row for its end-of-line caret / LF.
            val count = width / columns + 1
            starts[line + 1] = (starts[line].toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    val rowCount: Int get() = starts.last()

    fun firstRow(line: Int): Int = starts[line]

    fun rowFor(line: Int, column: Int): Int = starts[line] + column / columns

    fun lineAt(row: Int): Int {
        val target = row.coerceIn(0, rowCount - 1)
        var low = 0
        var high = starts.size - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (starts[middle] <= target) low = middle else high = middle - 1
        }
        return low.coerceAtMost(starts.size - 2)
    }

    fun segmentStart(row: Int, line: Int): Int = (row - starts[line]) * columns
}
