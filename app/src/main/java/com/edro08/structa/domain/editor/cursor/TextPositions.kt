package com.edro08.structa.domain.editor.cursor

import com.edro08.structa.domain.editor.buffer.TextBuffer

/**
 * Converts a UTF-16 offset using the buffer's line index. EOF is accepted;
 * an LF offset is the end of its preceding line. Throws [IndexOutOfBoundsException]
 * for offsets beyond EOF. CR and tabs each count as one column.
 */
fun TextBuffer.toLineColumn(offset: TextOffset): LineColumn {
    val line = getLineForOffset(offset.value)
    return LineColumn(line, offset.value - getLineStart(line))
}

/**
 * Converts a logical position without clamping. End-of-line columns are accepted,
 * but columns beyond them throw [IndexOutOfBoundsException], as do missing lines.
 * Callers such as hit testing must clamp explicitly when that behavior is wanted.
 */
fun TextBuffer.toOffset(position: LineColumn): TextOffset {
    val start = getLineStart(position.line)
    val lineLength = getLineEnd(position.line) - start
    if (position.column > lineLength) {
        throw IndexOutOfBoundsException("Column: ${position.column}, line length: $lineLength")
    }
    return TextOffset(start + position.column)
}
