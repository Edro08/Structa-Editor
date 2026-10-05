package com.edro08.structa.data.filesystem

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.*
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafFileSystem(context: Context) : FileSystem {
    private val context = context.applicationContext
    private val reader = SafFileReader(this.context)
    private val writer = SafFileWriter(this.context)
    private val directories = SafDirectoryReader(this.context)
    override val key = "saf"
    override suspend fun read(file: FileEntry) = reader.read(file)
    override suspend fun write(destination: DocumentId, content: String) = writer.write(destination, content)
    override suspend fun list(directory: DocumentId) = directories.list(directory)

    private fun document(id: DocumentId): DocumentFile {
        val uri = Uri.parse(id.value)
        return (if (DocumentsContract.isTreeUri(uri) && !DocumentsContract.isDocumentUri(context, uri))
            DocumentFile.fromTreeUri(context, uri) else DocumentFile.fromSingleUri(context, uri))
            ?: throw IOException("Documento no disponible")
    }

    private fun entry(file: DocumentFile): FileEntry {
        if (!file.exists()) throw IOException("El documento ya no existe o no hay permiso")
        return FileEntry(DocumentId(file.uri.toString()), file.name ?: "Sin nombre", file.length(), file.isDirectory)
    }

    override suspend fun stat(id: DocumentId) = withContext(Dispatchers.IO) { entry(document(id)) }

    override suspend fun create(parent: DocumentId, name: String, directory: Boolean) = withContext(Dispatchers.IO) {
        validateName(name)
        val folder = document(parent)
        val uri = DocumentsContract.createDocument(context.contentResolver, folder.uri,
            if (directory) DocumentsContract.Document.MIME_TYPE_DIR else "text/plain", name)
            ?: throw IOException("No se pudo crear el documento")
        entry(document(DocumentId(uri.toString())))
    }

    override suspend fun rename(file: DocumentId, name: String) = withContext(Dispatchers.IO) {
        validateName(name)
        val uri = DocumentsContract.renameDocument(context.contentResolver, document(file).uri, name)
            ?: throw IOException("No se pudo renombrar el documento")
        entry(document(DocumentId(uri.toString())))
    }

    override suspend fun delete(file: DocumentId): Unit = withContext(Dispatchers.IO) {
        if (!DocumentsContract.deleteDocument(context.contentResolver, document(file).uri))
            throw IOException("No se pudo eliminar el documento")
    }

    private fun validateName(name: String) {
        require(name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }) {
            "Nombre de archivo no válido"
        }
    }
}
