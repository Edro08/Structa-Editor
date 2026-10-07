package com.edro08.structa.ui.editor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.ui.screen.browser.BrowserScreen
import com.edro08.structa.ui.screen.browser.BrowserMode
import com.edro08.structa.ui.screen.browser.BrowserUiState
import com.edro08.structa.ui.theme.StructaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BrowserActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fileTapOpensAndMoreMenuOffersActionsForSelectedDocument() {
        val first = FileEntry(DocumentId("one"), "one.txt", 10, false)
        val second = FileEntry(DocumentId("two"), "two.txt", 20, false)
        val opened = mutableListOf<DocumentId>()
        compose.setContent {
            StructaTheme {
                BrowserScreen(BrowserUiState(stack = listOf(DocumentId("root")), entries = listOf(first, second)),
                    onBack = {}, onChooseFolder = {}, onQuery = {}, onEntry = { opened += it.id }, onRetry = {})
            }
        }

        compose.onNodeWithContentDescription("Acciones de one.txt").assertExists()
        compose.onNodeWithContentDescription("Abrir one.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Renombrar one.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Eliminar one.txt").assertDoesNotExist()
        compose.onNodeWithText("one.txt").performClick()
        compose.runOnIdle { assertEquals(listOf(first.id), opened) }
        compose.onNodeWithContentDescription("Acciones de two.txt").performClick()
        compose.onNodeWithText("Abrir two.txt").performClick()
        compose.runOnIdle { assertEquals(listOf(first.id, second.id), opened) }
    }

    @Test fun directoryPickerReusesBrowserWithoutFilesOrDestructiveActions() {
        val root = DocumentId("/storage/emulated/0")
        val directory = FileEntry(DocumentId("/storage/emulated/0/Documents"), "Documents", 0, true)
        val file = FileEntry(DocumentId("/storage/emulated/0/readme.txt"), "readme.txt", 10, false)
        val entered = mutableListOf<DocumentId>()
        var selected = false
        var created = false
        compose.setContent {
            StructaTheme {
                BrowserScreen(BrowserUiState(stack = listOf(root), entries = listOf(directory, file),
                    breadcrumbs = listOf("0")), onBack = {}, onChooseFolder = {}, onQuery = {},
                    onEntry = { entered += it.id }, onRetry = {}, onCreate = { _, isFolder -> created = isFolder },
                    mode = BrowserMode.SELECT_DIRECTORY, onSelectDirectory = { selected = true })
            }
        }
        compose.onNodeWithText("Seleccionar carpeta").assertExists()
        compose.onNodeWithText("readme.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Acciones de Documents").assertDoesNotExist()
        compose.onNodeWithText("Nuevo archivo").assertDoesNotExist()
        compose.onNodeWithText("Documents").performClick()
        compose.onNodeWithText("Nueva carpeta").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Proyecto")
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNodeWithText("Usar esta carpeta").performClick()
        compose.runOnIdle {
            assertEquals(listOf(directory.id), entered)
            assertEquals(true, created)
            assertEquals(true, selected)
        }
    }
}
