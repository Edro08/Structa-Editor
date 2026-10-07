package com.edro08.structa.domain.editor.history

import com.edro08.structa.domain.editor.buffer.TextBuffer

/**
 * Single-threaded operation history owned by a document (or a standalone session). Transactions are
 * explicit, non-nested, and must be committed before undo/redo. Each successful
 * content change invalidates redo; movement and no-op edits do not.
 */
class EditHistory internal constructor(private val buffer: TextBuffer) {
    private val undoStack = ArrayDeque<EditTransaction>()
    private val redoStack = ArrayDeque<EditTransaction>()
    private var pending: MutableList<EditOperation>? = null
    private var transactionStart: EditState? = null
    private var transactionRevision = 0L
    private var nextRevision = 0L
    var revision: Long = 0L
        private set

    val isInTransaction: Boolean get() = pending != null
    val canUndo: Boolean get() = !isInTransaction && undoStack.isNotEmpty()
    val canRedo: Boolean get() = !isInTransaction && redoStack.isNotEmpty()

    internal fun apply(operation: EditOperation, before: EditState, after: EditState) {
        if (operation.removedText == operation.insertedText) return
        operation.applyTo(buffer)
        val beforeRevision = revision
        revision = ++nextRevision
        val operations = pending
        if (operations == null) {
            undoStack.addLast(EditTransaction(listOf(operation), before, after, beforeRevision, revision))
        } else {
            operations.add(operation)
        }
        redoStack.clear()
    }

    internal fun beginTransaction(before: EditState) {
        check(!isInTransaction) { "Nested transactions are not supported" }
        transactionStart = before
        transactionRevision = revision
        pending = mutableListOf()
    }

    internal fun commitTransaction(after: EditState) {
        val operations = checkNotNull(pending) { "No active transaction" }
        if (operations.isNotEmpty()) {
            undoStack.addLast(EditTransaction(operations, checkNotNull(transactionStart), after, transactionRevision, revision))
        }
        pending = null
        transactionStart = null
    }

    internal fun undo(): EditState? {
        check(!isInTransaction) { "Commit the transaction before undo" }
        val transaction = undoStack.lastOrNull() ?: return null
        replay(transaction.operations.asReversed(), inverse = true)
        undoStack.removeLast()
        redoStack.addLast(transaction)
        revision = transaction.beforeRevision
        return transaction.before
    }

    internal fun redo(): EditState? {
        check(!isInTransaction) { "Commit the transaction before redo" }
        val transaction = redoStack.lastOrNull() ?: return null
        replay(transaction.operations, inverse = false)
        redoStack.removeLast()
        undoStack.addLast(transaction)
        revision = transaction.afterRevision
        return transaction.after
    }

    private fun replay(operations: List<EditOperation>, inverse: Boolean) {
        var applied = 0
        try {
            for (operation in operations) {
                operation.applyTo(buffer, inverse)
                applied++
            }
        } catch (failure: Exception) {
            // Restore already-replayed operations if a later operation is rejected.
            try {
                for (index in applied - 1 downTo 0) operations[index].applyTo(buffer, !inverse)
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }
}
