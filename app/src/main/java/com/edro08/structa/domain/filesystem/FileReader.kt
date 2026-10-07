package com.edro08.structa.domain.filesystem

interface FileReader {
    suspend fun read(file: FileEntry): String
}
