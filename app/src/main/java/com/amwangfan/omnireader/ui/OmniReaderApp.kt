package com.amwangfan.omnireader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amwangfan.omnireader.AppScreen
import com.amwangfan.omnireader.AppViewModel
import com.amwangfan.omnireader.data.BookDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniReaderApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDownload by remember { mutableStateOf<BookDto?>(null) }
    var pendingUpdate by remember { mutableStateOf<BookDto?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importBook)
    }
    val defaultFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            persistTreePermission(context, it)
            viewModel.setDefaultDownloadTree(it.toString())
        }
    }
    val oneTimeFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val book = pendingDownload
        pendingDownload = null
        if (uri != null && book != null) {
            persistTreePermission(context, uri)
            viewModel.download(book, uri.toString())
        }
    }

    val launchImport = {
        importLauncher.launch(arrayOf("application/epub+zip", "application/octet-stream"))
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.lastSyncMessage) {
        state.lastSyncMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    val updateFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val book = pendingUpdate
        pendingUpdate = null
        if (uri != null && book != null) {
            persistTreePermission(context, uri)
            viewModel.updateBook(book, uri.toString())
        }
    }
    LaunchedEffect(state.readerNotice) {
        state.readerNotice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeReaderNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle(state.screen)) },
                navigationIcon = {
                    if (state.screen == AppScreen.Reader) {
                        IconButton(onClick = viewModel::showShelf) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to shelf")
                        }
                    }
                },
                actions = {
                    if (state.screen == AppScreen.Shelf || state.screen == AppScreen.Reader) {
                        IconButton(onClick = launchImport, enabled = !state.isBusy) {
                            Icon(Icons.Outlined.FileOpen, contentDescription = "Import EPUB")
                        }
                    }
                    if (state.screen == AppScreen.Reader) {
                        val openBook = state.localBooks.firstOrNull {
                            it.id == state.reader?.localBookId
                        }
                        if (openBook != null) {
                            IconButton(
                                onClick = { viewModel.requestDelete(openBook) },
                                enabled = !state.isBusy,
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete local copy")
                            }
                        }
                    }
                    if (state.screen == AppScreen.Library && state.accessToken.isNotBlank()) {
                        IconButton(onClick = viewModel::sync, enabled = !state.isBusy) {
                            Icon(Icons.Outlined.Sync, contentDescription = "Sync library")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.screen != AppScreen.Reader) {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.screen == AppScreen.Shelf,
                        onClick = viewModel::showShelf,
                        icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                        label = { Text("Shelf") },
                    )
                    NavigationBarItem(
                        selected = state.screen == AppScreen.Library,
                        onClick = viewModel::showLibrary,
                        icon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                        label = { Text("Online") },
                    )
                    NavigationBarItem(
                        selected = state.screen == AppScreen.Settings,
                        onClick = viewModel::showSettings,
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val screenPadding = PaddingValues(0.dp)
            when (state.screen) {
                AppScreen.Shelf -> ShelfScreen(
                    state = state,
                    padding = screenPadding,
                    onImport = launchImport,
                    onOpen = viewModel::openBook,
                    onDelete = viewModel::requestDelete,
                )
                AppScreen.Library -> OnlineLibraryScreen(
                    state = state,
                    padding = screenPadding,
                    onSettings = viewModel::showSettings,
                    onSync = viewModel::sync,
                    onRead = viewModel::openBook,
                    onUpdate = { book -> viewModel.updateBook(book) },
                    onChooseUpdateFolder = { book ->
                        pendingUpdate = book
                        updateFolderLauncher.launch(null)
                    },
                    onDownloadDefault = viewModel::download,
                    onChooseFolder = { book ->
                        pendingDownload = book
                        oneTimeFolderLauncher.launch(null)
                    },
                )
                AppScreen.Settings -> SettingsScreen(
                    state = state,
                    padding = screenPadding,
                    onServerChanged = viewModel::updateServerUrl,
                    onSaveServer = viewModel::saveServerUrl,
                    onUsernameChanged = viewModel::updateUsername,
                    onPasswordChanged = viewModel::updatePassword,
                    onLogin = viewModel::login,
                    onLogout = viewModel::logout,
                    onChooseFolder = { defaultFolderLauncher.launch(null) },
                    onResetFolder = viewModel::clearDefaultDownloadTree,
                    onAutoUploadChanged = viewModel::setAutoUploadImports,
                )
                AppScreen.Reader -> ReaderScreen(
                    state = state,
                    padding = screenPadding,
                    onPrevious = viewModel::previousChapter,
                    onNext = viewModel::nextChapter,
                    onCheckpoint = viewModel::checkpointReading,
                    onVisiblePosition = viewModel::updateVisibleReadingPosition,
                    onActiveChanged = viewModel::readerActive,
                    onDisposed = viewModel::clearReaderAfterDispose,
                )
            }
        }
    }

    state.pendingDeleteBook?.let { book ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete local copy?") },
            text = { Text("This removes " + book.title + " from this device. The source file and server copy are kept.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            },
        )
    }
}

private fun screenTitle(screen: AppScreen): String = when (screen) {
    AppScreen.Shelf -> "Local shelf"
    AppScreen.Library -> "Online library"
    AppScreen.Settings -> "Settings"
    AppScreen.Reader -> "Reader"
}

private fun persistTreePermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}
