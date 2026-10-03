package com.edro08.structa.domain.editor.editing

import com.edro08.structa.domain.editor.buffer.TextBuffer
import com.edro08.structa.domain.editor.cursor.Cursor
import com.edro08.structa.domain.editor.cursor.Selection
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange
import com.edro08.structa.domain.editor.history.EditHistory
import com.edro08.structa.domain.editor.history.EditOperation
import com.edro08.structa.domain.editor.history.EditState

/**
 * Logical editing primitives used by engine commands, independent of Android.
 * All edits to the supplied buffer must go through this session while it is in use.
 * It is single-threaded, coordinates operation history and contains no visual state.
 *
 * Cursor and selection positions use UTF-16 code units. A nonempty selection always
 * has its active end at the cursor; empty selections are represented as null.
 */
class EditingSession internal constructor(
    private val buffer: TextBuffer,
    private val history: EditHistory
) {
    constructor(buffer: TextBuffer) : this(buffer, EditHistory(buffer))
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo
    val isInTransaction: Boolean get() = history.isInTransaction

    var cursor: Cursor = Cursor(TextOffset(0))
        private set

    var selection: Selection? = null
        private set

    /** Inserts at the cursor, replacing the selection if present (even for empty text). */
    fun insertText(text: CharSequence) {
        replace(selection?.range ?: TextRange(cursor.offset, cursor.offset), text)
    }

    /** Replaces an explicit range, clears selection and places the cursor after inserted text. */
    fun replace(range: TextRange, text: CharSequence) {
        checkOffset(range.start)
        checkOffset(range.end)
        val inserted = text.toString()
        val removed = buffer.getText(range.start.value, range.end.value).toString()
        require(inserted.length <= Int.MAX_VALUE - (buffer.length - (range.end.value - range.start.value))) {
            "Document too large"
        }
        val after = EditState(Cursor(TextOffset(range.start.value + inserted.length)), null)
        val operation = when {
            removed.isEmpty() -> EditOperation.Insert(range.start.value, inserted)
            inserted.isEmpty() -> EditOperation.Delete(range.start.value, removed)
            else -> EditOperation.Replace(range.start.value, removed, inserted)
        }
        history.apply(operation, currentState(), after)
        // Publish state only after the buffer has accepted the edit.
        restoreState(after)
    }

    fun beginTransaction() = history.beginTransaction(currentState())

    fun commitTransaction() = history.commitTransaction(currentState())

    fun undo() {
        history.undo()?.let(::restoreState)
    }

    fun redo() {
        history.redo()?.let(::restoreState)
    }

    private fun currentState(): EditState = EditState(cursor, selection)

    private fun restoreState(state: EditState) {
        cursor = state.cursor
        selection = state.selection
    }

    fun deleteBackward() {
        val selected = selection
        if (selected != null) {
            replace(selected.range, "")
        } else if (cursor.offset.value > 0) {
            replace(TextRange(TextOffset(cursor.offset.value - 1), cursor.offset), "")
        }
    }

    fun deleteForward() {
        val selected = selection
        if (selected != null) {
            replace(selected.range, "")
        } else if (cursor.offset.value < buffer.length) {
            replace(TextRange(cursor.offset, TextOffset(cursor.offset.value + 1)), "")
        }
    }

    /** Validates both endpoints before changing state. EOF is allowed. */
    fun select(anchor: TextOffset, active: TextOffset) {
        checkOffset(anchor)
        checkOffset(active)
        selection = Selection(anchor, active).takeUnless { it.isEmpty }
        cursor = Cursor(active)
    }

    /** Leaves the cursor at the active end. */
    fun clearSelection() {
        selection = null
    }

    fun moveTo(offset: TextOffset, extendSelection: Boolean = false) {
        checkOffset(offset)
        if (extendSelection) {
            select(selection?.anchor ?: cursor.offset, offset)
        } else {
            cursor = Cursor(offset)
            selection = null
        }
    }

    /** Without extension, an existing selection collapses left without an extra step. */
    fun moveLeft(extendSelection: Boolean = false) {
        val selected = selection
        val target = if (!extendSelection && selected != null) selected.start
            else TextOffset((cursor.offset.value - 1).coerceAtLeast(0))
        moveTo(target, extendSelection)
    }

    /** Without extension, an existing selection collapses right without an extra step. */
    fun moveRight(extendSelection: Boolean = false) {
        val selected = selection
        val target = if (!extendSelection && selected != null) selected.end
            else TextOffset(if (cursor.offset.value < buffer.length) cursor.offset.value + 1 else buffer.length)
        moveTo(target, extendSelection)
    }

    fun moveHome(extendSelection: Boolean = false) {
        val line = buffer.getLineForOffset(cursor.offset.value)
        moveTo(TextOffset(buffer.getLineStart(line)), extendSelection)
    }

    fun moveEnd(extendSelection: Boolean = false) {
        val line = buffer.getLineForOffset(cursor.offset.value)
        moveTo(TextOffset(buffer.getLineEnd(line)), extendSelection)
    }

    private fun checkOffset(offset: TextOffset) {
        if (offset.value > buffer.length) {
            throw IndexOutOfBoundsException("Offset: ${offset.value}, length: ${buffer.length}")
        }
    }
}
