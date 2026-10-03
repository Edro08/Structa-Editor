package com.edro08.structa.domain.settings

import com.edro08.structa.domain.document.DocumentId

interface SettingsRepository {
    fun lastFolder(): DocumentId?
    fun setLastFolder(folder: DocumentId)
}
