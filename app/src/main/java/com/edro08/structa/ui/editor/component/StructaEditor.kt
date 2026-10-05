package com.edro08.structa.ui.editor.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import com.edro08.structa.domain.editor.syntax.LanguageRegistry
import com.edro08.structa.domain.editor.search.SearchMatch
import com.edro08.structa.domain.editor.decoration.*
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.domain.editor.cursor.TextRange
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.ui.editor.render.EditorStyle
import com.edro08.structa.ui.editor.view.StructaEditorView
import com.edro08.structa.ui.editor.input.EditorInputSession
import com.edro08.structa.ui.editor.input.EditorAction
import com.edro08.structa.ui.editor.model.EditorViewState
import com.edro08.structa.domain.settings.EditorFont

/** Input and toolbar share the same session; engine commands remain the only mutation path. */
@Composable
fun StructaEditor(
    engine: EditorEngine,
    modifier: Modifier = Modifier,
    contentVersion: Long = 0L,
    cursorVisible: Boolean = true,
    editable: Boolean = false,
    inputSession: EditorInputSession? = null,
    viewState: EditorViewState? = null,
    fileName: String = "",
    searchMatches: List<SearchMatch> = emptyList(),
    selectedMatch: Int = -1,
    font: EditorFont = EditorFont.MONOSPACE,
    fontSize: Int = 14,
    onEditorAction: (EditorAction) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val language = remember(fileName) { LanguageRegistry.forFileName(fileName) }
    val decorations = remember(searchMatches, selectedMatch) {
        DecorationSet(searchMatches.mapIndexed { index, match ->
            Decoration(TextRange(TextOffset(match.start), TextOffset(match.end)),
                if (index == selectedMatch) DecorationType.SELECTED_OCCURRENCE else DecorationType.SEARCH_MATCH)
        })
    }
    val style = EditorStyle(
        font = font,
        textSizeSp = fontSize.toFloat(),
        background = colors.surface.toArgb(),
        foreground = colors.onSurface.toArgb(),
        gutterBackground = colors.surfaceVariant.toArgb(),
        gutterForeground = colors.onSurfaceVariant.toArgb(),
        currentLine = colors.onSurface.copy(alpha = 0.06f).toArgb(),
        selection = colors.primary.copy(alpha = 0.3f).toArgb(),
        cursor = colors.primary.toArgb(),
        keyword = if (dark) 0xFFC792EA.toInt() else 0xFF75319B.toInt(),
        string = if (dark) 0xFFC3E88D.toInt() else 0xFF38651C.toInt(),
        number = if (dark) 0xFFF78C6C.toInt() else 0xFF9D3B18.toInt(),
        comment = if (dark) 0xFF9CA7B0.toInt() else 0xFF59666F.toInt(),
        operator = if (dark) 0xFF89DDFF.toInt() else 0xFF006781.toInt(),
        key = if (dark) 0xFFFFCB6B.toInt() else 0xFF805500.toInt(),
        heading = if (dark) 0xFF82AAFF.toInt() else 0xFF2655A0.toInt()
    )
    AndroidView(
        factory = { context -> StructaEditorView(context) },
        modifier = modifier,
        update = { view ->
            view.style = style
            view.cursorVisible = cursorVisible
            view.editable = editable
            view.onEditorAction = onEditorAction
            view.bind(engine, contentVersion, inputSession, viewState, language)
            view.decorations = decorations
        }
    )
}
