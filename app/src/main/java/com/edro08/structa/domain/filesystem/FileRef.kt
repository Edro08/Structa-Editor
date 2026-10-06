package com.edro08.structa.domain.filesystem

import com.edro08.structa.domain.document.DocumentId

/** Provider plus opaque identifier. Direct IDs are absolute paths; SAF IDs are content URI strings. */
data class FileRef(val id: DocumentId, val fileSystem: String) {
    companion object {
        fun of(id: DocumentId): FileRef = FileRef(id, if (id.value.startsWith("privileged:")) "privileged" else if (id.value.startsWith("/") ||
            (id.value.length >= 3 && id.value[0].isLetter() && id.value[1] == ':' &&
                (id.value[2] == '\\' || id.value[2] == '/'))) "direct" else "saf")
    }
}
