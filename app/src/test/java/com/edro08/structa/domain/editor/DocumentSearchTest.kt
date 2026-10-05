package com.edro08.structa.domain.editor

import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.command.*
import com.edro08.structa.domain.editor.document.EditorDocument
import com.edro08.structa.domain.editor.search.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class DocumentSearchTest {
    @Test fun literalCaseAndUnicodeWholeWordUseUtf16Offsets() {
        val text = "😀 Cat cat scatter cat_ caté cat"
        assertEquals(6, DocumentSearch.find(text, "cat").matches.size)
        assertEquals(listOf(3, 7, 29), DocumentSearch.find(text, "cat", SearchOptions(wholeWord = true)).matches.map { it.start })
        assertEquals(2, DocumentSearch.find(text, "cat", SearchOptions(caseSensitive = true, wholeWord = true)).matches.size)
        assertEquals(1, DocumentSearch.find("a.b axb", "a.b").matches.size)
        assertEquals(2, DocumentSearch.find("a b c", " ").matches.size)
        assertTrue(DocumentSearch.find(text, "").matches.isEmpty())
    }

    @Test fun regexGroupsAndEscapesAreExpandedBeforeEditing() {
        val result = DocumentSearch.find("a12 b3", "(?<letter>[ab])(\\d+)", SearchOptions(regex = true), "\${letter}:\$2:\$0")
        assertNull(result.error)
        assertEquals(listOf("a:12:a12", "b:3:b3"), result.matches.map { it.replacement })
        assertEquals("\$1", DocumentSearch.find("x", "x", replacement = "\$1").matches.single().replacement)
        assertEquals("\$", DocumentSearch.find("x", "x", SearchOptions(regex = true), "\\$").matches.single().replacement)
    }

    @Test fun invalidExpressionsAndGroupsReturnNoPartialEdits() {
        assertNotNull(DocumentSearch.find("text", "[", SearchOptions(regex = true)).error)
        val result = DocumentSearch.find("a b", "(a)|(b)", SearchOptions(regex = true), "\$9")
        assertNotNull(result.error)
        assertTrue(result.matches.isEmpty())
    }

    @Test fun zeroWidthMatchesTerminateAndReplaceAllIsOneUndoStep() {
        val engine = EditorEngine(EditorDocument(PieceTableBuffer("ab")))
        val result = DocumentSearch.find("ab", "^|$", SearchOptions(regex = true), "!")
        assertEquals(listOf(0, 2), result.matches.map { it.start })
        engine.execute(ReplaceMatchesCommand(result.matches))
        assertEquals("!ab!", engine.document.buffer.getText(0, 4).toString())
        assertTrue(engine.document.dirty)
        engine.execute(UndoCommand)
        assertEquals("ab", engine.document.buffer.getText(0, 2).toString())
        assertFalse(engine.document.dirty)
        assertFalse(engine.canUndo)
        engine.execute(RedoCommand)
        assertEquals("!ab!", engine.document.buffer.getText(0, 4).toString())
    }

    @Test fun limitsAndCancellationAreExplicit() {
        val result = DocumentSearch.find("a".repeat(DocumentSearch.MAX_MATCHES + 1), "a")
        assertTrue(result.truncated)
        assertEquals(DocumentSearch.MAX_MATCHES, result.matches.size)
        assertNotNull(DocumentSearch.find("aa", "a", replacement = "x".repeat(600_000)).error)
        try {
            DocumentSearch.find("text", "x", checkCancelled = { throw CancellationException() })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test(timeout = 3000) fun pathologicalRegexHasBoundedExecution() {
        assertNotNull(DocumentSearch.find("a".repeat(50_000) + "!", "(a+)+$", SearchOptions(regex = true)).error)
    }

    @Test fun invalidBatchIsRejectedBeforeAnyMutationOrTransaction() {
        val engine = EditorEngine(EditorDocument(PieceTableBuffer("abc")))
        try {
            engine.execute(ReplaceMatchesCommand(listOf(SearchMatch(0, 1, "x"), SearchMatch(2, 4, "y"))))
            fail("Invalid range must fail before editing")
        } catch (_: IllegalArgumentException) { }
        assertEquals("abc", engine.document.buffer.getText(0, 3).toString())
        assertFalse(engine.canUndo)
        assertFalse(engine.document.dirty)
        engine.execute(InsertTextCommand("!"))
        engine.execute(UndoCommand)
        assertEquals("abc", engine.document.buffer.getText(0, 3).toString())
    }
}
