package com.edro08.structa.data.filesystem

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.edro08.structa.R
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Metadata for both SAF documents and content:// URIs from other apps (e.g. FileProvider). */
suspend fun externalDocument(context: Context, uri: Uri): FileEntry = withContext(Dispatchers.IO) {
    if (uri.scheme != "content") throw IOException("Solo se admiten documentos compartidos mediante content://")
    val resolver = context.contentResolver
    val cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?: throw IOException("No se pudo consultar el archivo")
    cursor.use {
        if (!it.moveToFirst()) throw IOException("El archivo ya no está disponible")
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIndex >= 0 && !it.isNull(nameIndex)) it.getString(nameIndex)
            else uri.lastPathSegment ?: context.getString(R.string.editor_untitled)
        val size = if (sizeIndex >= 0 && !it.isNull(sizeIndex)) it.getLong(sizeIndex) else 0L
        FileEntry(DocumentId(uri.toString()), name, size,
            resolver.getType(uri) == DocumentsContract.Document.MIME_TYPE_DIR)
    }
}
