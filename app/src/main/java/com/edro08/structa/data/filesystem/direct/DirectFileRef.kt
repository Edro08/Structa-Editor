package com.edro08.structa.data.filesystem.direct

import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileRef
import java.io.File

data class DirectFileRef(val path: String) {
    init { require(File(path).isAbsolute) }
    val ref: FileRef get() = FileRef(DocumentId(File(path).canonicalPath), "direct")
}
