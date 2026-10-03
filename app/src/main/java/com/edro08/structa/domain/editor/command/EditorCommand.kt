package com.edro08.structa.domain.editor.command

/** Commands contain intent, never references to an engine or a document. */
sealed interface EditorCommand {
    fun execute(context: EditorContext)
}
