package com.edro08.structa.data.filesystem

import android.content.Context
import android.net.Uri
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileWriter
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafFileWriter(context: Context) : FileWriter {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun write(destination: DocumentId, content: String): Unit = withContext(Dispatchers.IO) {
        try {
            val output = resolver.openOutputStream(Uri.parse(destination.value), "wt")
                ?: throw IOException("El archivo no se puede escribir")
            output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        } catch (exception: SecurityException) {
            throw IOException("No hay permiso para escribir el archivo", exception)
        }
    }
}
