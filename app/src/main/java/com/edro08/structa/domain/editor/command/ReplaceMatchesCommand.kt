package com.edro08.structa.domain.editor.command

import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange
import com.edro08.structa.domain.editor.search.SearchMatch

/** Prevalidated, non-overlapping matches from one revision, applied as one undo step. */
data class ReplaceMatchesCommand(val matches: List<SearchMatch>) : EditorCommand {
    override fun execute(context: EditorContext) {
        require(matches.all { it.replacement != null })
        require(matches.all { it.start >= 0 && it.start <= it.end && it.end <= context.session.length })
        require(matches.zipWithNext().all { (a, b) -> a.end <= b.start })
        require(context.session.length.toLong() + matches.sumOf { it.replacement!!.length.toLong() - (it.end - it.start) } <= Int.MAX_VALUE)
        if (matches.isEmpty()) return
        BeginTransactionCommand.execute(context)
        try {
            for (match in matches.asReversed()) {
                ReplaceTextCommand(TextRange(TextOffset(match.start), TextOffset(match.end)), match.replacement!!).execute(context)
            }
        } finally { CommitTransactionCommand.execute(context) }
    }
}
