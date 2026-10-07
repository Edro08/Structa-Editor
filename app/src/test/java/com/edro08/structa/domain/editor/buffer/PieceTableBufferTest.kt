package com.edro08.structa.domain.editor.buffer

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PieceTableBufferTest {
    @Test
    fun insertsAtStartEndAndMiddle() {
        val buffer = PieceTableBuffer("middle")
        buffer.insert(0, "[")
        buffer.insert(buffer.length, "]")
        buffer.insert(4, "\n")
        assertBuffer("[mid\ndle]", buffer)
    }

    @Test
    fun deletesAtStartEndAndMiddle() {
        for ((range, expected) in listOf((0 to 2) to "cdef", (4 to 6) to "abcd", (2 to 4) to "abef")) {
            val buffer = PieceTableBuffer("abcdef")
            buffer.delete(range.first, range.second)
            assertBuffer(expected, buffer)
        }
    }

    @Test
    fun deletionCrossesOriginalAndAddedPieces() {
        val buffer = PieceTableBuffer("abcdef")
        buffer.insert(2, "12")
        buffer.insert(6, "34")
        buffer.insert(0, "!")
        assertBuffer("!ab12cd34ef", buffer)
        buffer.delete(2, 9)
        assertBuffer("!aef", buffer)
        buffer.delete(0, buffer.length)
        assertBuffer("", buffer)
        buffer.insert(0, "reused\n")
        assertBuffer("reused\n", buffer)
    }

    @Test
    fun replacesAcrossPiecesAndReturnsIndependentSlices() {
        val buffer = PieceTableBuffer("abcd")
        buffer.insert(2, "123")
        val slice = buffer.getText(1, 6)
        assertEquals("b123c", slice.toString())
        buffer.replace(1, 6, "x\ny")
        assertBuffer("ax\nyd", buffer)
        assertEquals("b123c", slice.toString())
        buffer.replace(0, buffer.length, "")
        assertBuffer("", buffer)
    }

    @Test
    fun emptyDocumentAndEmptyEditsHaveOneLine() {
        val buffer = PieceTableBuffer()
        buffer.insert(0, "")
        buffer.delete(0, 0)
        buffer.replace(0, 0, "")
        assertBuffer("", buffer)
        assertEquals("", buffer.getText(0, 0).toString())
    }

    @Test
    fun lineIndexTracksInsertionDeletionAndReplacementAtBoundaries() {
        val buffer = PieceTableBuffer("a\nb\n")
        val index = buffer.lineIndex
        assertBuffer("a\nb\n", buffer)
        buffer.insert(2, "\n")
        assertBuffer("a\n\nb\n", buffer)
        buffer.delete(1, 3)
        assertBuffer("ab\n", buffer)
        buffer.replace(0, 3, "\n\n")
        assertBuffer("\n\n", buffer)
        assertEquals(3, index.lineCount)
        assertEquals(2, index.getLineForOffset(2))
        buffer.delete(0, 2)
        assertBuffer("", buffer)
    }

    @Test
    fun preservesCrLfAndUsesUtf16Offsets() {
        val buffer = PieceTableBuffer("A\r\n\uD83D\uDE00e\u0301\r\n")
        assertBuffer("A\r\n\uD83D\uDE00e\u0301\r\n", buffer)
        assertEquals("A\r", buffer.getLine(0).toString())
        assertEquals(3, buffer.getLineStart(1))
        assertEquals('\uD83D', buffer.charAt(3))
        assertEquals('\uDE00', buffer.charAt(4))
        buffer.delete(2, 3)
        assertBuffer("A\r\uD83D\uDE00e\u0301\r\n", buffer)
        buffer.insert(2, "\n")
        assertBuffer("A\r\n\uD83D\uDE00e\u0301\r\n", buffer)
    }

    @Test
    fun snapshotsMutableInput() {
        val initial = StringBuilder("abc")
        val buffer = PieceTableBuffer(initial)
        initial.setLength(0)
        val added = StringBuilder("123\n")
        buffer.insert(1, added)
        added.setLength(0)
        assertBuffer("a123\nbc", buffer)
    }

    @Test
    fun invalidRequestsDoNotMutateContentOrLines() {
        val buffer = PieceTableBuffer("a\nb")
        val invalid = listOf<() -> Unit>(
            { buffer.insert(-1, "x") }, { buffer.insert(4, "x") },
            { buffer.delete(2, 1) }, { buffer.delete(-1, 0) },
            { buffer.replace(0, 4, "\n") }, { buffer.replace(4, 4, "") },
            { buffer.getText(0, 4) }, { buffer.getText(2, 1) },
            { buffer.charAt(-1) }, { buffer.charAt(3) },
            { buffer.getLine(-1) }, { buffer.getLine(2) },
            { buffer.getLineStart(2) }, { buffer.getLineEnd(-1) },
            { buffer.getLineForOffset(-1) }, { buffer.getLineForOffset(4) }
        )
        for (request in invalid) {
            assertThrows(IndexOutOfBoundsException::class.java) { request() }
            assertBuffer("a\nb", buffer)
        }
    }

    @Test
    fun repeatedTypingAndFragmentedEditsPreserveText() {
        val buffer = PieceTableBuffer()
        val reference = StringBuilder()
        repeat(500) { index ->
            val text = if (index % 7 == 0) "\n" else "x"
            buffer.insert(buffer.length, text)
            reference.append(text)
        }
        repeat(100) { index ->
            buffer.insert(index * 2, "z")
            reference.insert(index * 2, "z")
        }
        buffer.delete(15, 550)
        reference.delete(15, 550)
        assertBuffer(reference.toString(), buffer)
    }

    @Test
    fun randomizedEditsMatchReferenceTextAndLinePositions() {
        repeat(12) { seed ->
            val random = Random(seed)
            val reference = StringBuilder("initial\r\ntext\n")
            val buffer = PieceTableBuffer(reference)
            repeat(500) { step ->
                val start = random.nextInt(reference.length + 1)
                val end = random.nextInt(start, reference.length + 1)
                val text = buildString {
                    repeat(random.nextInt(12)) { append("ab\n\r\t\uD83D\uDE00\u0301".random(random)) }
                }
                when (random.nextInt(3)) {
                    0 -> { buffer.insert(start, text); reference.insert(start, text) }
                    1 -> { buffer.delete(start, end); reference.delete(start, end) }
                    else -> { buffer.replace(start, end, text); reference.replace(start, end, text) }
                }
                try {
                    assertBuffer(reference.toString(), buffer)
                    val from = random.nextInt(reference.length + 1)
                    val to = random.nextInt(from, reference.length + 1)
                    assertEquals(reference.substring(from, to), buffer.getText(from, to).toString())
                } catch (failure: AssertionError) {
                    throw AssertionError("Seed $seed, step $step", failure)
                }
            }
        }
    }

    private fun assertBuffer(expected: String, buffer: PieceTableBuffer) {
        assertEquals(expected.length, buffer.length)
        assertEquals(expected, buffer.getText(0, buffer.length).toString())
        expected.forEachIndexed { index, char -> assertEquals(char, buffer.charAt(index)) }
        // Reference lines are rebuilt from plain text, independently of the incremental index.
        val lines = expected.split('\n')
        assertEquals(lines.size, buffer.lineCount)
        var start = 0
        lines.forEachIndexed { line, text ->
            assertEquals(start, buffer.getLineStart(line))
            assertEquals(start + text.length, buffer.getLineEnd(line))
            assertEquals(text, buffer.getLine(line).toString())
            for (offset in start..start + text.length) assertEquals(line, buffer.getLineForOffset(offset))
            start += text.length + 1
        }
    }
}
