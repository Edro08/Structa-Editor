package com.edro08.structa.application

import com.edro08.structa.application.editor.FormatXmlDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatXmlDocumentTest {
    private val format = FormatXmlDocument()

    @Test fun indentsElementsAndKeepsTextInline() = runBlocking {
        val input = "<?xml version=\"1.0\"?><root><item id=\"a>b\">value</item><empty/></root>"
        val expected = "<?xml version=\"1.0\"?>\n<root>\n  <item id=\"a>b\">value</item>\n  <empty/>\n</root>"
        assertEquals(expected, format(input))
        assertEquals(expected, format(expected))
    }

    @Test fun preservesMixedContentCdataAndSpacePreserve() = runBlocking {
        val mixed = "<root><p>Hello <b>world</b> !</p><pre xml:space='preserve'><a/>  <b/></pre><x><![CDATA[a < b]]></x></root>"
        assertEquals("<root>\n  <p>Hello <b>world</b> !</p>\n  <pre xml:space='preserve'><a/>  <b/></pre>\n  <x><![CDATA[a < b]]></x>\n</root>", format(mixed))
        assertEquals("<p> a <b/> b </p>", format("<p> a <b/> b </p>"))
    }

    @Test fun preservesCommentsEntitiesCrLfAndMalformedOrDtdDocuments() = runBlocking {
        assertEquals("<r>\r\n  <!--a > b-->\r\n  <a x='&amp;'/>\r\n</r>",
            format("<r><!--a > b--><a x='&amp;'/></r>\r\n" ).trimEnd())
        for (input in listOf("<a><b></a>", "<a x='unfinished>", "<a><b value=unquoted/></a>",
                "<!DOCTYPE r [<!ENTITY x 'value'>]><r>&x;</r>")) assertEquals(input, format(input))
    }
}
