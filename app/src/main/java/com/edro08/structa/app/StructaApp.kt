package com.edro08.structa.app

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.edro08.structa.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.ui.component.MessageOverlay
import com.edro08.structa.ui.navigation.*
import com.edro08.structa.ui.screen.browser.*
import com.edro08.structa.ui.screen.editor.*
import com.edro08.structa.ui.screen.home.*
import com.edro08.structa.ui.screen.settings.*
import com.edro08.structa.ui.theme.StructaTheme
import com.edro08.structa.ui.theme.StructaSystemBars
import kotlinx.coroutines.CancellationException

@Composable
fun StructaApp() {
    val activity = LocalContext.current as ComponentActivity
    val container = remember { AppContainer(activity.applicationContext) }
    val home: HomeViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val browser: BrowserViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val editor: EditorViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val quickOpen: QuickOpenViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val settings: SettingsViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val homeState by home.state.collectAsStateWithLifecycle()
    val browserState by browser.state.collectAsStateWithLifecycle()
    val editorState by editor.state.collectAsStateWithLifecycle()
    val quickState by quickOpen.state.collectAsStateWithLifecycle()
    val settingsState by settings.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var platformMessage by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(editorState.tabs, browserState.workspace?.id) {
        browser.setOpenDocuments(editorState.tabs.map { it.documentId })
    }
    LaunchedEffect(browserState.workspace?.root) { quickOpen.setRoot(browserState.workspace?.root?.id) }
    DisposableEffect(activity, editor) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) editor.checkpointSession()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { editor.checkpointSession(); activity.lifecycle.removeObserver(observer) }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: SecurityException) {
                try {
                    activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    platformMessage = R.string.error_temporary_folder_access
                }
            }
            browser.selectFolder(DocumentId(uri.toString()))
            home.opened(DocumentId(uri.toString()))
            screen = Screen.BROWSER
        }
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        editor.completeSave(uri?.let { DocumentId(it.toString()) })
    }

    StructaTheme(darkTheme = settingsState.darkTheme) {
        StructaSystemBars(activity, settingsState.darkTheme)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(bottomBar = {
                if (screen != Screen.EDITOR) AppNavigation(screen) { screen = it }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    when (screen) {
                         Screen.HOME -> HomeScreen(homeState, onOpenWorkspace = { id ->
                             browser.selectFolder(id)
                             home.opened(id)
                             screen = Screen.BROWSER
                         }, onChooseFolder = { folderPicker.launch(null) },
                             onToggleFavorite = home::toggleFavorite, onRemoveFromHistory = home::remove)
                        Screen.BROWSER -> BrowserScreen(browserState,
                            onBack = { if (!browser.back()) screen = Screen.HOME },
                            onChooseFolder = { folderPicker.launch(null) }, onQuery = browser::setQuery,
                            onEntry = { entry ->
                                if (entry.isDirectory) browser.enter(entry) else {
                                    editor.open(entry)
                                    screen = Screen.EDITOR
                                }
                             }, onRetry = browser::refresh, onToggle = browser::toggleFolder,
                             onCreate = browser::create, onRename = browser::rename, onDelete = browser::delete)
                        Screen.EDITOR -> EditorScreen(editorState, onBack = { screen = Screen.BROWSER },
                            editorFont = settingsState.editorFont, editorFontSize = settingsState.editorFontSize,
                            onExplore = { screen = Screen.BROWSER }, onMode = editor::setMode,
                             onMessage = editor::showMessage,
                             onSave = editor::save, onSelectDocument = editor::selectDocument,
                             onCloseDocument = editor::requestClose, onCancelClose = editor::cancelClose,
                             onDiscardClose = editor::discardAndClose, onSaveClose = editor::saveAndClose,
                             onSearch = editor::setSearch, onFormat = editor::format, onUndo = editor::undo,
                             onSearchOptions = editor::setSearchOptions, onReplacement = editor::setReplacement,
                             onFindNext = editor::findNext, onReplace = editor::replace, onQuickOpen = quickOpen::open,
                            onRedo = editor::redo, onLine = editor::goToLine, onSaveAs = {
                                editor.prepareSave()?.let { snapshot ->
                                    try {
                                        saveAs.launch(snapshot.name)
                                    } catch (exception: CancellationException) {
                                        throw exception
                                    } catch (exception: Exception) {
                                        editor.completeSave(null)
                                        editor.showMessage("No se pudo iniciar el guardado: ${exception.message}")
                                    }
                                }
                            })
                        Screen.SETTINGS -> SettingsScreen(settingsState, onBack = { screen = Screen.HOME },
                            onProvider = settings::selectProvider, onTheme = settings::selectTheme,
                            onFont = settings::selectFont, onFontSize = settings::selectFontSize)
                    }
                    editorState.message?.let { MessageOverlay(it, editor::dismissMessage) }
                     if (quickState.visible) ProductivityPicker(stringResource(R.string.editor_open), quickState.query, quickOpen::setQuery,
                        quickState.files.map { it.entry.name to it.relativePath },
                         quickState.message ?: pluralStringResource(R.plurals.editor_indexed_count,
                             quickState.indexedCount, quickState.indexedCount),
                        quickState.indexing, onChoose = { index ->
                            val entry = quickState.files[index].entry
                            quickOpen.close()
                            editor.open(entry)
                            screen = Screen.EDITOR
                        }, onDismiss = quickOpen::close)
                     if (platformMessage != 0) MessageOverlay(stringResource(platformMessage)) { platformMessage = 0 }
                }
            }
        }
    }
}
