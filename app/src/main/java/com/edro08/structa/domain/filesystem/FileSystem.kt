package com.edro08.structa.domain.filesystem

import com.edro08.structa.domain.document.DocumentId

interface FileSystem : FileReader, FileWriter, DirectoryReader {
    val key: String
    suspend fun stat(id: DocumentId): FileEntry
    suspend fun create(parent: DocumentId, name: String, directory: Boolean): FileEntry
    /** Providers may assign a new identifier on rename. */
    suspend fun rename(file: DocumentId, name: String): FileEntry
    suspend fun delete(file: DocumentId)
    suspend fun exists(id: DocumentId): Boolean = try { stat(id); true } catch (_: java.io.IOException) { false }
}
