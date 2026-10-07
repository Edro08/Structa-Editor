package com.edro08.structa.application.browser

import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.DirectoryReader

class ListDirectory(private val reader: DirectoryReader) {
    suspend operator fun invoke(directory: DocumentId): List<FileEntry> = reader.list(directory)
        .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
}
