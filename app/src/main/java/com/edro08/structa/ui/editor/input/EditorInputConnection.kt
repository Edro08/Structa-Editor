package com.edro08.structa.ui.editor.input

import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.SurroundingText
import com.edro08.structa.ui.editor.view.StructaEditorView

/** No Editable/TextField backing store: every mutation is translated into editor commands. */
class EditorInputConnection(
    private val view: StructaEditorView,
    private val input: EditorInputSession
) : BaseInputConnection(view, false) {
    private var closed = false
    private inline fun update(block: () -> Boolean): Boolean = if (closed) false else block()

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
        update { input.commitText(text?.toString().orEmpty(), newCursorPosition) }
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
        update { input.setComposingText(text?.toString().orEmpty(), newCursorPosition) }
    override fun finishComposingText(): Boolean = update { input.finishComposingText() }
    override fun setComposingRegion(start: Int, end: Int): Boolean = update { input.setComposingRegion(start, end) }
    override fun setSelection(start: Int, end: Int): Boolean = update { input.setSelection(start, end) }
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
        update { input.deleteSurroundingText(beforeLength, afterLength) }
    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
        update { input.deleteSurroundingText(beforeLength, afterLength, codePoints = true) }
    override fun beginBatchEdit(): Boolean = update { input.beginBatchEdit() }
    override fun endBatchEdit(): Boolean = update { input.endBatchEdit() }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = if (closed || n <= 0) "" else
        input.buffer.getText((input.selectionStart.toLong() - n).coerceAtLeast(0).toInt(), input.selectionStart)
    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = if (closed || n <= 0) "" else
        input.buffer.getText(input.selectionEnd, (input.selectionEnd.toLong() + n).coerceAtMost(input.buffer.length.toLong()).toInt())
    override fun getSelectedText(flags: Int): CharSequence? = if (closed || input.selectionStart == input.selectionEnd) null else input.selectedText()
    override fun getCursorCapsMode(reqModes: Int): Int = 0

    override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText? {
        if (closed || beforeLength < 0 || afterLength < 0) return null
        val start = (input.selectionStart.toLong() - beforeLength).coerceAtLeast(0).toInt()
        val end = (input.selectionEnd.toLong() + afterLength).coerceAtMost(input.buffer.length.toLong()).toInt()
        return SurroundingText(input.buffer.getText(start, end), input.anchor - start, input.active - start, start)
    }

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
        if (closed) return null
        // Bounded context; fullscreen extract mode is disabled by EditorInfo.
        val start = (input.selectionStart - 2048).coerceAtLeast(0)
        val end = (input.selectionEnd.toLong() + 2048).coerceAtMost(input.buffer.length.toLong()).toInt()
        return ExtractedText().apply {
            text = input.buffer.getText(start, end)
            startOffset = start
            partialStartOffset = -1
            partialEndOffset = -1
            selectionStart = input.anchor - start
            selectionEnd = input.active - start
        }
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean = update { view.dispatchKeyEvent(event) }
    override fun performContextMenuAction(id: Int): Boolean = update { view.performEditorContextAction(id) }
    override fun performEditorAction(actionCode: Int): Boolean = update { input.commitText("\n") }

    override fun closeConnection() {
        if (!closed) {
            closed = true
            input.close()
        }
    }
}
