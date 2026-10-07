package com.edro08.structa.domain.editor.buffer

/** A nonempty slice of a backing store, addressed in UTF-16 code units. */
data class Piece(
    val source: BufferSource,
    val start: Int,
    val length: Int
) {
    init {
        require(start >= 0)
        require(length > 0 && start <= Int.MAX_VALUE - length)
    }
}
