package com.edro08.structa.domain.editor

import com.edro08.structa.domain.editor.syntax.*
import com.edro08.structa.domain.editor.decoration.*
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.random.Random

class SyntaxTest {
    private val go = LanguageRegistry.forFileName("main.go")
    private fun highlight(text: String, previous: SyntaxSnapshot? = null) =
        IncrementalHighlighter.highlight(text, go, previous)

    @Test fun registryDetectsSpecifiedLanguagesAndFallsBackWithoutGuessing() {
        listOf("go", "json", "yaml", "md", "xml").forEach {
            assertNotEquals("text", LanguageRegistry.forFileName("file.$it").id)
        }
        assertEquals(go, LanguageRegistry.forFileName("MAIN.GO"))
        listOf("kt", "kts", "java", "js", "mjs", "cjs").forEach {
            assertEquals(LanguageRegistry.plain, LanguageRegistry.forFileName("file.$it"))
        }
        assertEquals(LanguageRegistry.plain, LanguageRegistry.forFileName("unknown"))
        assertEquals(LanguageRegistry.plain, LanguageRegistry.forFileName("notes.kt.txt"))
    }

    @Test fun spansAreUtf16OrderedAndMultilineStatesSurviveEmptyLines() {
        val text = "var face = \"😀\" // hi\r\n/* outer\n\ninside\nend */ var x = 2"
        val result = highlight(text)
        result.lines.forEach { line ->
            var end = 0
            line.result.spans.forEach { span ->
                assertTrue(span.start >= end && span.end <= line.text.length)
                end = span.end
            }
        }
        assertEquals("\"😀\"", result.lines[0].result.spans.first { it.style == SyntaxStyle.STRING }.let { text.substring(it.start, it.end) })
        assertEquals(1, result.lines[2].result.state.commentDepth)
        assertEquals(0, result.lines.last().result.state.commentDepth)
        assertTrue(result.lines.last().result.spans.any { it.style == SyntaxStyle.KEYWORD })
    }

    @Test fun rawStringsJsonKeysYamlCommentsAndMarkdownFences() {
        val raw = highlight("var s = `hello\n/* string */\n` + 1")
        assertEquals(SyntaxStyle.STRING, raw.lines[1].result.spans.single().style)
        assertEquals(TokenizerState(), raw.lines.last().result.state)
        val json = LanguageRegistry.forFileName("a.json").tokenizer.tokenize("{\"key\": true, \"x\": 1e-2}")
        assertEquals(2, json.spans.count { it.style == SyntaxStyle.KEY })
        assertEquals(1, json.spans.count { it.style == SyntaxStyle.NUMBER })
        val yaml = LanguageRegistry.forFileName("a.yml").tokenizer.tokenize("url: abc#x # comment")
        assertEquals("# comment", yaml.spans.last().let { "url: abc#x # comment".substring(it.start, it.end) })
        val md = IncrementalHighlighter.highlight("# Title\n````kt\n```\ncode\n````\n> quote", LanguageRegistry.forFileName("a.md"))
        assertEquals("````", md.lines[2].result.state.fence)
        assertEquals(TokenizerState(), md.lines[4].result.state)
        assertEquals(SyntaxStyle.COMMENT, md.lines.last().result.spans.single().style)
        val go = IncrementalHighlighter.highlight("var s = `one\ntwo`", LanguageRegistry.forFileName("a.go"))
        assertEquals(SyntaxStyle.STRING, go.lines.last().result.spans.single().style)
    }

    @Test fun localEditsReuseStableSuffixEvenWhenLinesAreInsertedOrDeleted() {
        val before = "var a = 1\nvar b = 2\nvar c = 3\n"
        val initial = highlight(before)
        val edited = highlight(before.replace("b = 2", "b = 4"), initial)
        assertEquals(1, edited.tokenizedLines)
        assertSame(initial.lines[2], edited.lines[2])
        val inserted = highlight("// new\n${edited.text}", edited)
        assertEquals(1, inserted.tokenizedLines)
        assertSame(edited.lines[0], inserted.lines[1])
        val deleted = highlight(edited.text, inserted)
        assertEquals(0, deleted.tokenizedLines)
        assertEquals(0, highlight(edited.text, deleted).tokenizedLines)
    }

    @Test fun changesPropagateUntilLexicalStateConvergesAndUndoMatchesFullScan() {
        val before = "var a = 1\nword\n*/\nvar b = 2"
        val initial = highlight(before)
        val after = highlight(before.replace("var a = 1", "/*"), initial)
        assertEquals(3, after.tokenizedLines)
        assertSame(initial.lines[3], after.lines[3])
        val undone = highlight(before, after)
        assertEquals(initial.lines, undone.lines)
        assertEquals(after.lines, highlight(after.text, undone).lines)
    }

    @Test fun randomInsertionsDeletionsAndReplacementsAgreeWithFullTokenization() {
        val random = Random(42)
        var current = highlight("/* comment */\nvar a = \"text\"\n\n")
        val pieces = listOf("\n", "/*", "*/", "`", "\"", "\\", "😀", "var ", "x", "")
        repeat(250) {
            val start = random.nextInt(current.text.length + 1)
            val end = random.nextInt(start, current.text.length + 1)
            val text = current.text.replaceRange(start, end, pieces.random(random))
            current = highlight(text, current)
            assertEquals(highlight(text).lines, current.lines)
        }
    }

    @Test fun cancelledWorkDoesNotMutatePublishedSnapshotAndLargeTextIsLimited() {
        val initial = highlight("var a = 1")
        var checks = 0
        try {
            IncrementalHighlighter.highlight("\"" + "\\x".repeat(30_000), go, initial) {
                if (++checks > 5) throw CancellationException()
            }
            fail("Expected cooperative cancellation inside a string")
        } catch (_: CancellationException) { }
        assertEquals("var a = 1", initial.text)
        assertTrue(highlight("x".repeat(IncrementalHighlighter.MAX_TEXT_LENGTH + 1)).limited)
        assertEquals(1, highlight("").lines.size)
    }

    @Test fun decorationIndexHandlesOverlapsBoundariesAndEmptyMarkers() {
        fun item(start: Int, end: Int) = Decoration(TextRange(TextOffset(start), TextOffset(end)), DecorationType.SEARCH_MATCH)
        val long = item(0, 100)
        val marker = item(50, 50)
        val set = DecorationSet(listOf(item(100, 102), item(1, 4), marker, long))
        assertEquals(listOf(long, marker), set.intersecting(50, 51))
        assertEquals(listOf(item(100, 102)), set.intersecting(100, 101))
        assertTrue(set.intersecting(103, 104).isEmpty())
    }
}
