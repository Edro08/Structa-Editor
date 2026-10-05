package com.edro08.structa.domain.editor.syntax

data class HighlightedLine(val text: String, val incoming: TokenizerState, val result: TokenizationResult)
data class SyntaxSnapshot(val languageId: String, val text: String, val lines: List<HighlightedLine>,
    val tokenizedLines: Int, val limited: Boolean = false)

/** Immutable snapshots allow cancelled workers to finish without corrupting the last published cache. */
object IncrementalHighlighter {
    const val MAX_TEXT_LENGTH = 1_048_576

    fun highlight(text: String, language: Language, previous: SyntaxSnapshot? = null,
        checkCancelled: () -> Unit = {}): SyntaxSnapshot {
        checkCancelled()
        if (text.length > MAX_TEXT_LENGTH || language.id == "text")
            return SyntaxSnapshot(language.id, text, emptyList(), 0, text.length > MAX_TEXT_LENGTH)
        val old = previous?.takeIf { it.languageId == language.id && !it.limited }?.lines.orEmpty()
        val source = text.split('\n')
        var prefix = 0
        while (prefix < minOf(source.size, old.size) && source[prefix] == old[prefix].text) {
            checkCancelled(); prefix++
        }
        var suffix = 0
        while (suffix < minOf(source.size, old.size) - prefix &&
            source[source.lastIndex - suffix] == old[old.lastIndex - suffix].text) {
            checkCancelled(); suffix++
        }
        val lines = ArrayList<HighlightedLine>(source.size)
        lines.addAll(old.take(prefix))
        var state = lines.lastOrNull()?.result?.state ?: TokenizerState()
        var tokenized = 0
        var index = prefix
        while (index < source.size) {
            checkCancelled()
            val oldIndex = index - source.size + old.size
            if (index >= source.size - suffix && old[oldIndex].incoming == state) {
                lines.addAll(old.subList(oldIndex, old.size))
                break
            }
            val result = language.tokenizer.tokenize(source[index], state, checkCancelled)
            lines += HighlightedLine(source[index], state, result)
            state = result.state
            tokenized++; index++
        }
        return SyntaxSnapshot(language.id, text, lines.toList(), tokenized)
    }
}
