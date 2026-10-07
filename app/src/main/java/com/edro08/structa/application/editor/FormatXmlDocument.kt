package com.edro08.structa.application.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Indents markup without rewriting text, attribute values, comments or CDATA. */
class FormatXmlDocument {
    suspend operator fun invoke(content: String): String = withContext(Dispatchers.Default) {
        val elements = mutableListOf<Element>()
        val stack = ArrayDeque<Int>()
        val boundaries = mutableListOf<Boundary>()
        var position = 0
        var previousEnd = -1
        var roots = 0

        while (position < content.length) {
            coroutineContext.ensureActive()
            if (content[position] != '<') {
                val end = content.indexOf('<', position).let { if (it < 0) content.length else it }
                if (content.substring(position, end).any { !it.isWhitespace() }) {
                    if (stack.isEmpty()) return@withContext content
                    elements[stack.last()].mixed = true
                }
                position = end
                continue
            }

            if (previousEnd >= 0) {
                val gap = content.substring(previousEnd, position)
                if (gap.all(Char::isWhitespace)) {
                    val depth = (stack.size - if (content.startsWith("</", position)) 1 else 0).coerceAtLeast(0)
                    boundaries += Boundary(previousEnd, position, stack.lastOrNull(), depth)
                }
            }

            val start = position
            when {
                content.startsWith("<!--", start) -> {
                    position = content.indexOf("-->", start + 4).takeIf { it >= 0 }?.plus(3)
                        ?: return@withContext content
                }
                content.startsWith("<![CDATA[", start) -> {
                    position = content.indexOf("]]>", start + 9).takeIf { it >= 0 }?.plus(3)
                        ?: return@withContext content
                    if (stack.isEmpty()) return@withContext content
                    if (content.substring(start + 9, position - 3).any { !it.isWhitespace() })
                        elements[stack.last()].mixed = true
                }
                content.startsWith("<?", start) -> {
                    position = content.indexOf("?>", start + 2).takeIf { it >= 0 }?.plus(2)
                        ?: return@withContext content
                }
                content.startsWith("<!", start) -> return@withContext content // DTD and declarations need a full parser.
                else -> {
                    val closing = content.startsWith("</", start)
                    var cursor = start + if (closing) 2 else 1
                    val nameStart = cursor
                    while (cursor < content.length && !content[cursor].isWhitespace() && content[cursor] !in "/>" ) cursor++
                    if (cursor == nameStart) return@withContext content
                    val name = content.substring(nameStart, cursor)
                    if (!validName(name)) return@withContext content
                    var quote = '\u0000'
                    var end = cursor
                    while (end < content.length) {
                        val ch = content[end]
                        if (quote != '\u0000') {
                            if (ch == quote) quote = '\u0000'
                        } else if (ch == '\'' || ch == '"') quote = ch
                        else if (ch == '<') return@withContext content
                        else if (ch == '>') break
                        end++
                    }
                    if (end == content.length || quote != '\u0000') return@withContext content
                    val attributes = content.substring(cursor, end)
                    if (closing) {
                        if (attributes.isNotBlank() || stack.isEmpty() || elements[stack.last()].name != name)
                            return@withContext content
                        stack.removeLast()
                    } else {
                        if (!validAttributes(attributes)) return@withContext content
                        if (stack.isEmpty()) roots++
                        if (roots > 1) return@withContext content
                        val parentPreserves = stack.lastOrNull()?.let { elements[it].preserve } ?: false
                        val preserves = parentPreserves || Regex("""(?:^|\s)xml:space\s*=\s*(['"])preserve\1""")
                            .containsMatchIn(attributes)
                        elements += Element(name, preserves)
                        if (!attributes.trimEnd().endsWith("/")) stack.addLast(elements.lastIndex)
                    }
                    position = end + 1
                }
            }
            previousEnd = position
        }
        if (stack.isNotEmpty() || roots != 1) return@withContext content
        val newline = if ("\r\n" in content) "\r\n" else "\n"
        val output = StringBuilder(content.length)
        var copied = 0
        for (boundary in boundaries) {
            coroutineContext.ensureActive()
            val parent = boundary.parent?.let(elements::get)
            if (parent?.mixed == true || parent?.preserve == true) continue
            output.append(content, copied, boundary.start)
            output.append(newline).append("  ".repeat(boundary.depth))
            copied = boundary.end
        }
        output.append(content, copied, content.length).toString()
    }

    private data class Element(val name: String, val preserve: Boolean, var mixed: Boolean = false)
    private data class Boundary(val start: Int, val end: Int, val parent: Int?, val depth: Int)

    private fun validName(name: String): Boolean = name.isNotEmpty() &&
        (name[0].isLetter() || name[0] == '_' || name[0] == ':') &&
        name.drop(1).all { it.isLetterOrDigit() || it in "_.:-" }

    private fun validAttributes(source: String): Boolean {
        var i = 0
        while (i < source.length) {
            val start = i
            while (i < source.length && source[i].isWhitespace()) i++
            if (i == source.length) return true
            if (source[i] == '/' && source.substring(i + 1).isBlank()) return true
            if (i == start) return false
            val nameStart = i
            while (i < source.length && !source[i].isWhitespace() && source[i] != '=') i++
            if (!validName(source.substring(nameStart, i))) return false
            while (i < source.length && source[i].isWhitespace()) i++
            if (i == source.length || source[i++] != '=') return false
            while (i < source.length && source[i].isWhitespace()) i++
            if (i == source.length || source[i] !in "\"'") return false
            val quote = source[i++]
            while (i < source.length && source[i] != quote) {
                if (source[i] == '<') return false
                i++
            }
            if (i == source.length) return false
            i++
        }
        return true
    }
}
