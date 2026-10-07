package com.edro08.structa.ui.editor

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edro08.structa.data.filesystem.direct.DirectFileSystem
import com.edro08.structa.domain.document.DocumentId
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectStorageIntegrationTest {
    @Test fun sharedStorageWorkspaceUsesActualPathsWithoutSaf() = runBlocking {
        if (!Environment.isExternalStorageManager()) return@runBlocking
        val fs = DirectFileSystem()
        val downloads = DocumentId(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath)
        val name = "structa-direct-test-${System.nanoTime()}"
        val folder = fs.create(downloads, name, true)
        try {
            val nested = fs.create(folder.id, "Projects", true)
            val text = fs.create(nested.id, "demo.json", false)
            fs.write(text.id, "{\"ok\":true}")
            assertEquals("{\"ok\":true}", fs.read(fs.stat(text.id)))
            assertTrue(fs.list(folder.id).any { it.id == nested.id })
            assertTrue(File(text.id.value).isFile)
            fs.delete(text.id)
            fs.delete(nested.id)
        } finally {
            fs.delete(folder.id)
        }
    }
}
