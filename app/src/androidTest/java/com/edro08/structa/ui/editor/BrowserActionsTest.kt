package com.edro08.structa.ui.editor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.ui.screen.browser.BrowserScreen
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
}
