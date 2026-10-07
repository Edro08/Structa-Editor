package com.edro08.structa.domain.editor

import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.syntax.*
import org.junit.Assert.*
import org.junit.Test

class ViewportHighlighterTest {
    @Test fun checkpointCarriesMultilineStateIntoVisibleWindow() {
        val language = LanguageRegistry.forFileName("file.go")
        val prefix = "/*\n" + "inside\n".repeat(ViewportHighlighter.CHECKPOINT_LINES - 1)
        val first = ViewportHighlighter.highlight(prefix.dropLast(1), 0, 0, language, TokenizerState())
        val state = first.checkpoints.getValue(ViewportHighlighter.CHECKPOINT_LINES)
        val visible = ViewportHighlighter.highlight("still inside\n*/ var x = 1", ViewportHighlighter.CHECKPOINT_LINES,
            ViewportHighlighter.CHECKPOINT_LINES, language, state).window
        assertEquals(SyntaxStyle.COMMENT, visible.spansAt(ViewportHighlighter.CHECKPOINT_LINES).single().style)
        assertEquals(SyntaxStyle.KEYWORD, visible.spansAt(ViewportHighlighter.CHECKPOINT_LINES + 1)[1].style)
        assertTrue(visible.spansAt(0).isEmpty())
    }

    @Test fun bufferChangesReportEarliestAffectedLineAcrossMultipleEdits() {
        val buffer = PieceTableBuffer("one\ntwo\nthree\nfour")
        val serial = buffer.changeSerial
        buffer.replace(buffer.getLineStart(3), buffer.getLineEnd(3), "last")
        buffer.insert(buffer.getLineStart(1), "added\n")
        assertEquals(1, buffer.firstChangedLineSince(serial))
        assertEquals(null, buffer.firstChangedLineSince(buffer.changeSerial))
    }
}
