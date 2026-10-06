package com.edro08.structa.application

import com.edro08.structa.application.editor.FormatYamlDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatYamlDocumentTest {
    private val format = FormatYamlDocument()

    @Test fun indentsSimpleMapsAndSequencesWithoutRewritingValues() = runBlocking {
        val input = "root:\n    name: \"Ada\" # keep\n    tags:\n        - 'one'\n        - two\n    active: true\n"
        val expected = "root:\n  name: \"Ada\" # keep\n  tags:\n    - 'one'\n    - two\n  active: true\n"
        assertEquals(expected, format(input))
        assertEquals(expected, format(expected))
    }

    @Test fun retainsCommentsAnchorsQuotesAndLineEndings() = runBlocking {
        val input = "# keep\r\nsettings:\r\n    default: &id 'yes'\r\n    copy: *id # keep\r\n"
        assertEquals("# keep\r\nsettings:\r\n  default: &id 'yes'\r\n  copy: *id # keep\r\n", format(input))
    }

    @Test fun doesNotTouchBlockScalarsAmbiguousItemsOrInvalidIndentation() = runBlocking {
        for (input in listOf(
            "root:\n    message: |\n      line 1\n      line 2\n    other: yes\n",
            "people:\n    - name: Ada\n      age: 20\n",
            "people:\n    - name:\n        first: Ada\n",
            "root:\n    child: value\n   sibling: value\n",
            "root:\n\tchild: value\n",
            "---\nroot: value\n"
        )) assertEquals(input, format(input))
    }
}
