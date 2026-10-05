package com.edro08.structa.domain.editor.search

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class SearchOptions(val caseSensitive: Boolean = false, val wholeWord: Boolean = false, val regex: Boolean = false)
data class SearchMatch(val start: Int, val end: Int, val replacement: String? = null)
data class SearchResult(val matches: List<SearchMatch> = emptyList(), val error: String? = null, val truncated: Boolean = false)

/** Immutable UTF-16 snapshots only; never accesses a live editor from a worker thread. */
object DocumentSearch {
    const val MAX_MATCHES = 10_000

    fun find(text: String, query: String, options: SearchOptions = SearchOptions(),
        replacement: String? = null, replacementMatch: SearchMatch? = null, checkCancelled: () -> Unit = {}): SearchResult {
        if (query.isEmpty()) return SearchResult()
        if (query.length > 4096) return SearchResult(error = "La búsqueda admite hasta 4096 caracteres.")
        val deadline = System.nanoTime() + 500_000_000L
        fun check() {
            checkCancelled()
            if (System.nanoTime() > deadline) throw SearchLimit()
        }
        // Cooperative budget: JVM regex consults charAt during backtracking. Native regex
        // implementations may only yield between matches, so this is not a hard timeout.
        val guarded = object : CharSequence {
            override val length get() = text.length
            override fun get(index: Int): Char { check(); return text[index] }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence { check(); return text.subSequence(startIndex, endIndex) }
            override fun toString() = text
        }
        return try {
            val source = if (options.regex) query else Pattern.quote(query)
            val expression = if (options.wholeWord) "(?<![\\p{L}\\p{M}\\p{N}_])(?:$source)(?![\\p{L}\\p{M}\\p{N}_])" else source
            val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            val matcher = Pattern.compile(expression, flags).matcher(guarded)
            val matches = ArrayList<SearchMatch>()
            var replacementSize = 0L
            while (matcher.find()) {
                check()
                if (matches.size == MAX_MATCHES) return SearchResult(matches, truncated = true)
                val expanded = replacement?.takeIf { replacementMatch == null ||
                    (replacementMatch.start == matcher.start() && replacementMatch.end == matcher.end()) }?.let {
                    if (options.regex) expand(matcher, it) else it
                }
                replacementSize += expanded?.length ?: 0
                if (replacementSize > 1024 * 1024) return SearchResult(error = "El reemplazo supera el límite de edición de 1 MB.")
                matches += SearchMatch(matcher.start(), matcher.end(), expanded)
            }
            SearchResult(matches)
        } catch (_: PatternSyntaxException) {
            SearchResult(error = "Expresión regular inválida.")
        } catch (_: SearchLimit) {
            SearchResult(error = "La búsqueda excedió el tiempo disponible. Simplifica la expresión.")
        } catch (_: ReplacementLimit) {
            SearchResult(error = "El reemplazo supera el límite de edición de 1 MB.")
        } catch (_: StackOverflowError) {
            SearchResult(error = "Expresión regular demasiado compleja.")
        } catch (_: IllegalArgumentException) {
            SearchResult(error = "Referencia de grupo inválida en el reemplazo.")
        } catch (_: IndexOutOfBoundsException) {
            SearchResult(error = "Referencia de grupo inválida en el reemplazo.")
        }
    }

    /** Java replacement syntax: $0, $1…, ${name}, and backslash escapes; no document-prefix copies. */
    private fun expand(match: Matcher, replacement: String): String = buildString {
        var i = 0
        while (i < replacement.length) {
            when (val char = replacement[i++]) {
                '\\' -> { require(i < replacement.length); append(replacement[i++]) }
                '$' -> {
                    require(i < replacement.length)
                    if (replacement[i] == '{') {
                        val end = replacement.indexOf('}', i + 1)
                        require(end > i + 1)
                        append(match.group(replacement.substring(i + 1, end)).orEmpty())
                        i = end + 1
                    } else {
                        require(replacement[i] in '0'..'9')
                        var group = replacement[i++].digitToInt()
                        require(group <= match.groupCount())
                        while (i < replacement.length && replacement[i] in '0'..'9') {
                            val next = group * 10 + replacement[i].digitToInt()
                            if (next > match.groupCount()) break
                            group = next; i++
                        }
                        append(match.group(group).orEmpty())
                    }
                }
                else -> append(char)
            }
            if (length > 1024 * 1024) throw ReplacementLimit()
        }
    }

    private class SearchLimit : RuntimeException()
    private class ReplacementLimit : RuntimeException()
}
