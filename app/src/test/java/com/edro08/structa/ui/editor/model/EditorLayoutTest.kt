package com.edro08.structa.ui.editor.model

import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import org.junit.Assert.*
import org.junit.Test

class EditorLayoutTest {
    @Test
    fun wrappingMapsVisualRowsBackToLogicalLinesWithoutChangingOffsets() {
        val buffer = PieceTableBuffer("abcdefghij\n\tX\n\nend")
        val layout = WrappedLayout(buffer, 5)
        assertEquals(7, layout.rowCount)
        assertEquals(listOf(0, 3, 5, 6, 7), (0..4).map(layout::firstRow))
        assertEquals(listOf(0, 0, 0, 1, 1, 2, 3), (0 until layout.rowCount).map(layout::lineAt))
        assertEquals(2, layout.rowFor(0, 10))
        assertEquals(10, layout.segmentStart(2, 0))
        assertEquals("abcdefghij\n\tX\n\nend", buffer.getText(0, buffer.length).toString())
    }

    @Test
    fun viewportReadsOnlyVisibleLinesAndMarginFromLargeDocument() {
        val viewport = EditorViewport.calculate(100_000, 400f, 20f, 50f, 46_800f)
        assertEquals(2338, viewport.firstVisibleLine)
        assertEquals(2361, viewport.lastVisibleLine)
        assertEquals(50f, viewport.scrollX, 0f)
        assertEquals(46_800f, viewport.scrollY, 0f)
    }

    @Test
    fun viewportClampsAtBothEndsAndAfterDocumentShrinks() {
        val first = EditorViewport.calculate(100, 100f, 20f, -10f, -50f)
        assertEquals(0, first.firstVisibleLine)
        assertEquals(6, first.lastVisibleLine)
        assertEquals(0f, first.scrollX, 0f)
        assertEquals(0f, first.scrollY, 0f)
        val last = EditorViewport.calculate(100, 100f, 20f, 0f, 100_000f)
        assertEquals(1900f, last.scrollY, 0f)
        assertEquals(93, last.firstVisibleLine)
        assertEquals(99, last.lastVisibleLine)
        val shrunk = EditorViewport.calculate(1, 100f, 20f, 0f, last.scrollY)
        assertEquals(0, shrunk.firstVisibleLine)
        assertEquals(0, shrunk.lastVisibleLine)
        assertEquals(0f, shrunk.scrollY, 0f)
    }

    @Test
    fun fractionalScrollingIncludesPartiallyVisibleLines() {
        val viewport = EditorViewport.calculate(100, 40f, 20f, 0f, 1f, overscan = 0)
        assertEquals(0, viewport.firstVisibleLine)
        assertEquals(2, viewport.lastVisibleLine)
    }

    @Test
    fun zeroHeightHasNoLinesAndShortDocumentsHaveNoVerticalScroll() {
        val zero = EditorViewport.calculate(100, 0f, 20f, 0f, 0f)
        assertTrue(zero.firstVisibleLine > zero.lastVisibleLine)
        val short = EditorViewport.calculate(2, 400f, 20f, 0f, 500f)
        assertEquals(0f, short.scrollY, 0f)
        assertEquals(1, short.lastVisibleLine)
    }

    @Test
    fun invalidGeometryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EditorViewport.calculate(0, 400f, 20f, 0f, 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditorViewport.calculate(100, 400f, 0f, 0f, 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditorViewport.calculate(100, 400f, 20f, Float.NaN, 0f)
        }
    }

    @Test
    fun tabsUseStopsAndOffsetMappingMatchesDisplayText() {
        val line = EditorLine(0, 0, "a\tb\tX")
        assertEquals("a   b   X", line.textInColumns(0, line.columnCount))
        assertEquals(listOf(0, 1, 4, 5, 8, 9), (0..5).map(line::columnAt))
        assertEquals(9, line.columnCount)
        assertEquals(1..4, line.selectionColumns(1, 3, false))
    }

    @Test
    fun crAndSurrogatesPreserveCoreUtf16Offsets() {
        val line = EditorLine(0, 0, "\uD83D\uDE00\r")
        assertEquals("\uD83D\uDE00 ", line.textInColumns(0, line.columnCount))
        assertEquals(3, line.columnCount)
        assertEquals(2, line.columnAt(2))
    }

    @Test
    fun selectionsClipToLineAndIncludeSelectedNewlineCell() {
        val line = EditorLine(1, 4, "abc")
        assertEquals(0..3, line.selectionColumns(0, 10, true))
        assertEquals(0..2, line.selectionColumns(0, 7, true))
        assertEquals(1..1, line.selectionColumns(5, 6, true))
        assertEquals(3..3, line.selectionColumns(7, 8, true))
        assertNull(line.selectionColumns(7, 8, false))
        assertNull(line.selectionColumns(8, 9, true))
        assertNull(line.selectionColumns(1, 4, true))
        assertNull(line.selectionColumns(5, 5, true))
    }

    @Test
    fun emptyLinesCanShowSelectedNewlineButNotEmptyEof() {
        val empty = EditorLine(0, 0, "")
        assertEquals(0..0, empty.selectionColumns(0, 1, true))
        assertNull(empty.selectionColumns(0, 1, false))
        assertNull(empty.selectionColumns(0, 0, true))
    }

    @Test
    fun horizontalSlicesMatchExpandedReferenceAcrossCheckpointsAndPartialTabs() {
        val raw = "a\tb\r".repeat(150)
        val expectedColumns = mutableListOf<Int>()
        val expected = buildString {
            raw.forEach { char ->
                expectedColumns.add(length)
                when (char) {
                    '\t' -> repeat(4 - length % 4) { append(' ') }
                    '\r' -> append(' ')
                    else -> append(char)
                }
            }
            expectedColumns.add(length)
        }
        val line = EditorLine(0, 0, raw)
        assertEquals(expected.length, line.columnCount)
        expectedColumns.forEachIndexed { index, column -> assertEquals(column, line.columnAt(index)) }
        for (start in expected.indices) {
            val end = minOf(start + 17, expected.length)
            assertEquals(expected.substring(start, end), line.textInColumns(start, end))
        }
    }

    @Test
    fun longLinesExposeSmallSlicesAndExactCheckpointEof() {
        val line = EditorLine(0, 0, "\t".repeat(65_536))
        assertEquals(262_144, line.columnCount)
        assertEquals(line.columnCount, line.columnAt(line.length))
        assertEquals(" ".repeat(80), line.textInColumns(200_000, 200_080))
        assertEquals("", line.textInColumns(line.columnCount, line.columnCount))
    }

    @Test
    fun hitTestingClampsAndMapsTabsToNearestInsertionBoundary() {
        val line = EditorLine(0, 0, "a\tb")
        assertEquals(0, line.offsetAtColumn(-100f))
        assertEquals(1, line.offsetAtColumn(1.9f))
        assertEquals(2, line.offsetAtColumn(2.6f))
        assertEquals(3, line.offsetAtColumn(100f))
        for (offset in 0..line.length) assertEquals(offset, line.offsetAtColumn(line.columnAt(offset).toFloat()))
    }
}
