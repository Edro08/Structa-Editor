package com.edro08.structa.domain.editor.syntax

/** Line-local, half-open UTF-16 ranges. No visual metadata is stored in the buffer. */
enum class SyntaxStyle { KEYWORD, STRING, NUMBER, COMMENT, OPERATOR, KEY, HEADING }
data class SyntaxSpan(val start: Int, val end: Int, val style: SyntaxStyle) {
    init { require(start >= 0 && end > start) }
}
data class TokenizerState(val commentDepth: Int = 0, val delimiter: String = "", val fence: String = "")
data class TokenizationResult(val spans: List<SyntaxSpan>, val state: TokenizerState)
interface LanguageTokenizer {
    fun tokenize(text: CharSequence, state: TokenizerState = TokenizerState(),
        checkCancelled: () -> Unit = {}): TokenizationResult
}
data class Language(val id: String, val title: String, val extensions: Set<String>, val tokenizer: LanguageTokenizer)

/** Filename detection belongs here, never in the renderer. Unknown extensions use plain text. */
object LanguageRegistry {
    val plain = Language("text", "Plain Text", setOf("txt"), object : LanguageTokenizer {
        override fun tokenize(text: CharSequence, state: TokenizerState, checkCancelled: () -> Unit) =
            TokenizationResult(emptyList(), TokenizerState())
    })
    val languages = listOf(plain,
        Language("kotlin", "Kotlin", setOf("kt", "kts"), CodeTokenizer("kotlin",
            "as break class continue do else false for fun if in interface is null object package return super this throw true try typealias typeof val var when while by catch constructor delegate dynamic field file finally get import init param property receiver set setparam where actual abstract annotation companion const crossinline data enum expect external final infix inline inner internal lateinit noinline open operator out override private protected public reified sealed suspend tailrec vararg")),
        Language("java", "Java", setOf("java"), CodeTokenizer("java",
            "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long native new null package private protected public record return sealed short static strictfp super switch synchronized this throw throws transient true false try var void volatile while yield")),
        Language("go", "Go", setOf("go"), CodeTokenizer("go",
            "break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var true false nil")),
        Language("json", "JSON", setOf("json", "jsonl"), CodeTokenizer("json", "true false null")),
        Language("yaml", "YAML", setOf("yaml", "yml"), CodeTokenizer("yaml", "true false null yes no on off")),
        Language("markdown", "Markdown", setOf("md", "markdown"), MarkdownTokenizer))

    fun forFileName(name: String): Language {
        val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        return languages.firstOrNull { extension in it.extensions } ?: plain
    }
}

/** Small deterministic lexical highlighter, not a compiler/parser. Loops are cancellation-aware. */
internal class CodeTokenizer(private val kind: String, keywords: String) : LanguageTokenizer {
    private val words = keywords.split(' ').toSet()
    override fun tokenize(text: CharSequence, state: TokenizerState, checkCancelled: () -> Unit): TokenizationResult {
        val spans = mutableListOf<SyntaxSpan>()
        var depth = state.commentDepth
        var delimiter = state.delimiter
        var i = 0
        var work = 0
        fun at(s: String): Boolean = i + s.length <= text.length && s.indices.all { text[i + it] == s[it] }
        fun tick() { if (work++ % 256 == 0) checkCancelled() }
        fun emit(start: Int, style: SyntaxStyle) { if (i > start) spans += SyntaxSpan(start, i, style) }
        fun comment() {
            while (i < text.length) {
                tick()
                if (at("*/")) { i += 2; depth--; if (depth == 0) break }
                else if (kind == "kotlin" && at("/*")) { depth++; i += 2 }
                else i++
            }
        }
        fun string() {
            val quote = delimiter
            while (i < text.length) {
                tick()
                if (at(quote)) {
                    i += quote.length
                    if (kind == "yaml" && quote == "'" && at("'")) { i++; continue }
                    delimiter = ""; break
                }
                if (text[i] == '\\' && quote.length == 1 && quote != "`" && !(kind == "yaml" && quote == "'"))
                    i = (i + 2).coerceAtMost(text.length)
                else i++
            }
            // Ordinary code/JSON quotes do not leak over a malformed line. YAML quotes may span lines.
            if (delimiter.length == 1 && delimiter != "`" && kind != "yaml") delimiter = ""
        }
        while (i < text.length) {
            checkCancelled()
            val start = i
            if (depth > 0) { comment(); emit(start, SyntaxStyle.COMMENT); continue }
            if (delimiter.isNotEmpty()) { string(); emit(start, SyntaxStyle.STRING); continue }
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                (kind == "yaml" && c == '#' && (i == 0 || text[i - 1].isWhitespace())) ||
                    (kind !in setOf("json", "yaml") && at("//")) -> {
                    i = text.length; emit(start, SyntaxStyle.COMMENT)
                }
                kind !in setOf("json", "yaml") && at("/*") -> {
                    depth = 1; i += 2; comment(); emit(start, SyntaxStyle.COMMENT)
                }
                c == '"' || (c == '\'' && kind != "json") || (c == '`' && kind == "go") -> {
                    delimiter = if (kind in setOf("kotlin", "java") && at("\"\"\"")) "\"\"\"" else c.toString()
                    i += delimiter.length; string()
                    var next = i
                    while (next < text.length && text[next].isWhitespace()) { checkCancelled(); next++ }
                    emit(start, if (kind in setOf("json", "yaml") && next < text.length && text[next] == ':') SyntaxStyle.KEY else SyntaxStyle.STRING)
                }
                c.isDigit() -> {
                    i++
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] in "._" ||
                            (text[i] in "+-" && text[i - 1] in "eEpP"))) { tick(); i++ }
                    emit(start, SyntaxStyle.NUMBER)
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    i++
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] in "_$")) { tick(); i++ }
                    val word = text.subSequence(start, i).toString()
                    var next = i
                    while (next < text.length && text[next].isWhitespace()) { checkCancelled(); next++ }
                    if (kind == "yaml" && next < text.length && text[next] == ':') emit(start, SyntaxStyle.KEY)
                    else if (word in words) emit(start, SyntaxStyle.KEYWORD)
                }
                else -> { i++; if (c in "{}[]():,=+-*/!<>?&|%;.@") emit(start, SyntaxStyle.OPERATOR) }
            }
        }
        return TokenizationResult(spans.toList(), TokenizerState(depth, delimiter))
    }
}

internal object MarkdownTokenizer : LanguageTokenizer {
    override fun tokenize(text: CharSequence, state: TokenizerState, checkCancelled: () -> Unit): TokenizationResult {
        checkCancelled()
        val value = text.toString().trimStart()
        val marker = when { value.startsWith("```") -> '`'; value.startsWith("~~~") -> '~'; else -> null }
        val fence = marker?.let { value.takeWhile { char -> char == it } }.orEmpty()
        val next = if (state.fence.isNotEmpty()) {
            if (fence.firstOrNull() == state.fence.first() && fence.length >= state.fence.length &&
                value.drop(fence.length).isBlank()) TokenizerState() else state
        } else if (fence.isNotEmpty()) TokenizerState(fence = fence) else state
        val style = when {
            state.fence.isNotEmpty() || fence.isNotEmpty() -> SyntaxStyle.STRING
            value.startsWith('#') -> SyntaxStyle.HEADING
            value.startsWith('>') -> SyntaxStyle.COMMENT
            else -> null
        }
        return TokenizationResult(if (style != null && text.isNotEmpty()) listOf(SyntaxSpan(0, text.length, style)) else emptyList(), next)
    }
}
