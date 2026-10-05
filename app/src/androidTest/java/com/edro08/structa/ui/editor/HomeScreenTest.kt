package com.edro08.structa.ui.editor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.workspace.WorkspaceShortcut
import com.edro08.structa.ui.screen.home.HomeScreen
import com.edro08.structa.ui.screen.home.HomeUiState
import com.edro08.structa.ui.theme.StructaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cardOpensStarTogglesAndRemovingRequiresConfirmation() {
        val id = DocumentId("workspace")
        var opened: DocumentId? = null
        var favorite: DocumentId? = null
        var removed: DocumentId? = null
        compose.setContent {
            StructaTheme {
                HomeScreen(HomeUiState(recent = listOf(WorkspaceShortcut(id, "Proyecto API", null, 0L))),
                    onOpenWorkspace = { opened = it }, onChooseFolder = {},
                    onToggleFavorite = { favorite = it }, onRemoveFromHistory = { removed = it })
            }
        }
        compose.onNodeWithText("Proyecto API").performClick()
        assertEquals(id, opened)
        compose.onNodeWithContentDescription("Añadir a favoritos").performClick()
        assertEquals(id, favorite)
        compose.onNodeWithContentDescription("Quitar del historial").performClick()
        compose.onNodeWithText("¿Quieres quitar «Proyecto API» del historial de Structa? La carpeta real no se eliminará.").assertExists()
        assertNull(removed)
        compose.onNodeWithText("Cancelar").performClick()
        assertNull(removed)
        compose.onNodeWithContentDescription("Quitar del historial").performClick()
        compose.onNodeWithText("Quitar", substring = false).performClick()
        assertEquals(id, removed)
    }
}
