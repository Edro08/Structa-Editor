package com.edro08.structa.domain.editor.decoration

import com.edro08.structa.domain.editor.cursor.TextRange
import com.edro08.structa.domain.editor.syntax.SyntaxStyle

enum class DecorationType { SYNTAX, ERROR, WARNING, SEARCH_MATCH, SELECTED_OCCURRENCE, GIT_CHANGE, BRACKET_MATCH, BREAKPOINT }
data class Decoration(val range: TextRange, val type: DecorationType, val style: SyntaxStyle? = null)

/** Immutable interval index; overlapping layers and zero-width diagnostic markers are supported. */
class DecorationSet(decorations: List<Decoration> = emptyList()) {
    private val items = decorations.sortedBy { it.range.start.value }
    private val maximumEnd = IntArray(items.size)
    init {
        var end = 0
        items.forEachIndexed { index, decoration ->
            end = maxOf(end, decoration.range.end.value); maximumEnd[index] = end
        }
    }
    fun intersecting(start: Int, end: Int): List<Decoration> {
        require(start >= 0 && end >= start)
        var low = 0
        var high = items.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (maximumEnd[middle] < start) low = middle + 1 else high = middle
        }
        val result = mutableListOf<Decoration>()
        while (low < items.size && items[low].range.start.value < end) {
            val item = items[low++]
            if (item.range.end.value > start || (item.range.isEmpty && item.range.start.value >= start)) result += item
        }
        return result
    }
}
