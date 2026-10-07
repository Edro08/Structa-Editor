package com.edro08.structa.ui.editor

import android.content.Context
import android.os.Environment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.edro08.structa.MainActivity
import com.edro08.structa.data.settings.AndroidSettingsRepository
import com.edro08.structa.data.settings.AndroidWorkspaceHistoryRepository
import com.edro08.structa.domain.document.DocumentId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DirectWorkspaceFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsSelectsDirectFolderWithStructaBrowserAndPersistsProvider() {
        if (!Environment.isExternalStorageManager()) return
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AndroidSettingsRepository(context)
        val previous = settings.lastFolderRef()
        val previousProvider = settings.fileAccessProvider()
        val download = DocumentId(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath)
        val history = AndroidWorkspaceHistoryRepository(context, settings)
        val alreadyRecent = history.all().any { it.id == download }
        try {
            compose.onNodeWithText("Configuración").performClick()
            compose.onNodeWithText("Acceso completo").performScrollTo().performClick()
            compose.onNodeWithText("Seleccionar carpeta").assertExists()
            compose.onNodeWithText("Download").performScrollTo().performClick()
            compose.onNodeWithText("Usar esta carpeta").performClick()
            compose.waitUntil(10_000) { settings.lastFolderRef()?.id == download }
            assertEquals("direct", settings.lastFolderRef()?.fileSystem)
            assertEquals("DIRECT", settings.fileAccessProvider())
            compose.onNodeWithText("Explorador").assertExists()
        } finally {
            settings.setFileAccessProvider(previousProvider)
            if (previous != null) settings.setLastFolderRef(previous) else
                context.getSharedPreferences("structa", Context.MODE_PRIVATE).edit()
                    .remove("last_folder_uri").remove("last_folder_provider").commit()
            if (!alreadyRecent) AndroidWorkspaceHistoryRepository(context, settings).remove(download)
        }
    }
}
