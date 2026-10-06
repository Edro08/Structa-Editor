package com.edro08.structa.data.filesystem

import com.edro08.structa.data.filesystem.direct.DirectFileRef
import com.edro08.structa.data.filesystem.direct.DirectFileSystem
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.domain.filesystem.FileRef
import com.edro08.structa.domain.filesystem.FileSystem
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectFileSystemTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun createsListsReadsWritesRenamesAndDeletesUsingRealPaths() = runBlocking {
        val fs = DirectFileSystem()
        val root = DirectFileRef(temporary.root.path).ref
        assertEquals("direct", root.fileSystem)
        assertEquals(FileRef.of(root.id), root)
        val folder = fs.create(root.id, "Documents", true)
        val original = fs.create(folder.id, "text.txt", false)
        fs.write(original.id, "uno\ndos")
        assertEquals("uno\ndos", fs.read(fs.stat(original.id)))
        assertEquals(listOf("text.txt"), fs.list(folder.id).map { it.name })
        val renamed = fs.rename(original.id, "nuevo.txt")
        assertFalse(fs.exists(original.id))
        assertEquals("uno\ndos", fs.read(renamed))
        assertTrue(fs.exists(renamed.id))
        assertThrows(IOException::class.java) { runBlocking { fs.create(folder.id, "nuevo.txt", false) } }
        fs.delete(renamed.id)
        fs.delete(folder.id)
        assertFalse(fs.exists(folder.id))
    }

    @Test fun refusesRestrictedRoutesAndPathTraversal() { runBlocking {
        val fs = DirectFileSystem()
        val root = DocumentId(temporary.root.canonicalPath)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { fs.create(root, "../outside", false) }
        }
        val android = fs.create(root, "Android", true)
        assertThrows(IOException::class.java) {
            runBlocking { fs.create(android.id, "data", true) }
        }
        assertFalse(java.io.File(temporary.root, "Android/data").exists())
        assertThrows(IllegalArgumentException::class.java) { DirectFileRef("relative") }
    } }

    @Test fun routesDirectPathsAndSafUrisToTheirOwnProviders() = runBlocking {
        val direct = DirectFileSystem()
        val safId = DocumentId("content://example/tree/project")
        val saf = object : FileSystem {
            override val key = "saf"
            override suspend fun stat(id: DocumentId) = FileEntry(id, "Saf", 0, true)
            override suspend fun list(directory: DocumentId) = listOf(stat(directory))
            override suspend fun read(file: FileEntry) = "saf"
            override suspend fun write(destination: DocumentId, content: String) = Unit
            override suspend fun create(parent: DocumentId, name: String, directory: Boolean) = stat(parent)
            override suspend fun rename(file: DocumentId, name: String) = stat(file)
            override suspend fun delete(file: DocumentId) = Unit
        }
        val routed = RoutedFileSystem(saf, direct)
        assertEquals("Saf", routed.stat(safId).name)
        assertEquals("saf", routed.read(routed.stat(safId)))
        val root = DocumentId(temporary.root.canonicalPath)
        assertEquals(root, routed.stat(root).id)
        val created = routed.create(root, "direct.txt", false)
        routed.write(created.id, "direct")
        assertEquals("direct", routed.read(created))
        assertEquals("direct", FileRef.of(created.id).fileSystem)
        assertEquals("saf", FileRef.of(safId).fileSystem)
    }
}
