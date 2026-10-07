package com.edro08.structa.ui.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.edro08.structa.data.settings.AndroidSettingsRepository
import com.edro08.structa.domain.settings.EditorFont
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class AppearanceSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "appearance_test"

    @After fun clear() { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun defaultsAndSelectionsSurviveRepositoryRecreation() {
        val settings = AndroidSettingsRepository(context, name)
        assertTrue(settings.darkTheme())
        assertEquals(EditorFont.MONOSPACE, settings.editorFont())
        assertEquals(14, settings.editorFontSize())
        settings.setDarkTheme(false)
        settings.setEditorFont(EditorFont.SANS_MONOSPACE)
        settings.setEditorFontSize(20)

        val reopened = AndroidSettingsRepository(context, name)
        assertFalse(reopened.darkTheme())
        assertEquals(EditorFont.SANS_MONOSPACE, reopened.editorFont())
        assertEquals(20, reopened.editorFontSize())
    }
}
