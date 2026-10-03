package com.edro08.structa.ui.editor.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.ui.editor.render.EditorStyle
import com.edro08.structa.ui.editor.view.StructaEditorView
import com.edro08.structa.ui.editor.input.EditorInputSession
import com.edro08.structa.ui.editor.input.EditorAction

/** Input and toolbar share the same session; engine commands remain the only mutation path. */
@Composable
fun StructaEditor(
    engine: EditorEngine,
    modifier: Modifier = Modifier,
    contentVersion: Long = 0L,
    cursorVisible: Boolean = true,
    editable: Boolean = false,
    inputSession: EditorInputSession? = null,
    onEditorAction: (EditorAction) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val style = EditorStyle(
        background = colors.surface.toArgb(),
        foreground = colors.onSurface.toArgb(),
        gutterBackground = colors.surfaceVariant.toArgb(),
        gutterForeground = colors.onSurfaceVariant.toArgb(),
        currentLine = colors.onSurface.copy(alpha = 0.06f).toArgb(),
        selection = colors.primary.copy(alpha = 0.3f).toArgb(),
        cursor = colors.primary.toArgb()
    )
    AndroidView(
        factory = { context -> StructaEditorView(context) },
        modifier = modifier,
        update = { view ->
            view.style = style
            view.cursorVisible = cursorVisible
            view.editable = editable
            view.onEditorAction = onEditorAction
            view.bind(engine, contentVersion, inputSession)
        }
    )
}
