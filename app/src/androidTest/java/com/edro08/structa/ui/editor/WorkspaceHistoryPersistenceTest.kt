package com.edro08.structa.ui.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.edro08.structa.data.settings.AndroidSettingsRepository
import com.edro08.structa.data.settings.AndroidWorkspaceHistoryRepository
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileRef
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

    @Test fun directWorkspaceProviderAndRootSurviveRecreationAlongsideSaf() {
        val settings = AndroidSettingsRepository(context, settingsName)
        val direct = FileRef(DocumentId("/storage/emulated/0/Documents/Structa"), "direct")
        val saf = DocumentId("content://example/tree/primary%3AProjects")
        settings.setLastFolderRef(direct)
        settings.setFileAccessProvider("DIRECT")
        val history = AndroidWorkspaceHistoryRepository(context, settings, historyName)
        history.opened(saf, 1L)
        history.opened(direct.id, 2L)
        assertEquals(direct, AndroidSettingsRepository(context, settingsName).lastFolderRef())
        assertEquals("DIRECT", AndroidSettingsRepository(context, settingsName).fileAccessProvider())
        val restored = AndroidWorkspaceHistoryRepository(context, settings, historyName).all()
        assertEquals("direct", restored.single { it.id == direct.id }.providerId)
        assertEquals(direct.id.value, restored.single { it.id == direct.id }.path)
        assertEquals("saf", restored.single { it.id == saf }.providerId)
    }
}
