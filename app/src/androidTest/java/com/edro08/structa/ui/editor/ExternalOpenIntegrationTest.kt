package com.edro08.structa.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.edro08.structa.data.filesystem.SafFileSystem
import com.edro08.structa.data.filesystem.externalDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalOpenIntegrationTest {
    @Test fun contentUriFromAnotherAppIsRegisteredAndReadable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((name, mime) in listOf("notes.txt" to "text/plain", "next.json" to "application/json")) {
            val uri = Uri.parse("content://com.edro08.structa.test.shared/$name")
            for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) {
                val handlers = context.packageManager.queryIntentActivities(
                    Intent(action).setDataAndType(uri, mime), 0)
                assertTrue(handlers.any { it.activityInfo.packageName == context.packageName })
            }
            val entry = externalDocument(context, uri)
            assertEquals(name, entry.name)
            assertEquals(("content for $name\n").length.toLong(), entry.sizeBytes)
            val fs = SafFileSystem(context)
            assertEquals(entry, fs.stat(entry.id))
            assertEquals("content for $name\n", fs.read(entry))
        }
    }
}
