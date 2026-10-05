package com.edro08.structa.ui.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.edro08.structa.data.settings.AndroidSettingsRepository
import com.edro08.structa.data.settings.AndroidWorkspaceHistoryRepository
import com.edro08.structa.domain.document.DocumentId
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WorkspaceHistoryPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settingsName = "workspace_history_settings_test"
    private val historyName = "workspace_history_test"

    @After fun clear() {
        context.getSharedPreferences(settingsName, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(historyName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun favoritesHistoryAndRemovalSurviveRecreationWithoutRestoringRemovedLastFolder() {
        val settings = AndroidSettingsRepository(context, settingsName)
        val first = DocumentId("content://example/tree/primary%3AWorkspace%2FStructa")
        val second = DocumentId("content://example/tree/primary%3AWorkspace%2FNotas")
        settings.setLastFolder(first)
        val history = AndroidWorkspaceHistoryRepository(context, settings, historyName)
        assertEquals("Structa", history.all().single().name)
        history.setFavorite(first, true)
        history.opened(second, 200L)
        history.opened(first, 300L)
        val restored = AndroidWorkspaceHistoryRepository(context, settings, historyName)
        assertEquals(setOf(first, second), restored.all().map { it.id }.toSet())
        assertTrue(restored.all().single { it.id == first }.favorite)
        restored.remove(first)
        assertEquals(listOf(second), AndroidWorkspaceHistoryRepository(context, settings, historyName).all().map { it.id })
        assertEquals(first, settings.lastFolder())
    }
}
