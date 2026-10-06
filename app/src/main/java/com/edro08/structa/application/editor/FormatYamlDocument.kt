package com.edro08.structa.application.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Whitespace-only formatter for a conservative subset of YAML block mappings and sequences.
 *  Anything that cannot be classified unambiguously is returned verbatim. */
class FormatYamlDocument {
    suspend operator fun invoke(content: String): String = withContext(Dispatchers.Default) {
        val output = StringBuilder(content.length)
        val levels = mutableListOf<Level>()
        var previous: Entry? = null
        var offset = 0
        while (offset < content.length) {
            coroutineContext.ensureActive()
            val end = content.indexOf('\n', offset).let { if (it < 0) content.length else it }
            val cr = end > offset && content[end - 1] == '\r'
            val raw = content.substring(offset, if (cr) end - 1 else end)
            val suffix = when { end == content.length -> ""; cr -> "\r\n"; else -> "\n" }
            offset = (end + 1).coerceAtMost(content.length)
            val trimmed = raw.trimStart(' ')
            if (trimmed.isBlank() || trimmed.startsWith('#')) {
                output.append(raw).append(suffix)
                continue
            }
            val indent = raw.length - trimmed.length
            if (raw.take(indent).any { it != ' ' }) return@withContext content
            val entry = classify(trimmed) ?: return@withContext content
            if (levels.isEmpty()) {
                levels += Level(indent, entry.kind)
            } else if (indent > levels.last().indent) {
                val parent = previous ?: return@withContext content
                if (!parent.children || levels.size >= 128) return@withContext content
                levels += Level(indent, entry.kind)
            } else {
                while (levels.isNotEmpty() && indent < levels.last().indent) levels.removeAt(levels.lastIndex)
                if (levels.isEmpty() || levels.last().indent != indent) return@withContext content
            }
            if (entry.kind != levels.last().kind) return@withContext content
            val depth = levels.size - 1
            output.append("  ".repeat(depth)).append(trimmed).append(suffix)
            previous = entry
        }
        output.toString()
    }

    private fun classify(text: String): Entry? {
        if (text.startsWith('-') && (text.length == 1 || text[1].isWhitespace())) {
            val rest = text.drop(1).trimStart()
            if (rest.isEmpty() || rest.startsWith('#')) return Entry(Kind.SEQUENCE, true)
            val mapping = mapping(rest)
            if (mapping != null) return Entry(Kind.SEQUENCE, false) // Implicit item mappings need a full YAML parser.
            if (ambiguous(rest)) return null
            return Entry(Kind.SEQUENCE, false)
        }
        val mapping = mapping(text) ?: return null
        return Entry(Kind.MAPPING, mapping)
    }

    /** Returns whether a mapping value is empty (and can therefore contain children). */
    private fun mapping(text: String): Boolean? {
        var end = 0
        if (text.startsWith('"') || text.startsWith('\'')) {
            val quote = text[0]
            end = 1
            while (end < text.length) {
                if (quote == '"' && text[end] == '\\') { end += 2; continue }
                if (text[end] == quote) {
                    if (quote == '\'' && text.getOrNull(end + 1) == '\'') { end += 2; continue }
                    end++
                    break
                }
                end++
            }
            if (end > text.length || text.getOrNull(end - 1) != quote) return null
        } else {
            while (end < text.length && (text[end].isLetterOrDigit() || text[end] in "_.-")) end++
            if (end == 0) return null
        }
        while (end < text.length && text[end] == ' ') end++
        if (text.getOrNull(end) != ':' || (end + 1 < text.length && !text[end + 1].isWhitespace())) return null
        val value = text.substring(end + 1).trimStart()
        if (value.startsWith('|') || value.startsWith('>')) return null // Block scalars have indentation semantics.
        if (ambiguous(value)) return null
        return value.isEmpty() || value.startsWith('#')
    }

    private fun ambiguous(value: String): Boolean = value.startsWith("---") || value.startsWith("...") ||
        value.contains("\t") || AMBIGUOUS_KEY.containsMatchIn(value)

    private enum class Kind { MAPPING, SEQUENCE }
    private data class Entry(val kind: Kind, val children: Boolean)
    private data class Level(val indent: Int, val kind: Kind)
    private companion object { val AMBIGUOUS_KEY = Regex("""\s+[^\s]+:\s""") }
}
