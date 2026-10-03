package com.edro08.structa.application

import com.edro08.structa.domain.document.Document
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.FileMode
import com.edro08.structa.domain.document.MAX_EDITABLE_BYTES
import com.edro08.structa.application.browser.ListDirectory
import com.edro08.structa.application.document.OpenDocument
import com.edro08.structa.application.document.SaveDocumentCopy
import com.edro08.structa.application.editor.ContentFormatDetector
import com.edro08.structa.application.editor.FormatJsonDocument
import com.edro08.structa.domain.filesystem.DirectoryReader
import com.edro08.structa.domain.filesystem.FileReader
import com.edro08.structa.domain.filesystem.FileWriter
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DocumentUseCasesTest {
    private val file = FileEntry(DocumentId("file"), "document", 10L, false)
    private val detector = object : ContentFormatDetector() {
        override fun detect(content: String) = FileMode.JSON
    }

    @Test
    fun listSortsDirectoriesFirstAndNamesIgnoringCase() = runBlocking {
        val directory = DocumentId("folder")
        val entries = listOf(file.copy(name = "z"), file.copy(name = "B"), file.copy(name = "a", isDirectory = true))
        val reader = object : DirectoryReader {
            override suspend fun list(directory: DocumentId): List<FileEntry> {
                assertEquals(DocumentId("folder"), directory)
                return entries
            }
        }
        assertEquals(listOf("a", "B", "z"), ListDirectory(reader)(directory).map { it.name })
    }

    @Test
    fun openPreservesEntryAndDetectsContentOnDefaultDispatcher() = runBlocking {
        val callerThread = Thread.currentThread()
        val reader = object : FileReader {
            override suspend fun read(file: FileEntry): String = "{}"
        }
        val checkingDetector = object : ContentFormatDetector() {
            override fun detect(content: String): FileMode {
                assertNotSame(callerThread, Thread.currentThread())
                assertEquals("{}", content)
                return FileMode.JSON
            }
        }
        assertEquals(Document(file, "{}", FileMode.JSON), OpenDocument(reader, checkingDetector)(file))
    }

    @Test
    fun openRejectsOversizeAndDirectoriesBeforeReading() = runBlocking {
        val reader = object : FileReader {
            override suspend fun read(file: FileEntry): String = error("Must not read rejected entries")
        }
        for (entry in listOf(file.copy(sizeBytes = MAX_EDITABLE_BYTES + 1), file.copy(isDirectory = true))) {
            try {
                OpenDocument(reader, detector)(entry)
                fail("Expected IOException")
            } catch (_: IOException) {
                // Expected validation failure.
            }
        }
    }

    @Test
    fun openAcceptsExactLimitAndUnknownSize() = runBlocking {
        val reader = object : FileReader {
            override suspend fun read(file: FileEntry): String = "{}"
        }
        for (size in listOf(MAX_EDITABLE_BYTES, -1L, 0L)) {
            assertEquals("{}", OpenDocument(reader, detector)(file.copy(sizeBytes = size)).content)
        }
    }

    @Test
    fun openPropagatesReaderFailure() = runBlocking {
        val failure = IOException("Read failed")
        val reader = object : FileReader {
            override suspend fun read(file: FileEntry): String = throw failure
        }
        try {
            OpenDocument(reader, detector)(file)
            fail("Expected IOException")
        } catch (exception: IOException) {
            assertSame(failure, exception)
        }
    }

    @Test
    fun saveForwardsDestinationAndContent() = runBlocking {
        var saved: Pair<DocumentId, String>? = null
        val writer = object : FileWriter {
            override suspend fun write(destination: DocumentId, content: String) {
                saved = destination to content
            }
        }
        SaveDocumentCopy(writer)(DocumentId("copy"), "edited")
        assertEquals(DocumentId("copy") to "edited", saved)
    }

    @Test
    fun formatPreservesNonJsonContentIncludingWhitespace() = runBlocking {
        val formatter = FormatJsonDocument()
        val content = "  title: unchanged\r\n\ttext\n"
        for (mode in listOf(FileMode.TEXT, FileMode.YAML)) {
            assertEquals(content, formatter(content, mode))
        }
    }
}
