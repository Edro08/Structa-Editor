package com.edro08.structa.domain.editor

import com.edro08.structa.domain.editor.syntax.*
import org.junit.Assert.*
import org.junit.Test

class XmlSyntaxTest {
    @Test fun registryRecognizesCommonXmlExtensions() {
        listOf("xml", "svg", "xsd", "xsl", "xslt").forEach {
            assertEquals("xml", LanguageRegistry.forFileName("DOCUMENT.$it").id)
        }
        assertEquals("text", LanguageRegistry.forFileName("code.unknown").id)
    }

    @Test fun xmlColorsTagsAttributesEntitiesCommentsAndCdataAcrossLines() {
        val language = LanguageRegistry.forFileName("file.xml")
        val source = "<!-- beginning\nstill --> <root\n id=\"line\ncontinued\">&amp;</root>\n<![CDATA[<raw>\nstill raw]]>"
        val result = IncrementalHighlighter.highlight(source, language)
        assertEquals(SyntaxStyle.COMMENT, result.lines[1].result.spans.first().style)
        assertEquals("root", result.lines[1].result.spans.first { it.style == SyntaxStyle.KEYWORD }
            .let { result.lines[1].text.substring(it.start, it.end) })
        assertEquals("id", result.lines[2].result.spans.first { it.style == SyntaxStyle.KEY }
            .let { result.lines[2].text.substring(it.start, it.end) })
        assertEquals("\"line", result.lines[2].result.spans.last().let {
            result.lines[2].text.substring(it.start, it.end)
        })
        assertEquals("continued\"", result.lines[3].result.spans.first().let {
            result.lines[3].text.substring(it.start, it.end)
        })
        assertTrue(result.lines[3].result.spans.any { it.style == SyntaxStyle.OPERATOR &&
            result.lines[3].text.substring(it.start, it.end) == "&amp;" })
        assertEquals(SyntaxStyle.STRING, result.lines[4].result.spans.single().style)
        assertEquals("]]>", result.lines[4].result.state.delimiter)
        assertEquals(TokenizerState(), result.lines.last().result.state)
    }

    @Test fun xmlCheckpointAndEditRecalculateFollowingCommentState() {
        val language = LanguageRegistry.forFileName("file.xml")
        val prefix = "<!--\n" + "inside\n".repeat(ViewportHighlighter.CHECKPOINT_LINES - 1)
        val first = ViewportHighlighter.highlight(prefix.dropLast(1), 0, 0, language, TokenizerState())
        val state = first.checkpoints.getValue(ViewportHighlighter.CHECKPOINT_LINES)
        val visible = ViewportHighlighter.highlight("still inside\n--><x id=\"a\"/>",
            ViewportHighlighter.CHECKPOINT_LINES, ViewportHighlighter.CHECKPOINT_LINES, language, state).window
        assertEquals(SyntaxStyle.COMMENT, visible.spansAt(ViewportHighlighter.CHECKPOINT_LINES).single().style)
        assertTrue(visible.spansAt(ViewportHighlighter.CHECKPOINT_LINES + 1).any { it.style == SyntaxStyle.KEYWORD })

        val before = "<!-- open\ncontent\n-->\n<root/>"
        val initial = IncrementalHighlighter.highlight(before, language)
        val changed = IncrementalHighlighter.highlight(before.replace("<!-- open", "<root/>"), language, initial)
        assertEquals(IncrementalHighlighter.highlight(changed.text, language).lines, changed.lines)
        assertEquals(TokenizerState(), changed.lines.last().result.state)
    }
}
