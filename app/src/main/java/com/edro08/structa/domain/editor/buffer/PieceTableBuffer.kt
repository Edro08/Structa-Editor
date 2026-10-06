package com.edro08.structa.domain.editor.buffer

/**
 * Piece table with an immutable original store and an append-only added store.
 * Edits splice descriptors, never copy the complete document. Adjacent contiguous
 * slices of the same store are merged. Piece lookup is initially linear in piece count.
 */
class PieceTableBuffer(initialText: CharSequence = "") : TextBuffer {
    private val original = initialText.toString()
    private val added = StringBuilder()
    private var pieces = if (original.isEmpty()) mutableListOf() else
        mutableListOf(Piece(BufferSource.ORIGINAL, 0, original.length))
    private val lines = MutableLineIndex(original)
    private val recentChanges = ArrayDeque<Pair<Long, Int>>()
    var changeSerial: Long = 0
        private set

    /** Earliest affected line since a reader's last revision; zero if the bounded log expired. */
    fun firstChangedLineSince(serial: Long): Int? {
        if (serial == changeSerial) return null
        if (serial < (recentChanges.firstOrNull()?.first ?: changeSerial) - 1) return 0
        return recentChanges.filter { it.first > serial }.minOfOrNull { it.second } ?: 0
    }

    /** Read-only interface to the live line index. */
    val lineIndex: LineIndex get() = lines

    override var length: Int = original.length
        private set
    override val lineCount: Int get() = lines.lineCount

    override fun charAt(offset: Int): Char {
        if (offset !in 0 until length) throw IndexOutOfBoundsException("Offset: $offset, length: $length")
        var position = 0
        for (piece in pieces) {
            if (offset < position + piece.length) return source(piece)[piece.start + offset - position]
            position += piece.length
        }
        error("Invalid piece table")
    }

    override fun insert(offset: Int, text: CharSequence) = replace(offset, offset, text)

    override fun delete(start: Int, end: Int) = replace(start, end, "")

    override fun replace(start: Int, end: Int, text: CharSequence) {
        checkRange(start, end)
        // Snapshot the supplied sequence once, including mutable CharSequences.
        val inserted = text.toString()
        if (start == end && inserted.isEmpty()) return
        val changedLine = lines.getLineForOffset(start)
        require(inserted.length <= Int.MAX_VALUE - (length - (end - start))) { "Document too large" }
        require(inserted.length <= Int.MAX_VALUE - added.length) { "Added store too large" }

        val replacement = ArrayList<Piece>()
        appendRange(replacement, 0, start)
        if (inserted.isNotEmpty()) {
            appendPiece(replacement, Piece(BufferSource.ADDED, added.length, inserted.length))
        }
        appendRange(replacement, end, length)
        added.append(inserted)
        lines.replace(start, end, inserted)
        pieces = replacement
        length += inserted.length - (end - start)
        recentChanges.addLast(++changeSerial to changedLine)
        if (recentChanges.size > 1024) recentChanges.removeFirst()
    }

    override fun getText(start: Int, end: Int): CharSequence {
        checkRange(start, end)
        if (start == end) return ""
        val result = StringBuilder(end - start)
        visitRange(start, end) { piece, from, count ->
            result.append(source(piece), from, from + count)
        }
        return result.toString()
    }

    override fun getLine(line: Int): CharSequence = getText(getLineStart(line), getLineEnd(line))
    override fun getLineStart(line: Int): Int = lines.getLineStart(line)
    override fun getLineEnd(line: Int): Int = lines.getLineEnd(line)
    override fun getLineForOffset(offset: Int): Int = lines.getLineForOffset(offset)

    private fun appendRange(target: MutableList<Piece>, start: Int, end: Int) {
        visitRange(start, end) { piece, from, count ->
            appendPiece(target, Piece(piece.source, from, count))
        }
    }

    private inline fun visitRange(start: Int, end: Int, visit: (Piece, Int, Int) -> Unit) {
        if (start == end) return
        var position = 0
        for (piece in pieces) {
            val next = position + piece.length
            val from = maxOf(start, position)
            val to = minOf(end, next)
            if (from < to) visit(piece, piece.start + from - position, to - from)
            if (next >= end) break
            position = next
        }
    }

    private fun appendPiece(target: MutableList<Piece>, piece: Piece) {
        val last = target.lastOrNull()
        if (last != null && last.source == piece.source && last.start + last.length == piece.start) {
            target[target.lastIndex] = last.copy(length = last.length + piece.length)
        } else {
            target.add(piece)
        }
    }

    private fun source(piece: Piece): CharSequence = when (piece.source) {
        BufferSource.ORIGINAL -> original
        BufferSource.ADDED -> added
    }

    private fun checkRange(start: Int, end: Int) {
        if (start < 0 || end < start || end > length) {
            throw IndexOutOfBoundsException("Range: [$start, $end), length: $length")
        }
    }
}
