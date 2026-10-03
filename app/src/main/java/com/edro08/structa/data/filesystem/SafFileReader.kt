package com.edro08.structa.data.filesystem

import android.content.Context
import android.net.Uri
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.document.MAX_EDITABLE_BYTES
import com.edro08.structa.domain.filesystem.FileReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SafFileReader(context: Context) : FileReader {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(file: FileEntry): String = withContext(Dispatchers.IO) {
        if (file.isDirectory) throw IOException("No se puede leer una carpeta como archivo")
        if (file.sizeBytes > MAX_EDITABLE_BYTES) throw IOException("Este archivo supera los 25 MB y aun no puede editarse.")
        try {
            val input = resolver.openInputStream(Uri.parse(file.id.value))
                ?: throw IOException("El archivo no se puede leer")
            input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    ensureActive()
                    val count = stream.read(buffer, 0, minOf(buffer.size.toLong(), MAX_EDITABLE_BYTES - total + 1).toInt())
                    if (count < 0) break
                    total += count
                    if (total > MAX_EDITABLE_BYTES) throw IOException("Este archivo supera los 25 MB y aun no puede editarse.")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } catch (exception: SecurityException) {
            throw IOException("No hay permiso para leer el archivo", exception)
        }
    }
}
