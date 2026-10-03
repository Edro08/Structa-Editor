package com.edro08.structa.domain.editor.document

import com.edro08.structa.domain.editor.buffer.TextBuffer
import com.edro08.structa.domain.editor.editing.EditingSession
import com.edro08.structa.domain.editor.history.EditHistory
import com.edro08.structa.domain.document.DocumentId
import java.util.UUID

/**
 * In-memory document identity, content and history. No filesystem or visual state.
 * Ownership of the supplied buffer is transferred to the document: mutations must
 * go through the engine's commands. This initial version supports one engine per
 * document; shared-view cursor synchronization belongs to the later split-editor phase.
 */
class EditorDocument(
    val buffer: TextBuffer,
    val id: DocumentId = DocumentId(UUID.randomUUID().toString())
) {
    val history: EditHistory = EditHistory(buffer)
    private var hasEditor = false

    internal fun createEditingSession(): EditingSession {
        check(!hasEditor) { "This document already has an editor engine" }
        hasEditor = true
        return EditingSession(buffer, history)
    }
}
