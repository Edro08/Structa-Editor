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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.ui.component.MessageOverlay
import com.edro08.structa.ui.navigation.*
import com.edro08.structa.ui.screen.browser.*
import com.edro08.structa.ui.screen.editor.*
import com.edro08.structa.ui.screen.home.*
import com.edro08.structa.ui.screen.settings.*
import com.edro08.structa.ui.theme.StructaTheme
import kotlinx.coroutines.CancellationException

@Composable
fun StructaApp() {
    val activity = LocalContext.current as ComponentActivity
    val container = remember { AppContainer(activity.applicationContext) }
    val home: HomeViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val browser: BrowserViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val editor: EditorViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val settings: SettingsViewModel = viewModel(viewModelStoreOwner = activity, factory = container.factory)
    val homeState by home.state.collectAsStateWithLifecycle()
    val browserState by browser.state.collectAsStateWithLifecycle()
    val editorState by editor.state.collectAsStateWithLifecycle()
    val settingsState by settings.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var platformMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: SecurityException) {
                platformMessage = "El proveedor solo permite acceso temporal a esta carpeta."
            }
            browser.selectFolder(DocumentId(uri.toString()))
            home.refresh()
            screen = Screen.BROWSER
        }
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        editor.completeSave(uri?.let { DocumentId(it.toString()) })
    }

    StructaTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(bottomBar = { AppNavigation(screen) { screen = it } }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(homeState, onOpenLastFolder = { screen = Screen.BROWSER },
                            onChooseFolder = { folderPicker.launch(null) })
                        Screen.BROWSER -> BrowserScreen(browserState,
                            onBack = { if (!browser.back()) screen = Screen.HOME },
                            onChooseFolder = { folderPicker.launch(null) }, onQuery = browser::setQuery,
                            onEntry = { entry ->
                                if (entry.isDirectory) browser.enter(entry) else {
                                    editor.open(entry)
                                    screen = Screen.EDITOR
                                }
                            }, onRetry = browser::refresh)
                        Screen.EDITOR -> EditorScreen(editorState, onBack = { screen = Screen.BROWSER },
                            onExplore = { screen = Screen.BROWSER }, onMode = editor::setMode,
                            onMessage = editor::showMessage,
                            onSearch = editor::setSearch, onFormat = editor::format, onUndo = editor::undo,
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
                            onProvider = settings::selectProvider)
                    }
                    editorState.message?.let { MessageOverlay(it, editor::dismissMessage) }
                    platformMessage?.let { MessageOverlay(it) { platformMessage = null } }
                }
            }
        }
    }
}
