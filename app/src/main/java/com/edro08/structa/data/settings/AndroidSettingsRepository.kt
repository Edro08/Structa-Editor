package com.edro08.structa.data.settings

import android.content.Context
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileRef
import com.edro08.structa.domain.settings.SettingsRepository
import com.edro08.structa.domain.settings.EditorFont

class AndroidSettingsRepository(context: Context, name: String = "structa") : SettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun lastFolder(): DocumentId? =
        preferences.getString("last_folder_uri", null)?.let(::DocumentId)

    override fun setLastFolder(folder: DocumentId) {
        setLastFolderRef(FileRef.of(folder))
    }

    override fun lastFolderRef(): FileRef? = lastFolder()?.let { id ->
        FileRef(id, preferences.getString("last_folder_provider", null) ?: FileRef.of(id).fileSystem)
    }

    override fun setLastFolderRef(folder: FileRef) {
        preferences.edit().putString("last_folder_uri", folder.id.value)
            .putString("last_folder_provider", folder.fileSystem).apply()
    }

    override fun fileAccessProvider(): String = preferences.getString("file_access_provider", "SAF") ?: "SAF"
    override fun setFileAccessProvider(provider: String) {
        preferences.edit().putString("file_access_provider", provider).apply()
    }

    override fun darkTheme(): Boolean = preferences.getBoolean("dark_theme", true)
    override fun setDarkTheme(dark: Boolean) { preferences.edit().putBoolean("dark_theme", dark).apply() }

    override fun editorFont(): EditorFont = EditorFont.entries.firstOrNull {
        it.name == preferences.getString("editor_font", null)
    } ?: EditorFont.MONOSPACE
    override fun setEditorFont(font: EditorFont) { preferences.edit().putString("editor_font", font.name).apply() }

    override fun editorFontSize(): Int = preferences.getInt("editor_font_size", 14).coerceIn(10, 32)
    override fun setEditorFontSize(size: Int) {
        require(size in 10..32)
        preferences.edit().putInt("editor_font_size", size).apply()
    }
}
