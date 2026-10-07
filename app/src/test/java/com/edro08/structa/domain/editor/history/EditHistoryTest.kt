package com.edro08.structa.domain.editor.history

import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.buffer.TextBuffer
import com.edro08.structa.domain.editor.cursor.Selection
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.editing.EditingSession
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class EditHistoryTest {
    @Test
    fun undoAndRedoInsertionRestoreTextAndCursor() {
        val buffer = PieceTableBuffer("ab")
        val editor = EditingSession(buffer)
        editor.moveTo(TextOffset(1))
        editor.insertText("\nx")
        assertState(buffer, editor, "a\nxb", 3)
        assertTrue(editor.canUndo)
        editor.undo()
        assertState(buffer, editor, "ab", 1)
        assertFalse(editor.canUndo)
        assertTrue(editor.canRedo)
        editor.redo()
        assertState(buffer, editor, "a\nxb", 3)
        assertFalse(editor.canRedo)
    }

    @Test
    fun undoAndRedoBothDeletionDirections() {
        for (backward in listOf(true, false)) {
            val buffer = PieceTableBuffer("ab\ncd")
            val editor = EditingSession(buffer)
            val offset = if (backward) 3 else 2
            editor.moveTo(TextOffset(offset))
            if (backward) editor.deleteBackward() else editor.deleteForward()
            assertState(buffer, editor, "abcd", 2)
            editor.undo()
            assertState(buffer, editor, "ab\ncd", offset)
            assertEquals(2, buffer.lineCount)
            editor.redo()
            assertState(buffer, editor, "abcd", 2)
            assertEquals(1, buffer.lineCount)
        }
    }

    @Test
    fun undoReplacementRestoresDirectionalSelectionAndRedoClearsIt() {
        for ((anchor, active) in listOf(1 to 4, 4 to 1)) {
            val buffer = PieceTableBuffer("ab\ncd")
            val editor = EditingSession(buffer)
            val selected = Selection(TextOffset(anchor), TextOffset(active))
            editor.select(selected.anchor, selected.active)
            editor.insertText("XYZ")
            editor.undo()
            assertState(buffer, editor, "ab\ncd", active, selected)
            editor.redo()
            assertState(buffer, editor, "aXYZd", 4)
        }
    }

    @Test
    fun undoSelectionDeletionRestoresItsDirection() {
        val buffer = PieceTableBuffer("abc")
        val editor = EditingSession(buffer)
        val selected = Selection(TextOffset(3), TextOffset(0))
        editor.select(selected.anchor, selected.active)
        editor.deleteForward()
        editor.undo()
        assertState(buffer, editor, "abc", 0, selected)
        editor.redo()
        assertState(buffer, editor, "", 0)
    }

    @Test
    fun groupedTypingIsOneUndoAction() {
        val buffer = PieceTableBuffer()
        val editor = EditingSession(buffer)
        editor.beginTransaction()
        "hello".forEach { editor.insertText(it.toString()) }
        assertFalse(editor.canUndo)
        assertTrue(editor.isInTransaction)
        editor.commitTransaction()
        editor.undo()
        assertState(buffer, editor, "", 0)
        assertFalse(editor.canUndo)
        editor.redo()
        assertState(buffer, editor, "hello", 5)
    }

    @Test
    fun mixedTransactionReplaysInCorrectOrderWithBoundaryStates() {
        val buffer = PieceTableBuffer("ab\ncd")
        val editor = EditingSession(buffer)
        val before = Selection(TextOffset(4), TextOffset(1))
        editor.select(before.anchor, before.active)
        editor.beginTransaction()
        editor.insertText("XYZ")
        editor.moveTo(TextOffset(0))
        editor.deleteForward()
        editor.moveEnd()
        editor.insertText("\n!")
        editor.select(TextOffset(1), TextOffset(3))
        val after = editor.selection
        editor.commitTransaction()
        editor.undo()
        assertState(buffer, editor, "ab\ncd", 1, before)
        assertFalse(editor.canUndo)
        editor.redo()
        assertState(buffer, editor, "XYZd\n!", 3, after)
    }

    @Test
    fun newEditAfterUndoDiscardsOnlyRedoBranch() {
        val buffer = PieceTableBuffer()
        val editor = EditingSession(buffer)
        editor.insertText("a")
        editor.insertText("b")
        editor.undo()
        editor.insertText("c")
        assertFalse(editor.canRedo)
        editor.redo()
        assertState(buffer, editor, "ac", 2)
        editor.undo()
        editor.undo()
        assertState(buffer, editor, "", 0)
    }

    @Test
    fun emptyAndIdenticalEditsAndMovementPreserveRedo() {
        val buffer = PieceTableBuffer("a")
        val editor = EditingSession(buffer)
        editor.insertText("x")
        editor.undo()
        editor.deleteBackward()
        editor.moveEnd()
        editor.deleteForward()
        editor.insertText("")
        editor.select(TextOffset(0), TextOffset(1))
        editor.insertText("a")
        editor.beginTransaction()
        editor.moveHome()
        editor.commitTransaction()
        assertFalse(editor.canUndo)
        assertTrue(editor.canRedo)
        editor.redo()
        assertState(buffer, editor, "xa", 1)
    }

    @Test
    fun transactionLifecycleRejectsNestedBeginAndReplayWhileOpen() {
        val buffer = PieceTableBuffer()
        val editor = EditingSession(buffer)
        assertThrows(IllegalStateException::class.java) { editor.commitTransaction() }
        editor.beginTransaction()
        assertThrows(IllegalStateException::class.java) { editor.beginTransaction() }
        editor.insertText("x")
        assertThrows(IllegalStateException::class.java) { editor.undo() }
        assertThrows(IllegalStateException::class.java) { editor.redo() }
        assertState(buffer, editor, "x", 1)
        editor.commitTransaction()
        editor.undo()
        assertState(buffer, editor, "", 0)
    }

    @Test
    fun emptyHistoryLeavesCursorAndSelectionUntouched() {
        val buffer = PieceTableBuffer("abc")
        val editor = EditingSession(buffer)
        val selected = Selection(TextOffset(2), TextOffset(1))
        editor.select(selected.anchor, selected.active)
        editor.undo()
        editor.redo()
        assertState(buffer, editor, "abc", 1, selected)
    }

    @Test
    fun failedNewEditPreservesRedoAndState() {
        val original = PieceTableBuffer("a")
        var reject = false
        val buffer = object : TextBuffer by original {
            override fun replace(start: Int, end: Int, text: CharSequence) {
                check(!reject) { "Rejected" }
                original.replace(start, end, text)
            }
        }
        val editor = EditingSession(buffer)
        editor.insertText("x")
        editor.undo()
        reject = true
        assertThrows(IllegalStateException::class.java) { editor.insertText("y") }
        assertState(buffer, editor, "a", 0)
        assertTrue(editor.canRedo)
        assertFalse(editor.canUndo)
        reject = false
        editor.redo()
        assertState(buffer, editor, "xa", 1)
    }

    @Test
    fun failedGroupedReplayRollsBackAndKeepsHistoryForRetry() {
        val original = PieceTableBuffer()
        var callsUntilFailure = -1
        val buffer = object : TextBuffer by original {
            override fun replace(start: Int, end: Int, text: CharSequence) {
                if (callsUntilFailure > 0 && --callsUntilFailure == 0) error("Rejected once")
                original.replace(start, end, text)
            }
        }
        val editor = EditingSession(buffer)
        editor.beginTransaction()
        editor.insertText("a")
        editor.insertText("b")
        editor.commitTransaction()
        callsUntilFailure = 2
        assertThrows(IllegalStateException::class.java) { editor.undo() }
        assertState(buffer, editor, "ab", 2)
        assertTrue(editor.canUndo)
        assertFalse(editor.canRedo)
        editor.undo()
        callsUntilFailure = 2
        assertThrows(IllegalStateException::class.java) { editor.redo() }
        assertState(buffer, editor, "", 0)
        assertTrue(editor.canRedo)
        assertFalse(editor.canUndo)
        editor.redo()
        assertState(buffer, editor, "ab", 2)
    }

    @Test
    fun randomizedHistoryMatchesReferenceSnapshots() {
        // Full snapshots are a test oracle only, never used by production history.
        data class Snapshot(val text: String, val cursor: Int, val selection: Selection?)
        val random = Random(305)
        val buffer = PieceTableBuffer("initial\n")
        val editor = EditingSession(buffer)
        val reference = StringBuilder("initial\n")
        val before = mutableListOf<Snapshot>()
        val after = mutableListOf<Snapshot>()
        repeat(300) {
            val anchor = random.nextInt(reference.length + 1)
            val active = random.nextInt(reference.length + 1)
            editor.select(TextOffset(anchor), TextOffset(active))
            before.add(Snapshot(reference.toString(), active, editor.selection))
            val inserted = "[edit:$it]\n!"
            val start = minOf(anchor, active)
            reference.replace(start, maxOf(anchor, active), inserted)
            editor.insertText(inserted)
            after.add(Snapshot(reference.toString(), start + inserted.length, null))
        }
        before.asReversed().forEach { expected ->
            editor.undo()
            assertState(buffer, editor, expected.text, expected.cursor, expected.selection)
        }
        assertFalse(editor.canUndo)
        after.forEach { expected ->
            editor.redo()
            assertState(buffer, editor, expected.text, expected.cursor, expected.selection)
        }
        assertFalse(editor.canRedo)
    }

    private fun assertState(buffer: TextBuffer, editor: EditingSession, text: String,
        cursor: Int, selection: Selection? = null) {
        assertEquals(text, buffer.getText(0, buffer.length).toString())
        assertEquals(TextOffset(cursor), editor.cursor.offset)
        assertEquals(selection, editor.selection)
        var start = 0
        val lines = text.split('\n')
        assertEquals(lines.size, buffer.lineCount)
        lines.forEachIndexed { index, line ->
            assertEquals(start, buffer.getLineStart(index))
            assertEquals(start + line.length, buffer.getLineEnd(index))
            start += line.length + 1
        }
    }
}
