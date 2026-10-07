package com.edro08.structa.domain.editor.cursor

/** Ordered half-open range [start, end). Direction belongs to [Selection]. */
data class TextRange(val start: TextOffset, val end: TextOffset) {
    init {
        require(start <= end) { "Range start must not exceed end" }
    }

    val length: Int get() = end.value - start.value
    val isEmpty: Boolean get() = start == end
}
