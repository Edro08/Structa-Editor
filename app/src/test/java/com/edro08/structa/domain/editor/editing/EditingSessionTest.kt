package com.edro08.structa.domain.editor.editing

import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.buffer.TextBuffer
import com.edro08.structa.domain.editor.cursor.Cursor
import com.edro08.structa.domain.editor.cursor.Selection
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EditingSessionTest {
    @Test
    fun insertionTracksCursorAtStartMiddleAndEnd() {
        val buffer = PieceTableBuffer("abcd")
        val session = EditingSession(buffer)
        session.insertText("!")
        assertState(buffer, session, "!abcd", 1)
        session.moveTo(TextOffset(3))
        session.insertText("12")
        assertState(buffer, session, "!ab12cd", 5)
        session.moveTo(TextOffset(buffer.length))
        session.insertText("\n")
        assertState(buffer, session, "!ab12cd\n", 8)
        assertEquals(2, buffer.lineCount)
    }

    @Test
    fun insertionReplacesSelectionInBothDirectionsAcrossPiecesAndLines() {
        for ((anchor, active) in listOf(1 to 7, 7 to 1)) {
            val buffer = PieceTableBuffer("abcd")
            val session = EditingSession(buffer)
            session.moveTo(TextOffset(2))
            session.insertText("12\n34")
            session.select(TextOffset(anchor), TextOffset(active))
            session.insertText("X\nY")
            assertState(buffer, session, "aX\nYcd", 4)
            assertEquals(2, buffer.lineCount)
            assertEquals(3, buffer.getLineStart(1))
        }
    }

    @Test
    fun backspaceAndDeleteRemoveSelectionBeforeAdjacentCharacters() {
        for (backward in listOf(true, false)) {
            for ((anchor, active) in listOf(1 to 4, 4 to 1)) {
                val buffer = PieceTableBuffer("ab\ncd")
                val session = EditingSession(buffer)
                session.select(TextOffset(anchor), TextOffset(active))
                if (backward) session.deleteBackward() else session.deleteForward()
                assertState(buffer, session, "ad", 1)
                assertEquals(1, buffer.lineCount)
            }
        }
    }

    @Test
    fun deletesAdjacentCodeUnitsAndJoinsLines() {
        val buffer = PieceTableBuffer("ab\ncd")
        val session = EditingSession(buffer)
        session.moveTo(TextOffset(3))
        session.deleteBackward()
        assertState(buffer, session, "abcd", 2)
        assertEquals(1, buffer.lineCount)
        session.deleteForward()
        assertState(buffer, session, "abd", 2)
        session.insertText("\n")
        session.moveLeft()
        session.deleteForward()
        assertState(buffer, session, "abd", 2)
        assertEquals(1, buffer.lineCount)
    }

    @Test
    fun emptyDocumentAndDocumentBoundariesAreSafe() {
        val buffer = PieceTableBuffer()
        val session = EditingSession(buffer)
        session.deleteBackward()
        session.deleteForward()
        session.moveLeft()
        session.moveRight()
        session.moveHome()
        session.moveEnd()
        session.moveLeft(extendSelection = true)
        session.moveRight(extendSelection = true)
        session.insertText("")
        assertState(buffer, session, "", 0)
        session.insertText("a")
        session.deleteForward()
        session.moveRight()
        assertState(buffer, session, "a", 1)
        session.moveLeft()
        session.deleteBackward()
        session.moveLeft()
        assertState(buffer, session, "a", 0)
        session.deleteForward()
        assertState(buffer, session, "", 0)
    }

    @Test
    fun explicitReplacementSetsCursorAfterInsertedTextAndClearsSelection() {
        val buffer = PieceTableBuffer("abcdef")
        val session = EditingSession(buffer)
        session.select(TextOffset(5), TextOffset(6))
        session.replace(TextRange(TextOffset(1), TextOffset(3)), "XYZ")
        assertState(buffer, session, "aXYZdef", 4)
        session.replace(TextRange(TextOffset(0), TextOffset(buffer.length)), "")
        assertState(buffer, session, "", 0)
    }

    @Test
    fun emptyInsertionDeletesSelectedText() {
        val buffer = PieceTableBuffer("abc")
        val session = EditingSession(buffer)
        session.select(TextOffset(3), TextOffset(0))
        session.insertText("")
        assertState(buffer, session, "", 0)
    }

    @Test
    fun leftAndRightCollapseSelectionsWithoutExtraMovement() {
        for ((anchor, active) in listOf(1 to 4, 4 to 1)) {
            val buffer = PieceTableBuffer("abcde")
            val session = EditingSession(buffer)
            session.select(TextOffset(anchor), TextOffset(active))
            session.moveLeft()
            assertState(buffer, session, "abcde", 1)
            session.select(TextOffset(anchor), TextOffset(active))
            session.moveRight()
            assertState(buffer, session, "abcde", 4)
        }
    }

    @Test
    fun selectionExtensionKeepsAnchorWhenCrossingIt() {
        val buffer = PieceTableBuffer("abcd")
        val session = EditingSession(buffer)
        session.moveTo(TextOffset(2))
        session.moveRight(extendSelection = true)
        assertState(buffer, session, "abcd", 3, Selection(TextOffset(2), TextOffset(3)))
        session.moveLeft(extendSelection = true)
        assertState(buffer, session, "abcd", 2)
        session.moveLeft(extendSelection = true)
        assertState(buffer, session, "abcd", 1, Selection(TextOffset(2), TextOffset(1)))
        session.moveTo(TextOffset(4), extendSelection = true)
        session.moveRight(extendSelection = true)
        assertState(buffer, session, "abcd", 4, Selection(TextOffset(2), TextOffset(4)))
        session.clearSelection()
        assertState(buffer, session, "abcd", 4)
    }

    @Test
    fun homeAndEndUseActiveLineIncludingEmptyAndFinalLines() {
        val buffer = PieceTableBuffer("ab\n\ncd\n")
        val session = EditingSession(buffer)
        val bounds = listOf(0 to 2, 0 to 2, 0 to 2, 3 to 3, 4 to 6, 4 to 6, 4 to 6, 7 to 7)
        bounds.forEachIndexed { offset, (start, end) ->
            session.moveTo(TextOffset(offset))
            session.moveHome()
            assertEquals(Cursor(TextOffset(start)), session.cursor)
            session.moveTo(TextOffset(offset))
            session.moveEnd()
            assertEquals(Cursor(TextOffset(end)), session.cursor)
        }
        session.select(TextOffset(1), TextOffset(5))
        session.moveHome()
        assertState(buffer, session, "ab\n\ncd\n", 4)
        session.select(TextOffset(5), TextOffset(1))
        session.moveEnd()
        assertState(buffer, session, "ab\n\ncd\n", 2)
    }

    @Test
    fun homeAndEndCanExtendSelection() {
        val buffer = PieceTableBuffer("abc\ndef")
        val session = EditingSession(buffer)
        session.moveTo(TextOffset(5))
        session.moveHome(extendSelection = true)
        assertState(buffer, session, "abc\ndef", 4, Selection(TextOffset(5), TextOffset(4)))
        session.moveEnd(extendSelection = true)
        assertState(buffer, session, "abc\ndef", 7, Selection(TextOffset(5), TextOffset(7)))
    }

    @Test
    fun explicitSelectionAndMovementMaintainActiveEndInvariant() {
        val buffer = PieceTableBuffer("abc")
        val session = EditingSession(buffer)
        session.select(TextOffset(3), TextOffset(1))
        assertState(buffer, session, "abc", 1, Selection(TextOffset(3), TextOffset(1)))
        session.clearSelection()
        assertState(buffer, session, "abc", 1)
        session.select(TextOffset(2), TextOffset(2))
        assertState(buffer, session, "abc", 2)
        session.select(TextOffset(0), TextOffset(3))
        session.moveTo(TextOffset(1))
        assertState(buffer, session, "abc", 1)
    }

    @Test
    fun rejectsInvalidRequestsWithoutChangingTextCursorOrSelection() {
        val buffer = PieceTableBuffer("abc")
        val session = EditingSession(buffer)
        val selected = Selection(TextOffset(2), TextOffset(1))
        session.select(selected.anchor, selected.active)
        val invalid = listOf<() -> Unit>(
            { session.moveTo(TextOffset(4)) },
            { session.moveTo(TextOffset(4), extendSelection = true) },
            { session.select(TextOffset(4), TextOffset(0)) },
            { session.select(TextOffset(0), TextOffset(4)) },
            { session.replace(TextRange(TextOffset(0), TextOffset(4)), "x") },
            { session.replace(TextRange(TextOffset(4), TextOffset(4)), "") }
        )
        for (request in invalid) {
            assertThrows(IndexOutOfBoundsException::class.java) { request() }
            assertState(buffer, session, "abc", 1, selected)
        }
    }

    @Test
    fun failedBufferEditPreservesCursorAndSelection() {
        val original = PieceTableBuffer("abc")
        val buffer = object : TextBuffer by original {
            override fun replace(start: Int, end: Int, text: CharSequence) {
                throw IllegalStateException("Edit rejected")
            }
        }
        val session = EditingSession(buffer)
        val selected = Selection(TextOffset(2), TextOffset(1))
        session.select(selected.anchor, selected.active)
        assertThrows(IllegalStateException::class.java) { session.insertText("x") }
        assertState(buffer, session, "abc", 1, selected)
    }

    @Test
    fun movementAndDeletionUseUtf16UnitsAndPreserveCrLfContract() {
        val buffer = PieceTableBuffer("\uD83D\uDE00\r\nx")
        val session = EditingSession(buffer)
        session.moveRight()
        assertEquals(Cursor(TextOffset(1)), session.cursor)
        session.moveRight()
        session.moveLeft()
        assertEquals(Cursor(TextOffset(1)), session.cursor)
        session.moveEnd()
        assertEquals(Cursor(TextOffset(3)), session.cursor)
        session.deleteForward()
        assertState(buffer, session, "\uD83D\uDE00\rx", 3)
        session.moveTo(TextOffset(2))
        session.deleteBackward()
        assertState(buffer, session, "\uD83D\rx", 1)
    }

    private fun assertState(
        buffer: TextBuffer,
        session: EditingSession,
        text: String,
        cursor: Int,
        selection: Selection? = null
    ) {
        assertEquals(text, buffer.getText(0, buffer.length).toString())
        assertEquals(text.length, buffer.length)
        assertEquals(Cursor(TextOffset(cursor)), session.cursor)
        assertEquals(selection, session.selection)
        if (selection == null) assertNull(session.selection)
        else assertEquals(selection.active, session.cursor.offset)
    }
}
