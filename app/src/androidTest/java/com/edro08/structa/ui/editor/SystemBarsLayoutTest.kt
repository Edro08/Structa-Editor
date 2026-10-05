package com.edro08.structa.ui.editor

import android.view.WindowInsets
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import com.edro08.structa.MainActivity
import com.edro08.structa.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SystemBarsLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun nestedScreenHeaderAndNavigationRespectSystemBars() {
        compose.activity.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        }
        compose.waitForIdle()

        val decor = compose.activity.window.decorView
        val insets = requireNotNull(decor.rootWindowInsets)
        val statusBottom = insets.getInsets(WindowInsets.Type.statusBars()).top
        val navigationTop = decor.height - insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        val density = compose.activity.resources.displayMetrics.density

        compose.onNodeWithText(compose.activity.getString(R.string.nav_browser)).performClick()
        assertTitleBelowStatus(compose.activity.getString(R.string.browser_title), statusBottom, density)
        compose.onNodeWithText(compose.activity.getString(R.string.nav_editor)).performClick()
        val editorBack = compose.onNodeWithContentDescription(compose.activity.getString(R.string.common_back))
        assertTrue("Editor header should follow the status bar",
            editorBack.fetchSemanticsNode().boundsInWindow.top < statusBottom + 64 * density)
        editorBack.performClick()

        val settings = compose.activity.getString(R.string.nav_settings)
        compose.onNodeWithText(settings).performClick()
        assertTitleBelowStatus(settings, statusBottom, density)
        val navigationLabelBottom = compose.onNodeWithText(compose.activity.getString(R.string.nav_home))
            .fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("App navigation should end above system navigation", navigationLabelBottom < navigationTop)
    }

    private fun assertTitleBelowStatus(title: String, statusBottom: Int, density: Float) {
        val titleTop = compose.onAllNodesWithText(title).fetchSemanticsNodes().minOf { it.boundsInWindow.top }
        assertTrue("$title should follow the status bar without an extra inset", titleTop < statusBottom + 64 * density)
    }
}
