package com.edro08.structa.domain.editor.syntax

/** A small contiguous snapshot, indexed by the document's actual line numbers. */
data class SyntaxWindow(val languageId: String, val firstLine: Int, val lines: List<HighlightedLine>) {
    fun spansAt(line: Int): List<SyntaxSpan> = lines.getOrNull(line - firstLine)?.result?.spans.orEmpty()
}

data class WindowHighlightResult(val window: SyntaxWindow, val checkpoints: Map<Int, TokenizerState>)

object ViewportHighlighter {
    const val CHECKPOINT_LINES = 128

    /** [text] contains complete lines from [startLine] through the requested last line. */
    fun highlight(text: String, startLine: Int, firstLine: Int, language: Language,
        incoming: TokenizerState, checkCancelled: () -> Unit = {}): WindowHighlightResult {
        var state = incoming
        val visible = ArrayList<HighlightedLine>()
        val checkpoints = mutableMapOf<Int, TokenizerState>()
        text.split('\n').forEachIndexed { offset, line ->
            checkCancelled()
            val number = startLine + offset
            val result = language.tokenizer.tokenize(line, state, checkCancelled)
            if (number >= firstLine) visible += HighlightedLine(line, state, result)
            state = result.state
            if ((number + 1) % CHECKPOINT_LINES == 0) checkpoints[number + 1] = state
        }
        return WindowHighlightResult(SyntaxWindow(language.id, firstLine, visible), checkpoints)
    }
}
