package com.edro08.structa.domain.editor.history

import com.edro08.structa.domain.editor.buffer.TextBuffer

/** Stores only affected text, never a snapshot of the entire document. */
sealed interface EditOperation {
    val offset: Int
    val removedText: String
    val insertedText: String

    data class Insert(override val offset: Int, val text: String) : EditOperation {
        init { require(offset >= 0) }
        override val removedText: String get() = ""
        override val insertedText: String get() = text
    }

    data class Delete(override val offset: Int, val text: String) : EditOperation {
        init { require(offset >= 0) }
        override val removedText: String get() = text
        override val insertedText: String get() = ""
    }

    /** A replacement is applied with one buffer call, including during replay. */
    data class Replace(
        override val offset: Int,
        override val removedText: String,
        override val insertedText: String
    ) : EditOperation {
        init { require(offset >= 0) }
    }
}

internal fun EditOperation.applyTo(buffer: TextBuffer, inverse: Boolean = false) {
    val removed = if (inverse) insertedText else removedText
    val inserted = if (inverse) removedText else insertedText
    check(offset <= buffer.length && removed.length <= buffer.length - offset) { "History range is stale" }
    check(buffer.getText(offset, offset + removed.length).toString() == removed) { "History text is stale" }
    buffer.replace(offset, offset + removed.length, inserted)
}
