package com.edro08.structa.data.filesystem

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.DirectoryReader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SafDirectoryReader(context: Context) : DirectoryReader {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    override suspend fun list(directory: DocumentId): List<FileEntry> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(directory.value)
            val folder = if (DocumentsContract.isTreeUri(uri) && !DocumentsContract.isDocumentUri(context, uri)) {
                DocumentFile.fromTreeUri(context, uri)
            } else {
                DocumentFile.fromSingleUri(context, uri)
            } ?: throw IOException("No hay una carpeta autorizada disponible")
            if (!folder.isDirectory) throw IOException("El documento no es una carpeta accesible")

            val folderUri = folder.uri
            val documentId = DocumentsContract.getDocumentId(folderUri)
            val tree = DocumentsContract.isTreeUri(folderUri)
            val childrenUri = if (tree) {
                DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)
            } else {
                DocumentsContract.buildChildDocumentsUri(folderUri.authority, documentId)
            }
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val cursor = resolver.query(childrenUri, columns, null, null, null)
                ?: throw IOException("La carpeta no se puede leer")
            cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        ensureActive()
                        val mimeType = it.getString(3) ?: continue
                        val childUri = if (tree) {
                            DocumentsContract.buildDocumentUriUsingTree(folderUri, it.getString(0))
                        } else {
                            DocumentsContract.buildDocumentUri(folderUri.authority, it.getString(0))
                        }
                        add(FileEntry(
                            id = DocumentId(childUri.toString()),
                            name = it.getString(1) ?: "Sin nombre",
                            sizeBytes = if (it.isNull(2)) 0L else it.getLong(2),
                            isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        ))
                    }
                }
            }
        } catch (exception: SecurityException) {
            throw IOException("No hay permiso para leer la carpeta", exception)
        }
    }
}
