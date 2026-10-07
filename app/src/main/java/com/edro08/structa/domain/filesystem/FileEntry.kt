package com.edro08.structa.domain.filesystem

import com.edro08.structa.domain.document.DocumentId

data class FileEntry(
    val id: DocumentId,
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean
)
