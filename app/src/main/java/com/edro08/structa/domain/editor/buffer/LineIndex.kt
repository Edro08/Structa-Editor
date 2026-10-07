package com.edro08.structa.domain.editor.buffer

/** Zero-based LF line index, with the same offset and line-end contract as [TextBuffer]. */
interface LineIndex {
    val lineCount: Int
    fun getLineStart(line: Int): Int
    fun getLineEnd(line: Int): Int

    /** Accepts EOF. An LF belongs to the preceding line; the offset after it to the next. */
    fun getLineForOffset(offset: Int): Int
}

/** Queries are O(1) by line and O(log lines) by offset. Edits shift only index entries. */
internal class MutableLineIndex(text: CharSequence) : LineIndex {
    private var textLength = text.length
    private val starts = ArrayList<Int>().apply {
        add(0)
        text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }

    override val lineCount: Int get() = starts.size

    override fun getLineStart(line: Int): Int {
        checkLine(line)
        return starts[line]
    }

    override fun getLineEnd(line: Int): Int {
        checkLine(line)
        return if (line == starts.lastIndex) textLength else starts[line + 1] - 1
    }

    override fun getLineForOffset(offset: Int): Int {
        if (offset !in 0..textLength) throw IndexOutOfBoundsException("Offset: $offset, length: $textLength")
        return upperBound(offset) - 1
    }

    fun replace(start: Int, end: Int, text: String) {
        // A start at `start` survives; starts in (start, end] belong to removed LFs.
        val firstRemoved = upperBound(start)
        val afterRemoved = upperBound(end)
        starts.subList(firstRemoved, afterRemoved).clear()
        val delta = text.length - (end - start)
        for (index in firstRemoved until starts.size) starts[index] = starts[index] + delta
        val inserted = ArrayList<Int>()
        text.forEachIndexed { index, char -> if (char == '\n') inserted.add(start + index + 1) }
        starts.addAll(firstRemoved, inserted)
        textLength += delta
    }

    private fun upperBound(offset: Int): Int {
        var low = 0
        var high = starts.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (starts[middle] <= offset) low = middle + 1 else high = middle
        }
        return low
    }

    private fun checkLine(line: Int) {
        if (line !in starts.indices) throw IndexOutOfBoundsException("Line: $line, count: $lineCount")
    }
}
