package com.edro08.structa.ui.editor.input

import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.command.*
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange

/** Single input owner per engine. Composition is a provisional range and one history transaction. */
class EditorInputSession(val engine: EditorEngine) {
    var composition: TextRange? = null
        private set
    private var ownsTransaction = false
    private var batchDepth = 0
    private var pending = false
    private var pendingTextChange = false
    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    val buffer get() = engine.document.buffer
    val anchor: Int get() = engine.selection?.anchor?.value ?: engine.cursor.offset.value
    val active: Int get() = engine.cursor.offset.value
    val selectionStart: Int get() = minOf(anchor, active)
    val selectionEnd: Int get() = maxOf(anchor, active)
    val canUndo: Boolean get() = engine.canUndo || ownsTransaction
    val canRedo: Boolean get() = engine.canRedo

    fun addListener(listener: (Boolean) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (Boolean) -> Unit) { listeners.remove(listener) }

    fun beginBatchEdit(): Boolean { batchDepth++; return true }
    fun endBatchEdit(): Boolean {
        if (batchDepth == 0) return false
        batchDepth--
        publish()
        return batchDepth > 0
    }

    private inline fun batch(block: () -> Unit) {
        beginBatchEdit()
        try { block() } finally { endBatchEdit() }
    }

    private fun changed(text: Boolean = false) {
        pending = true
        pendingTextChange = pendingTextChange || text
        publish()
    }

    private fun publish() {
        if (batchDepth > 0 || !pending) return
        val text = pendingTextChange
        pending = false
        pendingTextChange = false
        listeners.toList().forEach { it(text) }
    }

    private fun beginCompositionTransaction() {
        if (!ownsTransaction) {
            engine.execute(BeginTransactionCommand)
            ownsTransaction = true
        }
    }

    private fun replaceInput(text: String, cursorPosition: Int) {
        val range = composition ?: TextRange(TextOffset(selectionStart), TextOffset(selectionEnd))
        engine.execute(ReplaceTextCommand(range, text))
        val newEnd = range.start.value + text.length
        val cursor = if (cursorPosition > 0) newEnd.toLong() + cursorPosition - 1
            else range.start.value.toLong() + cursorPosition
        engine.execute(MoveCursorCommand(TextOffset(cursor.coerceIn(0, buffer.length.toLong()).toInt())))
        composition = TextRange(range.start, TextOffset(newEnd))
        changed(text = true)
    }

    fun setComposingText(text: String, newCursorPosition: Int): Boolean {
        batch {
            beginCompositionTransaction()
            replaceInput(text, newCursorPosition)
            if (text.isEmpty()) finishComposingText()
        }
        return true
    }

    fun commitText(text: String, newCursorPosition: Int = 1): Boolean {
        batch {
            beginCompositionTransaction()
            replaceInput(text, newCursorPosition)
            finishComposingText()
        }
        return true
    }

    fun finishComposingText(): Boolean {
        if (composition == null && !ownsTransaction) return true
        composition = null
        if (ownsTransaction) {
            engine.execute(CommitTransactionCommand)
            ownsTransaction = false
        }
        changed()
        return true
    }

    fun setComposingRegion(start: Int, end: Int): Boolean {
        batch {
            finishComposingText()
            val from = minOf(start, end).coerceIn(0, buffer.length)
            val to = maxOf(start, end).coerceIn(0, buffer.length)
            composition = if (from == to) null else TextRange(TextOffset(from), TextOffset(to))
            changed()
        }
        return true
    }

    fun setSelection(start: Int, end: Int): Boolean {
        if (start !in 0..buffer.length || end !in 0..buffer.length) return false
        batch {
            val composing = composition
            if (composing != null && (minOf(start, end) < composing.start.value || maxOf(start, end) > composing.end.value)) {
                finishComposingText()
            }
            engine.execute(SetSelectionCommand(TextOffset(start), TextOffset(end)))
            changed()
        }
        return true
    }

    /** Non-IME intent (toolbar, touch, clipboard, hardware keyboard). */
    fun execute(command: EditorCommand, changesText: Boolean = true) {
        batch {
            finishComposingText()
            engine.execute(command)
            changed(changesText)
        }
    }

    fun selectAll() = execute(SetSelectionCommand(TextOffset(0), TextOffset(buffer.length)), false)
    fun selectedText(): String = buffer.getText(selectionStart, selectionEnd).toString()

    fun deleteSurroundingText(before: Int, after: Int, codePoints: Boolean = false): Boolean {
        if (before < 0 || after < 0) return false
        val composing = composition
        // Android treats the composing region as part of the protected selection here.
        val start = minOf(selectionStart, composing?.start?.value ?: selectionStart)
        val end = maxOf(selectionEnd, composing?.end?.value ?: selectionEnd)
        val left = if (codePoints) codePointBoundary(start, before, backwards = true) ?: return false
            else (start.toLong() - before).coerceAtLeast(0).toInt()
        val right = if (codePoints) codePointBoundary(end, after, backwards = false) ?: return false
            else (end.toLong() + after).coerceAtMost(buffer.length.toLong()).toInt()
        if (left == start && right == end) return true
        batch {
            val oldAnchor = anchor
            val oldActive = active
            if (composing != null) beginCompositionTransaction() else engine.execute(BeginTransactionCommand)
            try {
                if (right > end) engine.execute(ReplaceTextCommand(TextRange(TextOffset(end), TextOffset(right)), ""))
                if (left < start) engine.execute(ReplaceTextCommand(TextRange(TextOffset(left), TextOffset(start)), ""))
                val removed = start - left
                engine.execute(SetSelectionCommand(TextOffset(oldAnchor - removed), TextOffset(oldActive - removed)))
                if (composing != null) composition = TextRange(TextOffset(composing.start.value - removed), TextOffset(composing.end.value - removed))
            } finally { if (composing == null) engine.execute(CommitTransactionCommand) }
            changed(text = true)
        }
        return true
    }

    private fun codePointBoundary(offset: Int, count: Int, backwards: Boolean): Int? {
        var position = offset
        var remaining = count
        while (remaining > 0 && if (backwards) position > 0 else position < buffer.length) {
            if (backwards) {
                val char = buffer.charAt(--position)
                if (char.isHighSurrogate()) return null
                if (char.isLowSurrogate()) {
                    if (position == 0 || !buffer.charAt(position - 1).isHighSurrogate()) return null
                    position--
                }
            } else {
                val char = buffer.charAt(position++)
                if (char.isLowSurrogate()) return null
                if (char.isHighSurrogate()) {
                    if (position == buffer.length || !buffer.charAt(position).isLowSurrogate()) return null
                    position++
                }
            }
            remaining--
        }
        return position
    }

    /** Closing a keyboard connection commits provisional text and flushes outstanding batches. */
    fun close() {
        finishComposingText()
        batchDepth = 0
        publish()
    }
}
