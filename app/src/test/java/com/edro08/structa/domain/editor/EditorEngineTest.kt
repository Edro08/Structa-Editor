package com.edro08.structa.domain.editor

import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.command.*
import com.edro08.structa.domain.editor.cursor.Cursor
import com.edro08.structa.domain.editor.cursor.Selection
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange
import com.edro08.structa.domain.editor.document.EditorDocument
import com.edro08.structa.domain.document.DocumentId
import org.junit.Assert.*
import org.junit.Test

class EditorEngineTest {
    @Test
    fun firstMilestoneRunsEntirelyThroughCommands() {
        val buffer = PieceTableBuffer("fun main() {\n}")
        val document = EditorDocument(buffer)
        val editor = EditorEngine(document)
        editor.execute(InsertTextCommand("hello"))
        assertState(editor, "hellofun main() {\n}", 5)
        assertTrue(document.history.canUndo)
        editor.execute(UndoCommand)
        assertState(editor, "fun main() {\n}", 0)
        assertFalse(editor.canUndo)
        assertTrue(editor.canRedo)
        editor.execute(RedoCommand)
        assertState(editor, "hellofun main() {\n}", 5)
        assertSame(buffer, document.buffer)
    }

    @Test
    fun selectionReplacementUndoAndRedoRestoreDirectionalState() {
        val editor = engine("ab\ncd")
        val selection = Selection(TextOffset(4), TextOffset(1))
        editor.execute(SetSelectionCommand(selection.anchor, selection.active))
        editor.execute(InsertTextCommand("X\nY"))
        assertState(editor, "aX\nYd", 4)
        editor.execute(UndoCommand)
        assertState(editor, "ab\ncd", 1, selection)
        editor.execute(RedoCommand)
        assertState(editor, "aX\nYd", 4)
    }

    @Test
    fun deleteAndExplicitReplaceCommandsShareTheSameHistory() {
        val editor = engine("ab\ncd")
        editor.execute(MoveCursorCommand(TextOffset(3)))
        editor.execute(DeleteBackwardCommand)
        assertState(editor, "abcd", 2)
        editor.execute(DeleteForwardCommand)
        assertState(editor, "abd", 2)
        editor.execute(ReplaceTextCommand(TextRange(TextOffset(0), TextOffset(2)), "!"))
        assertState(editor, "!d", 1)
        repeat(3) { editor.execute(UndoCommand) }
        assertState(editor, "ab\ncd", 3)
        repeat(3) { editor.execute(RedoCommand) }
        assertState(editor, "!d", 1)
    }

    @Test
    fun movementAndSelectionCommandsDoNotCreateHistory() {
        val editor = engine("abc\ndef")
        editor.execute(MoveCursorRightCommand)
        editor.execute(MoveCursorEndCommand)
        assertState(editor, "abc\ndef", 3)
        editor.execute(MoveCursorLeftCommand)
        editor.execute(MoveCursorHomeCommand)
        assertState(editor, "abc\ndef", 0)
        editor.execute(SelectRightCommand)
        assertState(editor, "abc\ndef", 1, Selection(TextOffset(0), TextOffset(1)))
        editor.execute(SelectLeftCommand)
        assertState(editor, "abc\ndef", 0)
        editor.execute(MoveCursorCommand(TextOffset(5)))
        editor.execute(SelectHomeCommand)
        assertState(editor, "abc\ndef", 4, Selection(TextOffset(5), TextOffset(4)))
        editor.execute(SelectEndCommand)
        assertState(editor, "abc\ndef", 7, Selection(TextOffset(5), TextOffset(7)))
        editor.execute(ClearSelectionCommand)
        assertState(editor, "abc\ndef", 7)
        editor.execute(MoveCursorCommand(TextOffset(0), extendSelection = true))
        assertState(editor, "abc\ndef", 0, Selection(TextOffset(7), TextOffset(0)))
        assertFalse(editor.canUndo)
        assertFalse(editor.canRedo)
    }

    @Test
    fun transactionCommandsGroupEditsAndRejectPrematureUndo() {
        val editor = engine("")
        editor.execute(BeginTransactionCommand)
        editor.execute(InsertTextCommand("hel"))
        editor.execute(InsertTextCommand("lo"))
        assertTrue(editor.isInTransaction)
        assertTrue(editor.document.history.isInTransaction)
        assertFalse(editor.canUndo)
        assertThrows(IllegalStateException::class.java) { editor.execute(UndoCommand) }
        editor.execute(CommitTransactionCommand)
        assertFalse(editor.isInTransaction)
        editor.execute(UndoCommand)
        assertState(editor, "", 0)
        assertFalse(editor.canUndo)
        editor.execute(RedoCommand)
        assertState(editor, "hello", 5)
    }

    @Test
    fun commandsCanBeReusedAcrossIndependentDocuments() {
        val first = engine("a")
        val second = engine("b")
        val insert = InsertTextCommand("X")
        first.execute(insert)
        second.execute(insert)
        first.execute(UndoCommand)
        assertState(first, "a", 0)
        assertState(second, "Xb", 1)
        assertTrue(first.canRedo)
        assertTrue(second.canUndo)
        assertNotEquals(first.document.id, second.document.id)
        assertNotSame(first.document.history, second.document.history)
        val id = DocumentId("in-memory-document")
        assertEquals(id, EditorDocument(PieceTableBuffer(), id).id)
    }

    @Test
    fun invalidCommandPreservesRedoAndSelection() {
        val editor = engine("abc")
        editor.execute(InsertTextCommand("X"))
        editor.execute(UndoCommand)
        val selection = Selection(TextOffset(2), TextOffset(1))
        editor.execute(SetSelectionCommand(selection.anchor, selection.active))
        assertThrows(IndexOutOfBoundsException::class.java) {
            editor.execute(ReplaceTextCommand(TextRange(TextOffset(0), TextOffset(99)), "oops"))
        }
        assertState(editor, "abc", 1, selection)
        assertTrue(editor.canRedo)
        assertFalse(editor.canUndo)
        editor.execute(InsertTextCommand("!"))
        assertState(editor, "a!c", 2)
        assertFalse(editor.canRedo)
    }

    @Test
    fun preventsCompetingEnginesWithStaleCursorPositions() {
        val document = EditorDocument(PieceTableBuffer("abc"))
        val editor = EditorEngine(document)
        editor.execute(MoveCursorCommand(TextOffset(3)))
        assertThrows(IllegalStateException::class.java) { EditorEngine(document) }
        editor.execute(InsertTextCommand("!"))
        assertState(editor, "abc!", 4)
    }

    private fun engine(text: String) = EditorEngine(EditorDocument(PieceTableBuffer(text)))

    private fun assertState(editor: EditorEngine, text: String, cursor: Int, selection: Selection? = null) {
        val buffer = editor.document.buffer
        assertEquals(text, buffer.getText(0, buffer.length).toString())
        assertEquals(Cursor(TextOffset(cursor)), editor.cursor)
        assertEquals(selection, editor.selection)
        val lines = text.split('\n')
        assertEquals(lines.size, buffer.lineCount)
        var offset = 0
        lines.forEachIndexed { line, content ->
            assertEquals(offset, buffer.getLineStart(line))
            assertEquals(offset + content.length, buffer.getLineEnd(line))
            assertEquals(content, buffer.getLine(line).toString())
            for (position in offset..offset + content.length) {
                assertEquals(line, buffer.getLineForOffset(position))
            }
            offset += content.length + 1
        }
    }
}
