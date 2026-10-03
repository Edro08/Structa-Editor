package com.edro08.structa.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.command.*
import com.edro08.structa.domain.editor.document.EditorDocument
import com.edro08.structa.ui.editor.input.EditorAction
import com.edro08.structa.ui.editor.input.EditorInputSession
import com.edro08.structa.ui.editor.view.StructaEditorView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorInputIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: StructaEditorView
    private lateinit var input: EditorInputSession

    private fun launch(text: String = "") {
        input = EditorInputSession(EditorEngine(EditorDocument(PieceTableBuffer(text))))
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                StructaEditorView(context).also {
                    view = it
                    it.editable = true
                    it.bind(input.engine, inputSession = input)
                }
            })
        }
        compose.runOnIdle { view.requestFocus() }
    }

    private fun connection(): InputConnection = view.onCreateInputConnection(EditorInfo())!!
    private fun text() = input.buffer.getText(0, input.buffer.length).toString()
    private fun key(code: Int, modifiers: Int = 0): Boolean = view.dispatchKeyEvent(
        KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, modifiers))

    @Test
    fun imeComposesCommitsAndUndoesOnceEvenAfterViewUpdate() {
        launch("ab")
        compose.runOnIdle {
            input.setSelection(1, 1)
            val info = EditorInfo()
            val ime = view.onCreateInputConnection(info)!!
            assertEquals(1, info.initialSelStart)
            assertTrue(ime.beginBatchEdit())
            assertTrue(ime.setComposingText("h", 1))
            assertTrue(ime.setComposingText("hello", 1))
            ime.endBatchEdit()
            view.bind(input.engine, version = 1L, inputSession = input)
            assertTrue(input.engine.isInTransaction)
            assertTrue(ime.commitText("hello", 1))
            assertEquals("ahellob", text())
            assertTrue(key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON))
            assertEquals("ab", text())
            assertFalse(input.canUndo)
            key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            assertEquals("ahellob", text())
            ime.closeConnection()
        }
    }

    @Test
    fun connectionQueriesSelectionAndDeletesCodePoints() {
        launch("a\uD83D\uDE00|\uD83D\uDE03z")
        compose.runOnIdle {
            val ime = connection()
            assertTrue(ime.setSelection(4, 3))
            assertEquals("a\uD83D\uDE00", ime.getTextBeforeCursor(100, 0).toString())
            assertEquals("\uD83D\uDE03z", ime.getTextAfterCursor(100, 0).toString())
            assertEquals("|", ime.getSelectedText(0).toString())
            val surrounding = ime.getSurroundingText(2, 2, 0)!!
            assertEquals("\uD83D\uDE00|\uD83D\uDE03", surrounding.text.toString())
            assertEquals(1, surrounding.offset)
            assertEquals(3, surrounding.selectionStart)
            assertEquals(2, surrounding.selectionEnd)
            assertTrue(ime.deleteSurroundingTextInCodePoints(1, 1))
            assertEquals("a|z", text())
            assertFalse(ime.setSelection(-1, 0))
            input.execute(UndoCommand)
            assertEquals("a\uD83D\uDE00|\uD83D\uDE03z", text())
        }
    }

    @Test
    fun closedAndReplacedConnectionsCannotModifyDocuments() {
        launch()
        compose.runOnIdle {
            val old = connection()
            old.beginBatchEdit()
            old.setComposingText("draft", 1)
            val next = connection()
            assertFalse(input.engine.isInTransaction)
            assertFalse(old.commitText("stale", 1))
            assertTrue(next.commitText("!", 1))
            assertEquals("draft!", text())
            val other = EditorInputSession(EditorEngine(EditorDocument(PieceTableBuffer("other"))))
            view.bind(other.engine, inputSession = other)
            assertFalse(next.commitText("stale", 1))
            assertEquals("other", other.buffer.getText(0, other.buffer.length).toString())
            assertEquals("draft!", text())
        }
    }

    @Test
    fun hardwareTypingSelectionAndNavigationUseCommands() {
        launch()
        compose.runOnIdle {
            assertTrue(key(KeyEvent.KEYCODE_A))
            assertTrue(key(KeyEvent.KEYCODE_ENTER))
            assertTrue(key(KeyEvent.KEYCODE_B))
            assertEquals("a\nb", text())
            key(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_SHIFT_ON)
            assertEquals("b", input.selectedText())
            key(KeyEvent.KEYCODE_DEL)
            assertEquals("a\n", text())
            key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
            assertEquals("a\nb", text())
            key(KeyEvent.KEYCODE_MOVE_HOME)
            assertEquals(2, input.active)
            key(KeyEvent.KEYCODE_DPAD_UP)
            assertEquals(0, input.active)
            key(KeyEvent.KEYCODE_FORWARD_DEL)
            assertEquals("\nb", text())
        }
    }

    @Test
    fun clipboardCutPasteAndUndoPreserveContent() {
        launch("hello")
        compose.runOnIdle {
            val clipboard = view.context.getSystemService(ClipboardManager::class.java)
            key(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
            assertTrue(view.performEditorContextAction(android.R.id.copy))
            assertEquals("hello", clipboard.primaryClip!!.getItemAt(0).text.toString())
            key(KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON)
            assertEquals("", text())
            clipboard.setPrimaryClip(ClipData.newPlainText("test", "line1\nline2"))
            key(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)
            assertEquals("line1\nline2", text())
            input.execute(UndoCommand)
            input.execute(UndoCommand)
            assertEquals("hello", text())
        }
    }

    @Test
    fun productivityShortcutsDispatchActionsWithoutEditing() {
        launch("abc")
        compose.runOnIdle {
            val actions = mutableListOf<EditorAction>()
            view.onEditorAction = { actions.add(it) }
            for (code in listOf(KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_P)) {
                assertTrue(key(code, KeyEvent.META_CTRL_ON))
            }
            key(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            assertEquals(EditorAction.entries.toList(), actions)
            assertEquals("abc", text())
            assertFalse(input.canUndo)
        }
    }

    @Test
    fun touchPlacesCursorDoubleTapSelectsWordAndDragExtendsIt() {
        launch("hello world")
        compose.runOnIdle {
            val time = SystemClock.uptimeMillis()
            fun send(action: Int, elapsed: Long, x: Float = 1f) {
                val event = MotionEvent.obtain(time, time + elapsed, action, x, 1f, 0)
                try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            input.setSelection(11, 11)
            send(MotionEvent.ACTION_DOWN, 0)
            send(MotionEvent.ACTION_UP, 40)
            assertEquals(0, input.active)
            send(MotionEvent.ACTION_DOWN, 100)
            assertEquals("hello", input.selectedText())
            send(MotionEvent.ACTION_MOVE, 120, view.width - 1f)
            send(MotionEvent.ACTION_UP, 140, view.width - 1f)
            assertEquals("hello world", input.selectedText())
            assertFalse(input.canUndo)
        }
    }

    @Test
    fun tappingEditorOpensSystemIme() {
        launch("abc")
        compose.runOnIdle {
            val time = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(time, time + action * 30, action, 1f, 1f, 0)
                try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            var visible = false
            compose.runOnIdle { visible = view.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
            visible
        }
    }
}
