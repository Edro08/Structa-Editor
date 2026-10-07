package com.edro08.structa.ui.editor.input

import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.document.EditorDocument
import com.edro08.structa.domain.editor.command.*
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange
import org.junit.Assert.*
import org.junit.Test

class EditorInputSessionTest {
    @Test
    fun composingUpdatesReplaceProvisionalTextAndCommitAsOneUndo() {
        val input = input("ab")
        input.setSelection(1, 1)
        input.setComposingText("h", 1)
        input.setComposingText("hel", 1)
        input.setComposingText("hello", 1)
        assertEquals("ahellob", text(input))
        assertEquals(TextRange(TextOffset(1), TextOffset(6)), input.composition)
        assertTrue(input.canUndo)
        assertFalse(input.engine.canUndo)
        input.commitText("hello", 1)
        assertNull(input.composition)
        assertFalse(input.engine.isInTransaction)
        input.execute(UndoCommand)
        assertEquals("ab", text(input))
        assertEquals(1, input.active)
        assertFalse(input.canUndo)
        input.execute(RedoCommand)
        assertEquals("ahellob", text(input))
        assertEquals(6, input.active)
    }

    @Test
    fun compositionReplacesBackwardSelectionAndUndoRestoresDirection() {
        val input = input("abcd")
        input.setSelection(3, 1)
        input.setComposingText("X", 1)
        input.commitText("YZ", 1)
        assertEquals("aYZd", text(input))
        input.execute(UndoCommand)
        assertEquals("abcd", text(input))
        assertEquals(3, input.anchor)
        assertEquals(1, input.active)
    }

    @Test
    fun commitCursorPositionUsesAndroidRelativeRulesAndRedoRestoresIt() {
        for ((position, cursor) in listOf(1 to 3, 0 to 1, -1 to 0, 2 to 4, Int.MAX_VALUE to 5, Int.MIN_VALUE to 0)) {
            val input = input("abc")
            input.setSelection(1, 1)
            input.commitText("XY", position)
            assertEquals("aXYbc", text(input))
            assertEquals(cursor, input.active)
            input.execute(UndoCommand)
            input.execute(RedoCommand)
            assertEquals(cursor, input.active)
        }
    }

    @Test
    fun existingComposingRegionIsReplacedRatherThanDuplicated() {
        val input = input("hello world")
        input.setSelection(5, 5)
        input.setComposingRegion(5, 0)
        input.setComposingText("hola", 1)
        input.finishComposingText()
        assertEquals("hola world", text(input))
        input.execute(UndoCommand)
        assertEquals("hello world", text(input))
        assertEquals(5, input.active)
    }

    @Test
    fun emptyCompositionAndRepeatedFinishDoNotLeaveOpenTransaction() {
        val input = input("")
        input.setComposingText("x", 1)
        input.setComposingText("", 1)
        input.finishComposingText()
        input.finishComposingText()
        assertEquals("", text(input))
        assertNull(input.composition)
        assertFalse(input.engine.isInTransaction)
        input.commitText("z")
        input.execute(UndoCommand)
        assertEquals("", text(input))
    }

    @Test
    fun selectionOutsideCompositionFinishesItWhileInvalidSelectionDoesNothing() {
        val input = input("ab")
        input.setSelection(1, 1)
        input.setComposingText("XY", 1)
        assertFalse(input.setSelection(-1, 0))
        assertNotNull(input.composition)
        assertTrue(input.setSelection(2, 2))
        assertNotNull(input.composition)
        input.setSelection(0, 0)
        assertNull(input.composition)
        assertFalse(input.engine.isInTransaction)
        input.execute(UndoCommand)
        assertEquals("ab", text(input))
    }

    @Test
    fun deleteSurroundingPreservesSelectedTextAndDirectionInOneTransaction() {
        val input = input("abXYcd")
        input.setSelection(4, 2)
        input.deleteSurroundingText(1, 2)
        assertEquals("aXY", text(input))
        assertEquals(3, input.anchor)
        assertEquals(1, input.active)
        input.execute(UndoCommand)
        assertEquals("abXYcd", text(input))
        assertEquals(4, input.anchor)
        assertEquals(2, input.active)
        assertFalse(input.canUndo)
    }

    @Test
    fun deletionClampsAndRejectsNegativeCounts() {
        val input = input("abc")
        input.setSelection(1, 1)
        assertFalse(input.deleteSurroundingText(-1, 0))
        assertEquals("abc", text(input))
        input.deleteSurroundingText(Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals("", text(input))
        assertEquals(0, input.active)
        input.execute(UndoCommand)
        assertEquals("abc", text(input))
    }

    @Test
    fun codePointDeletionDoesNotSplitSurrogatePairs() {
        val input = input("a\uD83D\uDE00|\uD83D\uDE03z")
        input.setSelection(3, 4)
        input.deleteSurroundingText(1, 1, codePoints = true)
        assertEquals("a|z", text(input))
        assertEquals("|", input.selectedText())
        input.execute(UndoCommand)
        assertEquals("a\uD83D\uDE00|\uD83D\uDE03z", text(input))
    }

    @Test
    fun surroundingDeletionProtectsCompositionAndUpdatesItsRange() {
        val input = input("ab")
        input.setSelection(1, 1)
        input.setComposingText("hi", 1)
        input.deleteSurroundingText(0, 0)
        assertNotNull(input.composition)
        input.deleteSurroundingText(1, 1)
        assertEquals("hi", text(input))
        assertEquals(TextRange(TextOffset(0), TextOffset(2)), input.composition)
        input.commitText("hello", 1)
        assertEquals("hello", text(input))
        input.execute(UndoCommand)
        assertEquals("ab", text(input))
        assertFalse(input.canUndo)
    }

    @Test
    fun codePointDeletionRejectsUnpairedSurrogatesWithoutPartialEdits() {
        val input = input("a|\uD83Dz")
        input.setSelection(1, 2)
        assertFalse(input.deleteSurroundingText(1, 1, codePoints = true))
        assertEquals("a|\uD83Dz", text(input))
        assertEquals("|", input.selectedText())
        assertFalse(input.canUndo)
    }

    @Test
    fun batchEditsPublishOnlyFinalStateAndCloseFlushesUnbalancedBatch() {
        val input = input("")
        val changes = mutableListOf<Pair<Boolean, String>>()
        input.addListener { changes.add(it to text(input)) }
        input.beginBatchEdit()
        input.beginBatchEdit()
        input.setComposingText("a", 1)
        input.setComposingText("ab", 1)
        assertTrue(changes.isEmpty())
        assertTrue(input.endBatchEdit())
        input.close()
        assertEquals(listOf(true to "ab"), changes)
        assertFalse(input.endBatchEdit())
        assertFalse(input.engine.isInTransaction)
        input.execute(UndoCommand)
        assertEquals("", text(input))
    }

    @Test
    fun undoDuringCompositionFinishesItBeforeReplayingHistory() {
        val input = input("")
        input.setComposingText("draft", 1)
        input.execute(UndoCommand)
        assertEquals("", text(input))
        assertNull(input.composition)
        input.execute(RedoCommand)
        assertEquals("draft", text(input))
        assertNull(input.composition)
    }

    private fun input(text: String) = EditorInputSession(EditorEngine(EditorDocument(PieceTableBuffer(text))))
    private fun text(input: EditorInputSession) = input.buffer.getText(0, input.buffer.length).toString()
}
