package com.edro08.structa.data.filesystem.direct

import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.document.MAX_EDITABLE_BYTES
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.FileSystem
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Shared storage via real paths; never traverses Android/data or Android/obb. */
class DirectFileSystem : FileSystem {
    override val key = "direct"

    private fun file(id: DocumentId): File {
        require(File(id.value).isAbsolute) { "Se requiere una ruta absoluta" }
        val resolved = File(id.value).canonicalFile
        val parts = resolved.path.split(File.separatorChar)
        if (parts.windowed(2).any { it[0].equals("Android", true) &&
                (it[1].equals("data", true) || it[1].equals("obb", true)) })
            throw IOException("Esta ruta requiere acceso privilegiado")
        return resolved
    }

    private fun entry(file: File): FileEntry {
        if (!file.exists()) throw IOException("El archivo ya no existe")
        return FileEntry(DocumentId(file.canonicalPath), file.name.ifBlank { file.path },
            if (file.isDirectory) 0 else file.length(), file.isDirectory)
    }

    override suspend fun stat(id: DocumentId): FileEntry = withContext(Dispatchers.IO) { entry(file(id)) }

    override suspend fun exists(id: DocumentId): Boolean = withContext(Dispatchers.IO) { file(id).exists() }

    override suspend fun list(directory: DocumentId): List<FileEntry> = withContext(Dispatchers.IO) {
        val folder = file(directory)
        if (!folder.isDirectory) throw IOException("La carpeta no existe o no se puede leer")
        val children = folder.listFiles() ?: throw IOException("No se puede leer la carpeta")
        children.filter { child ->
            // Restricted directories may be shown, but may not be entered.
            try { file(DocumentId(child.path)); true } catch (_: IOException) { false }
        }.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }).map(::entry)
    }

    override suspend fun read(file: FileEntry): String = withContext(Dispatchers.IO) {
        val source = file(file.id)
        if (!source.isFile) throw IOException("No se puede leer una carpeta como archivo")
        if (source.length() > MAX_EDITABLE_BYTES) throw IOException("Este archivo supera los 25 MB y aun no puede editarse.")
        source.inputStream().buffered().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                ensureActive()
                val count = input.read(bytes)
                if (count < 0) break
                total += count
                if (total > MAX_EDITABLE_BYTES) throw IOException("Este archivo supera los 25 MB y aun no puede editarse.")
                output.write(bytes, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    override suspend fun write(destination: DocumentId, content: String): Unit = withContext(Dispatchers.IO) {
        val target = file(destination)
        if (!target.isFile) throw IOException("El archivo no existe o es una carpeta")
        target.outputStream().bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    override suspend fun create(parent: DocumentId, name: String, directory: Boolean): FileEntry = withContext(Dispatchers.IO) {
        validateName(name)
        val folder = file(parent)
        if (!folder.isDirectory) throw IOException("La carpeta no existe")
        val target = file(DocumentId(File(folder, name).path))
        if (target.exists()) throw IOException("Ya existe un archivo con ese nombre")
        if (directory) {
            if (!target.mkdir()) throw IOException("No se pudo crear la carpeta")
        } else if (!target.createNewFile()) throw IOException("No se pudo crear el archivo")
        entry(target)
    }

    override suspend fun rename(file: DocumentId, name: String): FileEntry = withContext(Dispatchers.IO) {
        validateName(name)
        val source = file(file)
        if (!source.exists()) throw IOException("El archivo ya no existe")
        val target = file(DocumentId(File(source.parentFile, name).path))
        if (target != source && target.exists()) throw IOException("Ya existe un archivo con ese nombre")
        if (target != source && !source.renameTo(target)) throw IOException("No se pudo renombrar el archivo")
        entry(target)
    }

    override suspend fun delete(file: DocumentId): Unit = withContext(Dispatchers.IO) {
        val target = file(file)
        if (!target.delete()) throw IOException("No se pudo eliminar el archivo (las carpetas deben estar vacías)")
    }

    private fun validateName(name: String) {
        require(name.isNotBlank() && name != "." && name != ".." && name.none {
            it == '/' || it == '\\' || it == '\u0000'
        }) { "Nombre de archivo no válido" }
    }
}
