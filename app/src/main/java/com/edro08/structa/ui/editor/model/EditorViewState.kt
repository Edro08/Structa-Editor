package com.edro08.structa.ui.editor.model

import com.edro08.structa.domain.editor.cursor.Cursor
import com.edro08.structa.domain.editor.cursor.Selection
import com.edro08.structa.domain.editor.cursor.TextOffset

/** In-memory view snapshot, separate from document content/history. Logical edits use the engine. */
class EditorViewState {
    var syntax: com.edro08.structa.domain.editor.syntax.SyntaxSnapshot? = null
    var onScrollChanged: (() -> Unit)? = null
    var cursor: Cursor = Cursor(TextOffset(0))
    var selection: Selection? = null
    var scrollX: Float = 0f
        set(value) { if (field != value) { field = value; onScrollChanged?.invoke() } }
    var scrollY: Float = 0f
        set(value) { if (field != value) { field = value; onScrollChanged?.invoke() } }
}
