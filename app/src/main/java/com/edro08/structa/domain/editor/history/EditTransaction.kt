package com.edro08.structa.domain.editor.history

import com.edro08.structa.domain.editor.cursor.Cursor
import com.edro08.structa.domain.editor.cursor.Selection

/** Logical state only; no viewport, scroll or document snapshot. */
data class EditState(val cursor: Cursor, val selection: Selection?) {
    init {
        require(selection == null || (!selection.isEmpty && selection.active == cursor.offset))
    }
}

class EditTransaction internal constructor(
    operations: List<EditOperation>,
    val before: EditState,
    val after: EditState
) {
    val operations: List<EditOperation> = operations.toList()
}
