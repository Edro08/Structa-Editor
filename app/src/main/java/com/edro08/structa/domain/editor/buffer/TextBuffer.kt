package com.edro08.structa.domain.editor.buffer

/**
 * Mutable text addressed by UTF-16 offsets. Ranges are [start, end), lines are zero-based.
 * Empty text has one line. LF separates lines; CR is preserved as ordinary content,
 * including in CRLF files. Line ends exclude LF, so CRLF text round-trips unchanged.
 * Invalid offsets, ranges and line numbers throw [IndexOutOfBoundsException].
 * Implementations are not required to be thread-safe.
 */
interface TextBuffer {
    val length: Int
    val lineCount: Int

    fun charAt(offset: Int): Char
    fun insert(offset: Int, text: CharSequence)
    fun delete(start: Int, end: Int)
    fun replace(start: Int, end: Int, text: CharSequence)
    fun getText(start: Int, end: Int): CharSequence
    fun getLine(line: Int): CharSequence
    fun getLineStart(line: Int): Int
    fun getLineEnd(line: Int): Int
    fun getLineForOffset(offset: Int): Int
}
