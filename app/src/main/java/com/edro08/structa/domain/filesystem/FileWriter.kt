package com.edro08.structa.domain.filesystem

import com.edro08.structa.domain.document.DocumentId

interface FileWriter {
    suspend fun write(destination: DocumentId, content: String)
}
