package com.edro08.structa.domain.workspace

import com.edro08.structa.domain.filesystem.FileEntry

data class IndexedFile(val entry: FileEntry, val relativePath: String)

/** Subsequence matching, with a preference for contiguous matches and file names. */
object FuzzySearch {
    fun score(candidate: String, query: String): Int? {
        val text = candidate.lowercase()
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return 0
        val exact = text.indexOf(needle)
        if (exact >= 0) return exact + text.length - needle.length
        var position = -1
        var penalty = text.length + 100
        for (char in needle) {
            val next = text.indexOf(char, position + 1)
            if (next < 0) return null
            penalty += (next - position - 1) * 3
            position = next
        }
        return penalty
    }

    fun files(files: List<IndexedFile>, query: String): List<IndexedFile> = files.mapNotNull { file ->
        val name = score(file.entry.name, query)
        val path = score(file.relativePath, query)?.plus(200)
        val score = listOfNotNull(name, path).minOrNull() ?: return@mapNotNull null
        score to file
    }.sortedWith(compareBy<Pair<Int, IndexedFile>> { it.first }.thenBy { it.second.relativePath })
        .take(100).map { it.second }
}
