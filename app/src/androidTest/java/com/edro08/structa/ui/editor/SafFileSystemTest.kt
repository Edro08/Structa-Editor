package com.edro08.structa.ui.editor

import android.content.Context
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.edro08.structa.data.filesystem.SafFileSystem
import com.edro08.structa.domain.document.DocumentId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SafFileSystemTest {
    @Test fun treeProviderSupportsNestedCreateTruncatingWriteRenameAndDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val granted = CountDownLatch(1)
        context.sendOrderedBroadcast(Intent().setComponent(ComponentName(
            InstrumentationRegistry.getInstrumentation().context.packageName,
            GrantTestDocumentsReceiver::class.java.name)), null, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { granted.countDown() }
        }, null, 0, null, null)
        assertTrue(granted.await(10, TimeUnit.SECONDS))
        val fs = SafFileSystem(context)
        val root = DocumentId(DocumentsContract.buildTreeDocumentUri("com.edro08.structa.test.documents", "root").toString())
        val folder = fs.create(root, "case-${System.nanoTime()}", true)
        try {
            val file = fs.create(folder.id, "before.txt", false)
            fs.write(file.id, "long original content")
            fs.write(file.id, "short")
            assertEquals("short", fs.read(fs.stat(file.id)))
            assertEquals(listOf(file.id), fs.list(folder.id).map { it.id })
            val renamed = fs.rename(file.id, "after.txt")
            assertNotEquals(file.id, renamed.id)
            assertEquals("after.txt", renamed.name)
            assertEquals("short", fs.read(renamed))
            fs.delete(renamed.id)
            assertTrue(fs.list(folder.id).isEmpty())
        } finally { fs.delete(folder.id) }
    }
}
