package com.edro08.structa.domain.workspace

import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileRef

data class Workspace(val id: String, val root: FileRef, val openDocuments: List<DocumentId> = emptyList())

data class SessionTab(val id: DocumentId, val anchor: Int, val active: Int,
    val scrollX: Float, val scrollY: Float)

data class EditorSession(val tabs: List<SessionTab> = emptyList(), val active: DocumentId? = null)

/** Metadata only. File contents are always reloaded from the filesystem. */
interface SessionRepository {
    fun load(): EditorSession
    fun save(session: EditorSession)
}
