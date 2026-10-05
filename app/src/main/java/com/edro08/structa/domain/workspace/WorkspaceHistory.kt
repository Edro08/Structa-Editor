package com.edro08.structa.domain.workspace

import com.edro08.structa.domain.document.DocumentId

data class WorkspaceShortcut(
    val id: DocumentId,
    val name: String,
    val path: String?,
    val lastOpenedAt: Long,
    val favorite: Boolean = false
)

interface WorkspaceHistoryRepository {
    fun all(): List<WorkspaceShortcut>
    fun opened(id: DocumentId, at: Long)
    fun rename(id: DocumentId, name: String)
    fun setFavorite(id: DocumentId, favorite: Boolean)
    fun remove(id: DocumentId)
}
