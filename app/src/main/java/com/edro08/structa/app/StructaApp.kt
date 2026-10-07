package com.edro08.structa.app

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
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
import com.edro08.structa.data.filesystem.externalDocument
import com.edro08.structa.data.filesystem.direct.DirectFileRef
import com.edro08.structa.domain.filesystem.FileRef

@Composable
fun StructaApp(openIntent: Intent? = null, onOpenIntentHandled: (Intent) -> Unit = {}) {
    val activity = LocalContext.current as ComponentActivity
    val container = remember { AppContainer(activity.applicationContext) }
    val home: HomeViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val browser: BrowserViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val directPicker: BrowserViewModel = viewModel(key = "direct-directory-picker", viewModelStoreOwner = activity,
        factory = container.factory)
    val editor: EditorViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val quickOpen: QuickOpenViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val settings: SettingsViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val homeState by home.state.collectAsStateWithLifecycle()
    val browserState by browser.state.collectAsStateWithLifecycle()
    val pickerState by directPicker.state.collectAsStateWithLifecycle()
    val editorState by editor.state.collectAsStateWithLifecycle()
    val quickState by quickOpen.state.collectAsStateWithLifecycle()
    val settingsState by settings.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var platformMessage by rememberSaveable { mutableIntStateOf(0) }
    var directAuthorized by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    var pendingDirectPicker by rememberSaveable { mutableStateOf(false) }
    var pendingDirectWorkspace by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerReturnScreen by rememberSaveable { mutableStateOf(Screen.SETTINGS) }
    LaunchedEffect(openIntent) {
        if (openIntent != null) {
            try {
                val uri = openIntent.data ?: throw IllegalArgumentException("Missing document URI")
                val entry = externalDocument(activity, uri)
                if (entry.isDirectory) throw IllegalArgumentException("Not a file")
                val grants = openIntent.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (openIntent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 && grants != 0) {
                    try {
                        activity.contentResolver.takePersistableUriPermission(uri, grants)
                    } catch (_: SecurityException) {
                        if (grants and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                            try {
                                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (_: SecurityException) { /* This URI only has a temporary grant. */ }
                        }
                    }
                }
                editor.openExternal(entry)
                screen = Screen.EDITOR
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                platformMessage = R.string.error_open_external_file
            } finally {
                onOpenIntentHandled(openIntent)
            }
        }
    }
    LaunchedEffect(editorState.tabs, browserState.workspace?.id) {
        browser.setOpenDocuments(editorState.tabs.map { it.documentId })
    }
    LaunchedEffect(browserState.workspace?.root) { quickOpen.setRoot(browserState.workspace?.root?.id) }
    DisposableEffect(activity, editor) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) editor.checkpointSession()
            if (event == Lifecycle.Event.ON_RESUME) directAuthorized = Environment.isExternalStorageManager()
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
    fun openDirectPicker() {
        pickerReturnScreen = screen
        directPicker.selectFolder(DirectFileRef(Environment.getExternalStorageDirectory().absolutePath).ref, persist = false)
        screen = Screen.SELECT_DIRECTORY
    }
    val allFilesPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        directAuthorized = Environment.isExternalStorageManager()
        if (pendingDirectPicker && directAuthorized) openDirectPicker()
        if (directAuthorized) pendingDirectWorkspace?.let { path ->
            val id = DocumentId(path)
            browser.selectFolder(home.reference(id))
            home.opened(id)
            screen = Screen.BROWSER
        }
        pendingDirectWorkspace = null
        pendingDirectPicker = false
    }
    fun requestDirect(openPicker: Boolean) {
        directAuthorized = Environment.isExternalStorageManager()
        if (directAuthorized) {
            if (openPicker) openDirectPicker()
        } else {
            pendingDirectPicker = openPicker
            allFilesPermission.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${activity.packageName}")))
        }
    }
    fun chooseWorkspace() {
        if (settingsState.provider == "DIRECT") requestDirect(true) else folderPicker.launch(null)
    }

    StructaTheme(darkTheme = settingsState.darkTheme) {
        StructaSystemBars(activity, settingsState.darkTheme)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(bottomBar = {
                if (screen != Screen.EDITOR && screen != Screen.SELECT_DIRECTORY) AppNavigation(screen) { screen = it }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    when (screen) {
                         Screen.HOME -> HomeScreen(homeState, onOpenWorkspace = { id ->
                              if (home.reference(id).fileSystem == "direct" && !Environment.isExternalStorageManager()) {
                                  pendingDirectWorkspace = id.value
                                  requestDirect(false)
                              } else {
                                  browser.selectFolder(home.reference(id))
                                  home.opened(id)
                                  screen = Screen.BROWSER
                              }
                          }, onChooseFolder = ::chooseWorkspace,
                             onToggleFavorite = home::toggleFavorite, onRemoveFromHistory = home::remove)
                        Screen.BROWSER -> BrowserScreen(browserState,
                            onBack = { if (!browser.back()) screen = Screen.HOME },
                             onChooseFolder = ::chooseWorkspace, onQuery = browser::setQuery,
                            onEntry = { entry ->
                                if (entry.isDirectory) browser.enter(entry) else {
                                    editor.open(entry)
                                    screen = Screen.EDITOR
                                }
                             }, onRetry = browser::refresh, onToggle = browser::toggleFolder,
                              onCreate = browser::create, onRename = browser::rename, onDelete = browser::delete)
                         Screen.SELECT_DIRECTORY -> BrowserScreen(pickerState,
                             onBack = { if (!directPicker.back()) screen = pickerReturnScreen },
                             onChooseFolder = {}, onQuery = directPicker::setQuery,
                             onEntry = { if (it.isDirectory) directPicker.enter(it) },
                             onRetry = directPicker::refresh, onCreate = directPicker::create,
                             mode = BrowserMode.SELECT_DIRECTORY, fileSystem = container.fileSystem,
                             root = pickerState.workspace?.root, onBreadcrumb = directPicker::navigateTo,
                             onSelectDirectory = {
                                 if (Environment.isExternalStorageManager() && !pickerState.loading && pickerState.error == null) {
                                     pickerState.stack.lastOrNull()?.let { id ->
                                         browser.selectFolder(FileRef(id, "direct"))
                                         home.opened(id)
                                         screen = Screen.BROWSER
                                     }
                                 } else { directAuthorized = false; screen = Screen.SETTINGS }
                             })
                        Screen.EDITOR -> EditorScreen(editorState, onBack = { screen = Screen.BROWSER },
                             editorFont = settingsState.editorFont, editorFontSize = settingsState.editorFontSize,
                             onExplore = { screen = Screen.BROWSER }, onLanguage = editor::setLanguage,
                             onMessage = editor::showMessage,
                              onSave = editor::save, onSaveAll = editor::saveAll, onSelectDocument = editor::selectDocument,
                              onCloseDocument = editor::requestClose, onCloseAll = editor::requestCloseAll,
                              onCloseOthers = editor::requestCloseOthers, onCancelClose = editor::cancelClose,
                             onDiscardClose = editor::discardAndClose, onSaveClose = editor::saveAndClose,
                             onSearch = editor::setSearch, onFormat = editor::format, onUndo = editor::undo,
                             onSearchOptions = editor::setSearchOptions, onReplacement = editor::setReplacement,
                             onFindNext = editor::findNext, onReplace = editor::replace, onQuickOpen = quickOpen::open,
                             onRedo = editor::redo, onLine = editor::goToLine,
                             onGoToStart = editor::goToStart, onGoToEnd = editor::goToEnd, onSaveAs = {
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
                             onProvider = { provider ->
                                 settings.selectProvider(provider)
                                 if (provider == "DIRECT") requestDirect(true)
                             }, onTheme = settings::selectTheme,
                             onFont = settings::selectFont, onFontSize = settings::selectFontSize,
                             directAuthorized = directAuthorized, onManagePermission = { requestDirect(false) })
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
