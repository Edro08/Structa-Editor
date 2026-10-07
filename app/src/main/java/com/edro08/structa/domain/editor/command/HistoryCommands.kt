package com.edro08.structa.domain.editor.command

object UndoCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.undo()
}

object RedoCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.redo()
}

object BeginTransactionCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.beginTransaction()
}

object CommitTransactionCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.commitTransaction()
}
