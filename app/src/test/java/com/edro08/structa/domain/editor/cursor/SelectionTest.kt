package com.edro08.structa.domain.editor.cursor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {
    @Test
    fun forwardAndBackwardSelectionsShareRangeButKeepDirection() {
        val forward = Selection(TextOffset(2), TextOffset(8))
        val backward = Selection(TextOffset(8), TextOffset(2))
        for (selection in listOf(forward, backward)) {
            assertEquals(TextOffset(2), selection.start)
            assertEquals(TextOffset(8), selection.end)
            assertEquals(TextRange(TextOffset(2), TextOffset(8)), selection.range)
            assertEquals(6, selection.range.length)
            assertFalse(selection.isEmpty)
        }
        assertEquals(TextOffset(2), forward.anchor)
        assertEquals(TextOffset(8), forward.active)
        assertEquals(TextOffset(8), backward.anchor)
        assertEquals(TextOffset(2), backward.active)
        assertFalse(forward == backward)
    }

    @Test
    fun activeEndCanCrossAnchorWithoutLosingIt() {
        val selection = Selection(TextOffset(4), TextOffset(7))
        val crossed = selection.copy(active = TextOffset(1))
        assertEquals(TextOffset(4), crossed.anchor)
        assertEquals(TextRange(TextOffset(1), TextOffset(4)), crossed.range)
        assertTrue(crossed.copy(active = crossed.anchor).isEmpty)
    }

    @Test
    fun collapseClearsEitherDirectionAtActiveEnd() {
        for (selection in listOf(Selection(TextOffset(2), TextOffset(8)),
            Selection(TextOffset(8), TextOffset(2)), Selection(TextOffset(0), TextOffset(0)))) {
            val collapsed = selection.collapse()
            assertEquals(selection.active, collapsed.anchor)
            assertEquals(selection.active, collapsed.active)
            assertTrue(collapsed.isEmpty)
            assertTrue(collapsed.range.isEmpty)
            assertEquals(0, collapsed.range.length)
            assertEquals(Cursor(selection.active), Cursor(collapsed.active))
        }
    }

    @Test
    fun rangesMustBeOrderedAndMayBeEmpty() {
        assertThrows(IllegalArgumentException::class.java) { TextRange(TextOffset(8), TextOffset(2)) }
        assertTrue(TextRange(TextOffset(8), TextOffset(8)).isEmpty)
        assertEquals(Int.MAX_VALUE, TextRange(TextOffset(0), TextOffset(Int.MAX_VALUE)).length)
    }
}
