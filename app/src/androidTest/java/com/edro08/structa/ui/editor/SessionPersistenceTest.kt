package com.edro08.structa.ui.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.edro08.structa.data.settings.AndroidSessionRepository
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.workspace.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SessionPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "session_test"
    @After fun clear() { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun metadataSurvivesRepositoryRecreationAndEmptySessionReplacesOldTabs() {
        val id = DocumentId("content://provider/document/one")
        val session = EditorSession(listOf(SessionTab(id, 2, 7, 30f, 180f)), id)
        AndroidSessionRepository(context, name).save(session)
        assertEquals(session, AndroidSessionRepository(context, name).load())
        AndroidSessionRepository(context, name).save(EditorSession())
        assertEquals(EditorSession(), AndroidSessionRepository(context, name).load())
    }

    @Test fun corruptMetadataFallsBackToEmptySession() {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("metadata", "broken").commit()
        assertEquals(EditorSession(), AndroidSessionRepository(context, name).load())
    }
}
