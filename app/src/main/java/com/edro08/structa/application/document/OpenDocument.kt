package com.edro08.structa.application.document

import com.edro08.structa.application.editor.ContentFormatDetector
import com.edro08.structa.domain.document.Document
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.document.MAX_EDITABLE_BYTES
import com.edro08.structa.domain.filesystem.FileReader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenDocument(
    private val reader: FileReader,
    private val detector: ContentFormatDetector = ContentFormatDetector()
) {
    suspend operator fun invoke(file: FileEntry): Document {
        if (file.isDirectory) throw IOException("No se puede abrir una carpeta como archivo")
        if (file.sizeBytes > MAX_EDITABLE_BYTES) throw IOException("Este archivo supera los 25 MB y aun no puede editarse.")
        val content = reader.read(file)
        return withContext(Dispatchers.Default) { Document(file, content, detector.detect(content)) }
    }
}
