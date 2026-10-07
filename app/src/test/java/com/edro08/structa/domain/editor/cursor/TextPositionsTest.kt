package com.edro08.structa.domain.editor.cursor

import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.buffer.TextBuffer
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TextPositionsTest {
    @Test
    fun mapsLineBoundariesEmptyLinesAndEof() {
        val buffer = PieceTableBuffer("ab\n\nx\n")
        val positions = listOf(
            LineColumn(0, 0), LineColumn(0, 1), LineColumn(0, 2),
            LineColumn(1, 0), LineColumn(2, 0), LineColumn(2, 1), LineColumn(3, 0)
        )
        positions.forEachIndexed { offset, position ->
            assertEquals(position, buffer.toLineColumn(TextOffset(offset)))
            assertEquals(TextOffset(offset), buffer.toOffset(position))
        }
    }

    @Test
    fun emptyDocumentHasOneInsertionPosition() {
        val buffer = PieceTableBuffer()
        assertEquals(LineColumn(0, 0), buffer.toLineColumn(TextOffset(0)))
        assertEquals(TextOffset(0), buffer.toOffset(LineColumn(0, 0)))
        assertThrows(IndexOutOfBoundsException::class.java) { buffer.toOffset(LineColumn(0, 1)) }
        assertThrows(IndexOutOfBoundsException::class.java) { buffer.toLineColumn(TextOffset(1)) }
    }

    @Test
    fun columnsCountUtf16IncludingCrTabsAndSurrogateHalves() {
        val buffer = PieceTableBuffer("\t\uD83D\uDE00e\u0301\r\nZ")
        for (column in 0..6) {
            assertEquals(LineColumn(0, column), buffer.toLineColumn(TextOffset(column)))
            assertEquals(TextOffset(column), buffer.toOffset(LineColumn(0, column)))
        }
        assertEquals(LineColumn(1, 0), buffer.toLineColumn(TextOffset(7)))
        assertEquals(LineColumn(1, 1), buffer.toLineColumn(TextOffset(8)))
    }

    @Test
    fun rejectsInvalidPositionsInsteadOfClampingOrOverflowing() {
        val buffer = PieceTableBuffer("ab\nc")
        for (position in listOf(LineColumn(0, 3), LineColumn(1, 2), LineColumn(2, 0),
            LineColumn(1, Int.MAX_VALUE), LineColumn(Int.MAX_VALUE, 0))) {
            assertThrows(IndexOutOfBoundsException::class.java) { buffer.toOffset(position) }
        }
        assertThrows(IndexOutOfBoundsException::class.java) { buffer.toLineColumn(TextOffset(5)) }
        assertThrows(IndexOutOfBoundsException::class.java) { buffer.toLineColumn(TextOffset(Int.MAX_VALUE)) }
        assertThrows(IllegalArgumentException::class.java) { TextOffset(-1) }
        assertThrows(IllegalArgumentException::class.java) { LineColumn(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { LineColumn(0, -1) }
    }

    @Test
    fun conversionsUseIndexWithoutReadingText() {
        val indexed = PieceTableBuffer("ab\ncd")
        val buffer = object : TextBuffer by indexed {
            override fun charAt(offset: Int): Char = error("Must use line index")
            override fun getText(start: Int, end: Int): CharSequence = error("Must use line index")
            override fun getLine(line: Int): CharSequence = error("Must use line index")
        }
        assertEquals(LineColumn(1, 1), buffer.toLineColumn(TextOffset(4)))
        assertEquals(TextOffset(4), buffer.toOffset(LineColumn(1, 1)))
    }

    @Test
    fun conversionsMatchReferenceAfterRandomEdits() {
        val random = Random(1408)
        val reference = StringBuilder("a\n\nb\r\n")
        val buffer = PieceTableBuffer(reference)
        repeat(500) { step ->
            val start = random.nextInt(reference.length + 1)
            val end = random.nextInt(start, reference.length + 1)
            val inserted = buildString {
                repeat(random.nextInt(10)) { append("ab\r\n\t\uD83D\uDE00".random(random)) }
            }
            reference.replace(start, end, inserted)
            buffer.replace(start, end, inserted)
            var line = 0
            var column = 0
            for (offset in 0..reference.length) {
                val position = LineColumn(line, column)
                assertEquals("Step $step, offset $offset", position, buffer.toLineColumn(TextOffset(offset)))
                assertEquals("Step $step, position $position", TextOffset(offset), buffer.toOffset(position))
                if (offset < reference.length) {
                    if (reference[offset] == '\n') { line++; column = 0 } else column++
                }
            }
        }
    }
}
