package com.edro08.structa.domain.editor.command

import com.edro08.structa.domain.editor.cursor.TextOffset

data class MoveCursorCommand(val offset: TextOffset, val extendSelection: Boolean = false) : EditorCommand {
    override fun execute(context: EditorContext) = context.session.moveTo(offset, extendSelection)
}

object MoveCursorLeftCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.moveLeft()
}

object MoveCursorRightCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.moveRight()
}

object MoveCursorHomeCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.moveHome()
}

object MoveCursorEndCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.moveEnd()
}

data class SetSelectionCommand(val anchor: TextOffset, val active: TextOffset) : EditorCommand {
    override fun execute(context: EditorContext) = context.session.select(anchor, active)
}

object ClearSelectionCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.clearSelection()
}

object SelectLeftCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.moveLeft(extendSelection = true)
}

object SelectRightCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.moveRight(extendSelection = true)
}

object SelectHomeCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.moveHome(extendSelection = true)
}

object SelectEndCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.moveEnd(extendSelection = true)
}
