package com.edro08.structa.domain.editor.cursor

/** The anchor stays fixed while the active end follows the user's cursor. */
data class Selection(val anchor: TextOffset, val active: TextOffset) {
    val start: TextOffset get() = minOf(anchor, active)
    val end: TextOffset get() = maxOf(anchor, active)
    val range: TextRange get() = TextRange(start, end)
    val isEmpty: Boolean get() = anchor == active

    /** Clears the selected span at its active end, including for backward selections. */
    fun collapse(): Selection = Selection(active, active)
}
