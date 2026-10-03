package com.edro08.structa.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.edro08.structa.application.browser.ListDirectory
import com.edro08.structa.application.document.OpenDocument
import com.edro08.structa.application.document.SaveDocumentCopy
import com.edro08.structa.application.editor.ContentFormatDetector
import com.edro08.structa.application.editor.FormatJsonDocument
import com.edro08.structa.data.filesystem.SafDirectoryReader
import com.edro08.structa.data.filesystem.SafFileReader
import com.edro08.structa.data.filesystem.SafFileWriter
import com.edro08.structa.data.settings.AndroidSettingsRepository
import com.edro08.structa.ui.screen.browser.BrowserViewModel
import com.edro08.structa.ui.screen.editor.EditorViewModel
import com.edro08.structa.ui.screen.home.HomeViewModel
import com.edro08.structa.ui.screen.settings.SettingsViewModel

class AppContainer(context: Context) {
    val directoryReader = SafDirectoryReader(context.applicationContext)
    val fileReader = SafFileReader(context.applicationContext)
    val fileWriter = SafFileWriter(context.applicationContext)
    val settings = AndroidSettingsRepository(context.applicationContext)
    val listDirectory = ListDirectory(directoryReader)
    val openDocument = OpenDocument(fileReader, ContentFormatDetector())
    val formatDocument = FormatJsonDocument()
    val saveDocumentCopy = SaveDocumentCopy(fileWriter)

    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val model = when (modelClass) {
                HomeViewModel::class.java -> HomeViewModel(settings)
                BrowserViewModel::class.java -> BrowserViewModel(listDirectory, settings)
                EditorViewModel::class.java -> EditorViewModel(openDocument, formatDocument, saveDocumentCopy)
                SettingsViewModel::class.java -> SettingsViewModel()
                else -> error("Unknown ViewModel: $modelClass")
            }
            return modelClass.cast(model)!!
        }
    }
}
