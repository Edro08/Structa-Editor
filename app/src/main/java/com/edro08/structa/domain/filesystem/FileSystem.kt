package com.edro08.structa.domain.filesystem

import com.edro08.structa.domain.document.DocumentId

/** Provider-neutral identifiers; no physical path is required. */
data class FileRef(val id: DocumentId, val fileSystem: String)

interface FileSystem : FileReader, FileWriter, DirectoryReader {
    val key: String
    suspend fun stat(id: DocumentId): FileEntry
    suspend fun create(parent: DocumentId, name: String, directory: Boolean): FileEntry
    /** Providers may assign a new identifier on rename. */
    suspend fun rename(file: DocumentId, name: String): FileEntry
    suspend fun delete(file: DocumentId)
}
