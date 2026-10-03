package com.edro08.structa.domain.editor.cursor

/** Nonnegative UTF-16 offset. A buffer-dependent operation must also check EOF. */
@JvmInline
value class TextOffset(val value: Int) : Comparable<TextOffset> {
    init {
        require(value >= 0) { "Offset must be nonnegative: $value" }
    }

    override fun compareTo(other: TextOffset): Int = value.compareTo(other.value)
}
