package com.edro08.structa.domain.editor.command

import com.edro08.structa.domain.editor.cursor.TextRange

data class InsertTextCommand(val text: String) : EditorCommand {
    override fun execute(context: EditorContext) = context.session.insertText(text)
}

data class ReplaceTextCommand(val range: TextRange, val text: String) : EditorCommand {
    override fun execute(context: EditorContext) = context.session.replace(range, text)
}

object DeleteBackwardCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.deleteBackward()
}

object DeleteForwardCommand : EditorCommand {
    override fun execute(context: EditorContext) = context.session.deleteForward()
}
