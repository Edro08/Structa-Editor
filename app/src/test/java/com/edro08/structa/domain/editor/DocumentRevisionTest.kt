package com.edro08.structa.domain.editor

import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.command.*
import com.edro08.structa.domain.editor.document.EditorDocument
import org.junit.Assert.*
import org.junit.Test

class DocumentRevisionTest {
    @Test fun savedCheckpointSurvivesUndoRedoAndDivergentHistory() {
        val document = EditorDocument(PieceTableBuffer(""))
        val engine = EditorEngine(document)
        assertFalse(document.dirty)
        engine.execute(InsertTextCommand("a"))
        val saved = document.revision
        document.markSaved()
        engine.execute(InsertTextCommand("b"))
        assertTrue(document.dirty)
        engine.execute(UndoCommand)
        assertEquals(saved, document.revision)
        assertFalse(document.dirty)
        engine.execute(UndoCommand)
        assertTrue(document.dirty)
        engine.execute(RedoCommand)
        assertFalse(document.dirty)
        engine.execute(UndoCommand)
        engine.execute(InsertTextCommand("other branch"))
        assertNotEquals(saved, document.revision)
        assertTrue(document.dirty)
        assertFalse(engine.canRedo)
    }

    @Test fun savingAnOlderSnapshotDoesNotClearNewerEdits() {
        val document = EditorDocument(PieceTableBuffer(""))
        val engine = EditorEngine(document)
        engine.execute(InsertTextCommand("a"))
        val saving = document.revision
        engine.execute(InsertTextCommand("b"))
        document.markSaved(saving)
        assertTrue(document.dirty)
        engine.execute(UndoCommand)
        assertFalse(document.dirty)
    }
}
