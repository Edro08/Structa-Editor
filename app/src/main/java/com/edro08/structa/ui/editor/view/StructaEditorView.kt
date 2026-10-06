package com.edro08.structa.ui.editor.view

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Canvas
import android.graphics.Rect
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.edro08.structa.domain.editor.EditorEngine
import com.edro08.structa.domain.editor.buffer.PieceTableBuffer
import com.edro08.structa.domain.editor.command.InsertTextCommand
import com.edro08.structa.domain.editor.command.MoveCursorCommand
import com.edro08.structa.domain.editor.command.SetSelectionCommand
import com.edro08.structa.domain.editor.cursor.TextOffset
import com.edro08.structa.ui.editor.input.*
import com.edro08.structa.ui.editor.model.EditorLine
import com.edro08.structa.ui.editor.model.EditorViewport
import com.edro08.structa.ui.editor.model.EditorViewState
import com.edro08.structa.ui.editor.model.WordWrapMode
import com.edro08.structa.ui.editor.model.WrappedLayout
import com.edro08.structa.ui.editor.render.EditorRenderer
import com.edro08.structa.ui.editor.render.EditorStyle
import com.edro08.structa.domain.editor.syntax.*
import com.edro08.structa.domain.editor.decoration.DecorationSet
import kotlinx.coroutines.*

/**
 * Canvas editor surface with command-based input and two-axis scrolling.
 * Content is read from the engine; this view never mutates the buffer.
 * After external commands, call [refresh] (or update the Compose contentVersion).
 */
class StructaEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val renderer = EditorRenderer(resources.displayMetrics.density) { sp ->
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    }
    private val scroller = OverScroller(context)
    private var engine: EditorEngine? = null
    private var input: EditorInputSession? = null
    private var connection: EditorInputConnection? = null
    private var keys: KeyBindingHandler? = null
    private var selectionMode: ActionMode? = null
    private var dragAnchor: Int? = null
    private var blinkVisible = true
    private val blink = object : Runnable {
        override fun run() {
            if (!hasFocus() || !editable || !isAttachedToWindow) return
            blinkVisible = !blinkVisible
            invalidate()
            postDelayed(this, 500)
        }
    }
    var onEditorAction: (EditorAction) -> Unit = {}
    var editable: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            isFocusable = value
            isFocusableInTouchMode = value
            if (!value) {
                connection?.closeConnection()
                connection = null
                selectionMode?.finish()
                clearFocus()
            }
        }
    private val inputChanged: (Boolean) -> Unit = {
        if (it) decorations = DecorationSet()
        refresh(it)
        revealCursor()
        resetBlink()
        notifyIme()
        selectionMode?.invalidate()
    }
    private var contentVersion = 0L
    private var viewState: EditorViewState? = null
    private var offsetX = 0f
    private var offsetY = 0f
    private var maxX = 0f
    private var maxY = 0f
    private var wrappedLayout: WrappedLayout? = null
    var wordWrapMode: WordWrapMode = WordWrapMode.OFF
        set(value) {
            if (field == value) return
            field = value
            scroller.forceFinished(true)
            isHorizontalScrollBarEnabled = value == WordWrapMode.OFF
            wrappedLayout = null
            updateViewport()
            revealCursor()
            invalidate()
        }
    private var syntaxScope: CoroutineScope? = null
    private var syntaxJob: Job? = null
    private var syntaxGeneration = 0L
    private var requestedSyntaxText: String? = null
    private var language: Language = LanguageRegistry.plain
    private class LargeSyntaxCache(val languageId: String, var serial: Long) {
        val checkpoints = java.util.TreeMap<Int, TokenizerState>().apply { put(0, TokenizerState()) }
        var window: SyntaxWindow? = null
        var requestedRange: IntRange? = null
    }
    private var largeSyntaxCache: LargeSyntaxCache? = null
    var syntaxSnapshot: SyntaxSnapshot? = null
        private set
    val visibleSyntaxWindow: SyntaxWindow? get() = largeSyntaxCache?.window
    var decorations: DecorationSet = DecorationSet()
        set(value) { field = value; invalidate() }

    var viewport = EditorViewport(0, -1, 0f, 0f)
        private set

    var style: EditorStyle = EditorStyle()
        set(value) {
            if (field == value) return
            field = value
            scroller.forceFinished(true)
            refresh()
            updateViewport()
            if (editable && hasFocus()) revealCursor()
        }

    var cursorVisible: Boolean = true
        set(value) { if (field != value) { field = value; invalidate() } }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            scrollToPosition(offsetX + distanceX, offsetY + distanceY)
            return true
        }

        override fun onFling(first: MotionEvent?, current: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (dragAnchor != null) return true
            scroller.fling(offsetX.toInt(), offsetY.toInt(), (-velocityX).toInt(), (-velocityY).toInt(),
                0, maxX.toInt(), 0, maxY.toInt())
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            if (editable && dragAnchor == null) {
                input?.execute(MoveCursorCommand(TextOffset(offsetAt(event.x, event.y))), false)
                selectionMode?.finish()
                showKeyboard()
            }
            return performClick()
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (!editable) return false
            selectWord(event.x, event.y)
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            if (editable) selectWord(event.x, event.y)
        }
    })

    init {
        isClickable = true
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Same engine/version retains cached lines and scroll; another engine starts at the top. */
    fun bind(editor: EditorEngine, version: Long = 0L, inputSession: EditorInputSession? = null,
        savedViewState: EditorViewState? = null, language: Language = LanguageRegistry.plain) {
        require(inputSession == null || inputSession.engine === editor)
        val changed = engine !== editor
        val viewChanged = viewState !== savedViewState
        val inputChanged = inputSession != null && input !== inputSession
        val languageChanged = this.language != language
        if (!changed && !inputChanged && !viewChanged && !languageChanged && contentVersion == version) return
        if (changed || viewChanged || languageChanged) {
            syntaxJob?.cancel()
            syntaxGeneration++
            requestedSyntaxText = null
            syntaxSnapshot = null
            largeSyntaxCache = null
        }
        this.language = language
        if (changed || inputChanged) {
            connection?.closeConnection()
            connection = null
            input?.removeListener(this.inputChanged)
            input = inputSession ?: EditorInputSession(editor)
            input?.addListener(this.inputChanged)
            keys = KeyBindingHandler(input!!, ::performEditorContextAction) { onEditorAction(it) }
            selectionMode?.finish()
        }
        engine = editor
        viewState = savedViewState
        contentVersion = version
        scroller.forceFinished(true)
        if (changed || viewChanged) {
            offsetX = savedViewState?.scrollX ?: 0f
            offsetY = savedViewState?.scrollY ?: 0f
        }
        refresh()
        if ((changed || inputChanged) && editable && hasFocus()) {
            context.getSystemService(InputMethodManager::class.java).restartInput(this)
        }
    }

    /** Invalidates visible line layouts after commands. Does not recreate the document. */
    fun refresh(contentChanged: Boolean = true) {
        renderer.invalidateContent()
        if (contentChanged) wrappedLayout = null
        scheduleSyntax()
        contentDescription = "Editor de código, ${engine?.document?.buffer?.lineCount ?: 0} líneas"
        invalidate()
    }

    private fun scheduleSyntax() {
        val editor = engine ?: return
        val scope = syntaxScope ?: return
        val buffer = editor.document.buffer
        if (language == LanguageRegistry.plain) {
            syntaxJob?.cancel()
            syntaxGeneration++
            requestedSyntaxText = null
            syntaxSnapshot = null
            largeSyntaxCache = null
            viewState?.syntax = null
            return
        }
        if (buffer.length > IncrementalHighlighter.MAX_TEXT_LENGTH) {
            if (requestedSyntaxText != null) {
                syntaxJob?.cancel()
                syntaxGeneration++
                requestedSyntaxText = null
            }
            syntaxSnapshot = null
            viewState?.syntax = null
            scheduleLargeSyntax(editor, buffer as? PieceTableBuffer ?: return, scope)
            return
        }
        largeSyntaxCache = null
        val text = buffer.getText(0, buffer.length).toString()
        if (requestedSyntaxText == text) return
        requestedSyntaxText = text
        syntaxJob?.cancel()
        val token = ++syntaxGeneration
        val previous = viewState?.syntax ?: syntaxSnapshot
        syntaxSnapshot = previous?.takeIf { it.text == text && it.languageId == language.id }
        if (syntaxSnapshot != null) return
        val requestedLanguage = language
        syntaxJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                IncrementalHighlighter.highlight(text, requestedLanguage, previous) { ensureActive() }
            }
            if (token == syntaxGeneration && engine === editor) {
                syntaxSnapshot = result
                viewState?.syntax = result
                invalidate()
            }
        }
    }

    private fun scheduleLargeSyntax(editor: EditorEngine, buffer: PieceTableBuffer, scope: CoroutineScope) {
        if (viewport.lastVisibleLine < viewport.firstVisibleLine) return
        var cache = largeSyntaxCache
        if (cache == null || cache.languageId != language.id) {
            cache = LargeSyntaxCache(language.id, buffer.changeSerial)
            largeSyntaxCache = cache
        }
        val firstChanged = buffer.firstChangedLineSince(cache.serial)
        if (firstChanged != null) {
            cache.serial = buffer.changeSerial
            cache.checkpoints.tailMap(firstChanged + 1, true).clear()
            if (cache.window?.let { firstChanged < it.firstLine + it.lines.size } == true) cache.window = null
            cache.requestedRange = null
            syntaxJob?.cancel()
            syntaxGeneration++
        }
        val metrics = renderer.metrics(buffer.lineCount, style)
        val wrap = if (wordWrapMode == WordWrapMode.VIEWPORT && width > 0) layoutForWrap(buffer, metrics) else null
        val first = wrap?.lineAt(viewport.firstVisibleLine) ?: viewport.firstVisibleLine
        val last = wrap?.lineAt(viewport.lastVisibleLine) ?: viewport.lastVisibleLine
        if (cache.window?.let { first >= it.firstLine && last < it.firstLine + it.lines.size } == true) return
        val range = (first - 32).coerceAtLeast(0)..(last + 64).coerceAtMost(buffer.lineCount - 1)
        if (cache.requestedRange == range) return
        cache.requestedRange = range
        syntaxJob?.cancel()
        val token = ++syntaxGeneration
        val serial = cache.serial
        val start = cache.checkpoints.floorKey(range.first) ?: 0
        val incoming = cache.checkpoints.getValue(start)
        // The buffer belongs to the UI thread. Workers receive only this immutable slice.
        val text = buffer.getText(buffer.getLineStart(start), buffer.getLineEnd(range.last)).toString()
        val requestedLanguage = language
        syntaxJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                ViewportHighlighter.highlight(text, start, range.first, requestedLanguage, incoming) { ensureActive() }
            }
            if (token == syntaxGeneration && engine === editor && buffer.changeSerial == serial && largeSyntaxCache === cache) {
                cache.checkpoints.putAll(result.checkpoints)
                cache.window = result.window
                invalidate()
            }
        }
    }

    fun scrollToPosition(x: Float, y: Float) {
        require(x.isFinite() && y.isFinite())
        // Prepare the target viewport before limiting horizontal movement to its known width.
        updateViewport(x, y)
        awakenScrollBars()
        invalidate()
    }

    private fun layoutForWrap(buffer: com.edro08.structa.domain.editor.buffer.TextBuffer,
        metrics: com.edro08.structa.ui.editor.render.EditorMetrics): WrappedLayout {
        val columns = ((width - metrics.gutterWidth - metrics.textPadding * 2 - metrics.cursorWidth) /
            metrics.characterWidth).toInt().coerceAtLeast(1)
        return wrappedLayout?.takeIf { it.columns == columns } ?: WrappedLayout(buffer, columns).also { wrappedLayout = it }
    }

    private fun updateViewport(x: Float = offsetX, y: Float = offsetY) {
        val buffer = engine?.document?.buffer ?: return
        val metrics = renderer.metrics(buffer.lineCount, style)
        val wrap = if (wordWrapMode == WordWrapMode.VIEWPORT && width > 0) layoutForWrap(buffer, metrics) else null
        val rowCount = wrap?.rowCount ?: buffer.lineCount
        maxY = EditorViewport.maxScrollY(rowCount, height.toFloat(), metrics.lineHeight)
        val requested = EditorViewport.calculate(rowCount, height.toFloat(), metrics.lineHeight, x, y)
        val logical = if (wrap != null && requested.lastVisibleLine >= requested.firstVisibleLine)
            requested.copy(firstVisibleLine = wrap.lineAt(requested.firstVisibleLine),
                lastVisibleLine = wrap.lineAt(requested.lastVisibleLine)) else requested
        renderer.prepare(buffer, logical)
        maxX = if (wrap == null) renderer.maxScrollX(width.toFloat(), metrics) else 0f
        offsetX = x.coerceIn(0f, maxX)
        offsetY = requested.scrollY
        viewport = requested.copy(scrollX = offsetX)
        viewState?.let {
            it.cursor = engine!!.cursor
            it.selection = engine!!.selection
            it.scrollX = offsetX
            it.scrollY = offsetY
        }
        if (buffer.length > IncrementalHighlighter.MAX_TEXT_LENGTH) scheduleSyntax()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // AndroidView's canvas may include Compose siblings. In particular,
        // drawColor fills the entire clip, not just this View's local bounds.
        val saved = canvas.save()
        try {
            canvas.clipRect(0, 0, width, height)
            val editor = engine
            if (editor == null) {
                canvas.drawColor(style.background)
                return
            }
            updateViewport()
            val metrics = renderer.metrics(editor.document.buffer.lineCount, style)
            val wrap = if (wordWrapMode == WordWrapMode.VIEWPORT && width > 0)
                layoutForWrap(editor.document.buffer, metrics) else null
            val logical = if (wrap != null && viewport.lastVisibleLine >= viewport.firstVisibleLine)
                viewport.copy(firstVisibleLine = wrap.lineAt(viewport.firstVisibleLine),
                    lastVisibleLine = wrap.lineAt(viewport.lastVisibleLine)) else viewport
            val lines = renderer.prepare(editor.document.buffer, logical)
            val showCursor = cursorVisible && (!editable || !hasFocus() || blinkVisible)
            if (wrap != null) renderer.drawWrapped(canvas, editor, lines, viewport, wrap, metrics,
                width.toFloat(), height.toFloat(), style, showCursor, input?.composition,
                syntaxSnapshot, decorations, largeSyntaxCache?.window)
            else renderer.draw(canvas, editor, lines, viewport, metrics, width.toFloat(), height.toFloat(),
                style, showCursor, input?.composition, syntaxSnapshot, decorations, largeSyntaxCache?.window)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scroller.forceFinished(true)
        updateViewport()
        // In particular, keep the active insertion position above the newly opened IME.
        if (editable && hasFocus() && (h < oldh || (wordWrapMode == WordWrapMode.VIEWPORT && w != oldw))) revealCursor()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (engine == null) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) dragAnchor = null
        if (editable && event.actionMasked == MotionEvent.ACTION_MOVE && dragAnchor != null) {
            parent?.requestDisallowInterceptTouchEvent(true)
            val inset = 32f * resources.displayMetrics.density
            val dx = if (event.x < inset) -inset else if (event.x > width - inset) inset else 0f
            val dy = if (event.y < inset) -inset else if (event.y > height - inset) inset else 0f
            scrollToPosition(offsetX + dx, offsetY + dy)
            input?.execute(SetSelectionCommand(TextOffset(dragAnchor!!), TextOffset(offsetAt(event.x, event.y))), false)
            return true
        }
        val handled = gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
            dragAnchor = null
        }
        return handled || event.actionMasked == MotionEvent.ACTION_CANCEL || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val editor = engine
        if (editor != null && event.action == MotionEvent.ACTION_SCROLL) {
            val step = renderer.metrics(editor.document.buffer.lineCount, style).lineHeight * 3
            scroller.forceFinished(true)
            scrollToPosition(offsetX - event.getAxisValue(MotionEvent.AXIS_HSCROLL) * step,
                offsetY - event.getAxisValue(MotionEvent.AXIS_VSCROLL) * step)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollToPosition(scroller.currX.toFloat(), scroller.currY.toFloat())
            postInvalidateOnAnimation()
        }
    }

    override fun computeVerticalScrollRange(): Int = (maxY + height).toInt()
    override fun computeVerticalScrollOffset(): Int = offsetY.toInt()
    override fun computeVerticalScrollExtent(): Int = height
    override fun computeHorizontalScrollRange(): Int = (maxX + width).toInt()
    override fun computeHorizontalScrollOffset(): Int = offsetX.toInt()
    override fun computeHorizontalScrollExtent(): Int = width

    override fun onDetachedFromWindow() {
        syntaxScope?.cancel()
        syntaxScope = null
        largeSyntaxCache?.requestedRange = null
        requestedSyntaxText = null
        syntaxGeneration++
        scroller.forceFinished(true)
        removeCallbacks(blink)
        connection?.closeConnection()
        connection = null
        input?.removeListener(inputChanged)
        selectionMode?.finish()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syntaxScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scheduleSyntax()
        input?.addListener(inputChanged)
        resetBlink()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) {
            connection?.closeConnection()
            connection = null
            input?.finishComposingText()
        }
        resetBlink()
    }

    private fun resetBlink() {
        removeCallbacks(blink)
        blinkVisible = true
        if (hasFocus() && editable && isAttachedToWindow) postDelayed(blink, 500)
        invalidate()
    }

    override fun onCheckIsTextEditor(): Boolean = editable

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val session = input ?: return null
        if (!editable) return null
        connection?.closeConnection()
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = session.anchor
        outAttrs.initialSelEnd = session.active
        outAttrs.setInitialSurroundingSubText(session.buffer.getText(
            (session.selectionStart - 1024).coerceAtLeast(0),
            (session.selectionEnd.toLong() + 1024).coerceAtMost(session.buffer.length.toLong()).toInt()),
            (session.selectionStart - 1024).coerceAtLeast(0))
        return EditorInputConnection(this, session).also { connection = it }
    }

    private fun notifyIme() {
        val session = input ?: return
        if (hasFocus()) context.getSystemService(InputMethodManager::class.java).updateSelection(this,
            session.anchor, session.active, session.composition?.start?.value ?: -1, session.composition?.end?.value ?: -1)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (editable && keys?.handle(event) == true) true else super.onKeyDown(keyCode, event)

    private fun showKeyboard() {
        requestFocus()
        windowInsetsController?.show(android.view.WindowInsets.Type.ime())
    }

    fun offsetAt(x: Float, y: Float): Int {
        val editor = engine ?: return 0
        val buffer = editor.document.buffer
        val metrics = renderer.metrics(buffer.lineCount, style)
        val row = ((y + offsetY) / metrics.lineHeight).toInt()
        val wrap = if (wordWrapMode == WordWrapMode.VIEWPORT && width > 0) layoutForWrap(buffer, metrics) else null
        val number = wrap?.lineAt(row) ?: row.coerceIn(0, buffer.lineCount - 1)
        val line = EditorLine(number, buffer.getLineStart(number), buffer.getLine(number).toString())
        val start = wrap?.segmentStart(row.coerceIn(0, wrap.rowCount - 1), number) ?: 0
        val column = if (x < metrics.gutterWidth) start.toFloat() else
            start + (x + offsetX - metrics.gutterWidth - metrics.textPadding) / metrics.characterWidth
        if (wrap != null) return line.startOffset + line.offsetAtColumn(column.coerceAtMost(
            (start + wrap.columns).toFloat()))
        return line.startOffset + line.offsetAtColumn(column)
    }

    private fun revealCursor() {
        val editor = engine ?: return
        if (width == 0 || height == 0) return
        val buffer = editor.document.buffer
        val metrics = renderer.metrics(buffer.lineCount, style)
        val number = buffer.getLineForOffset(editor.cursor.offset.value)
        val line = EditorLine(number, buffer.getLineStart(number), buffer.getLine(number).toString())
        val column = line.columnAt(editor.cursor.offset.value - line.startOffset)
        val wrap = if (wordWrapMode == WordWrapMode.VIEWPORT) layoutForWrap(buffer, metrics) else null
        val x = (column - (wrap?.let { column / it.columns * it.columns } ?: 0)) * metrics.characterWidth + metrics.textPadding
        val y = (wrap?.rowFor(number, column) ?: number) * metrics.lineHeight
        val availableWidth = (width - metrics.gutterWidth - metrics.textPadding).coerceAtLeast(metrics.characterWidth)
        val targetX = if (x < offsetX) x else if (x + metrics.cursorWidth > offsetX + availableWidth) x + metrics.cursorWidth - availableWidth else offsetX
        val targetY = if (y < offsetY) y else if (y + metrics.lineHeight > offsetY + height) y + metrics.lineHeight - height else offsetY
        scrollToPosition(if (wrap == null) targetX else 0f, targetY)
    }

    private fun selectWord(x: Float, y: Float) {
        val session = input ?: return
        val buffer = session.buffer
        if (buffer.length == 0) { showKeyboard(); return }
        val offset = offsetAt(x, y).coerceAtMost(buffer.length - 1)
        fun isWord(char: Char) = char.isLetterOrDigit() || char == '_'
        var start = offset
        var end = offset + 1
        if (isWord(buffer.charAt(offset))) {
            while (start > 0 && isWord(buffer.charAt(start - 1))) start--
            while (end < buffer.length && isWord(buffer.charAt(end))) end++
        }
        session.execute(SetSelectionCommand(TextOffset(start), TextOffset(end)), false)
        dragAnchor = start
        showKeyboard()
        showSelectionMenu()
    }

    private fun showSelectionMenu() {
        if (selectionMode != null) { selectionMode?.invalidate(); return }
        selectionMode = startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, android.R.id.selectAll, 0, "Seleccionar todo")
                menu.add(0, android.R.id.copy, 1, "Copiar")
                menu.add(0, android.R.id.cut, 2, "Cortar")
                menu.add(0, android.R.id.paste, 3, "Pegar")
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val selected = input?.engine?.selection != null
                menu.findItem(android.R.id.copy).isEnabled = selected
                menu.findItem(android.R.id.cut).isEnabled = selected
                return true
            }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val handled = performEditorContextAction(item.itemId)
                if (handled && item.itemId != android.R.id.selectAll) mode.finish()
                return handled
            }
            override fun onDestroyActionMode(mode: ActionMode) { selectionMode = null }
        }, ActionMode.TYPE_FLOATING)
    }

    fun performEditorContextAction(id: Int): Boolean {
        val session = input ?: return false
        if (!editable) return false
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        when (id) {
            android.R.id.selectAll -> session.selectAll()
            android.R.id.copy, android.R.id.cut -> {
                if (session.selectionStart == session.selectionEnd) return false
                clipboard.setPrimaryClip(ClipData.newPlainText("Structa", session.selectedText()))
                if (id == android.R.id.cut) session.execute(InsertTextCommand(""))
            }
            android.R.id.paste -> {
                val clip = clipboard.primaryClip ?: return false
                if (clip.itemCount == 0) return false
                val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return false
                session.execute(InsertTextCommand(text))
            }
            else -> return false
        }
        return true
    }
}
