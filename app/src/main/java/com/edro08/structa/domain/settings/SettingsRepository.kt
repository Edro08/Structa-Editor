package com.edro08.structa.domain.settings

import com.edro08.structa.domain.document.DocumentId

enum class EditorFont(val label: String, val family: String) {
    MONOSPACE("Monoespaciada", "monospace"),
    SANS_MONOSPACE("Sans monoespaciada", "sans-serif-monospace")
}

interface SettingsRepository {
    fun lastFolder(): DocumentId?
    fun setLastFolder(folder: DocumentId)
    fun darkTheme(): Boolean = true
    fun setDarkTheme(dark: Boolean) = Unit
    fun editorFont(): EditorFont = EditorFont.MONOSPACE
    fun setEditorFont(font: EditorFont) = Unit
    fun editorFontSize(): Int = 14
    fun setEditorFontSize(size: Int) = Unit
}
