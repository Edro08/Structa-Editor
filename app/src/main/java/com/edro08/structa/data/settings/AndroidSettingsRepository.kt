package com.edro08.structa.data.settings

import android.content.Context
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.settings.SettingsRepository

class AndroidSettingsRepository(context: Context) : SettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences("structa", Context.MODE_PRIVATE)

    override fun lastFolder(): DocumentId? =
        preferences.getString("last_folder_uri", null)?.let(::DocumentId)

    override fun setLastFolder(folder: DocumentId) {
        preferences.edit().putString("last_folder_uri", folder.value).apply()
    }
}
