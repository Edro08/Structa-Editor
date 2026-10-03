package com.edro08.structa.domain.editor

import com.edro08.structa.domain.editor.command.EditorCommand
import com.edro08.structa.domain.editor.command.EditorContext
import com.edro08.structa.domain.editor.cursor.Cursor
import com.edro08.structa.domain.editor.cursor.Selection
import com.edro08.structa.domain.editor.document.EditorDocument

/** Single-threaded command entry point. Logical state is owned by this engine. */
class EditorEngine(val document: EditorDocument) {
    private val session = document.createEditingSession()
    private val context = EditorContext(session)

    val cursor: Cursor get() = session.cursor
    val selection: Selection? get() = session.selection
    val canUndo: Boolean get() = session.canUndo
    val canRedo: Boolean get() = session.canRedo
    val isInTransaction: Boolean get() = session.isInTransaction

    fun execute(command: EditorCommand) {
        command.execute(context)
    }
}
