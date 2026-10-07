package com.edro08.structa.domain.document

import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.FileMode

const val MAX_EDITABLE_BYTES: Long = 25L * 1024L * 1024L

data class Document(val entry: FileEntry, val content: String, val mode: FileMode)
