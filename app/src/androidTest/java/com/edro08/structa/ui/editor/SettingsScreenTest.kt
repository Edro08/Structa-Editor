package com.edro08.structa.ui.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.edro08.structa.domain.settings.EditorFont
import com.edro08.structa.ui.screen.settings.SettingsScreen
import com.edro08.structa.ui.screen.settings.SettingsUiState
import com.edro08.structa.ui.theme.StructaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cardsKeepThemeFontSizeAndProviderControlsOperable() {
        var dark = true
        var font = EditorFont.MONOSPACE
        var size = 14
        var provider = "SAF"
        compose.setContent {
            StructaTheme {
                SettingsScreen(SettingsUiState(), onBack = {}, onProvider = { provider = it },
                    onTheme = { dark = it }, onFont = { font = it }, onFontSize = { size = it })
            }
        }
        compose.onNodeWithText("Claro").performClick()
        compose.onNodeWithText("Sans monoespaciada").performClick()
        compose.onNode(SemanticsMatcher("slider") { it.config.contains(SemanticsActions.SetProgress) })
            .performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        compose.onNodeWithText("Shizuku").performClick()
        compose.runOnIdle {
            assertEquals(false, dark)
            assertEquals(EditorFont.SANS_MONOSPACE, font)
            assertEquals(20, size)
            assertEquals("Shizuku", provider)
        }
    }
}
