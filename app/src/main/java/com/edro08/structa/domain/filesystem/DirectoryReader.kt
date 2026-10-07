package com.edro08.structa.domain.filesystem

import com.edro08.structa.domain.document.DocumentId

interface DirectoryReader {
    suspend fun list(directory: DocumentId): List<FileEntry>
}
