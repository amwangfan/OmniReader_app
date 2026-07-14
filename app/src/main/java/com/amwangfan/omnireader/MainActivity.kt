package com.amwangfan.omnireader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amwangfan.omnireader.data.BookDto
import com.amwangfan.omnireader.data.LocalBook

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OmniReaderApp()
                }
            }
        }
    }
}

@Composable
fun OmniReaderApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.lastSyncMessage) {
        state.lastSyncMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    AppScaffold(
        state = state,
        snackbarHostState = snackbarHostState,
        onServer = viewModel::showServerConfig,
        onLogout = viewModel::logout,
        onLibrary = viewModel::showLibrary,
        onShelf = viewModel::showShelf,
        onSync = viewModel::sync,
        content = { padding ->
            when (state.screen) {
                AppScreen.ServerConfig -> ServerConfigScreen(
                    state = state,
                    padding = padding,
                    onServerChanged = viewModel::updateServerUrl,
                    onSave = viewModel::saveServerUrl,
                )
                AppScreen.Login -> LoginScreen(
                    state = state,
                    padding = padding,
                    onUsernameChanged = viewModel::updateUsername,
                    onPasswordChanged = viewModel::updatePassword,
                    onLogin = viewModel::login,
                    onServer = viewModel::showServerConfig,
                )
                AppScreen.Library -> LibraryScreen(
                    state = state,
                    padding = padding,
                    onSync = viewModel::sync,
                    onDownload = viewModel::download,
                    onOpenLocal = viewModel::openBook,
                )
                AppScreen.Shelf -> ShelfScreen(
                    state = state,
                    padding = padding,
                    onOpenLocal = viewModel::openBook,
                )
                AppScreen.Reader -> ReaderScreen(
                    state = state,
                    padding = padding,
                    onBack = viewModel::showShelf,
                    onPrevious = viewModel::previousChapter,
                    onNext = viewModel::nextChapter,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    state: AppUiState,
    snackbarHostState: SnackbarHostState,
    onServer: () -> Unit,
    onLogout: () -> Unit,
    onLibrary: () -> Unit,
    onShelf: () -> Unit,
    onSync: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val loggedIn = state.accessToken.isNotBlank()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OmniReader") },
                actions = {
                    if (loggedIn) {
                        TextButton(onClick = onSync, enabled = !state.isBusy) { Text("Sync") }
                        TextButton(onClick = onLogout) { Text("Logout") }
                    }
                    TextButton(onClick = onServer) { Text("Server") }
                },
            )
        },
        bottomBar = {
            if (loggedIn && state.screen != AppScreen.Reader) {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.screen == AppScreen.Library,
                        onClick = onLibrary,
                        label = { Text("Library") },
                        icon = { Text("All") },
                    )
                    NavigationBarItem(
                        selected = state.screen == AppScreen.Shelf,
                        onClick = onShelf,
                        label = { Text("Shelf") },
                        icon = { Text("Local") },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            content(padding)
        }
    }
}

@Composable
private fun ServerConfigScreen(
    state: AppUiState,
    padding: PaddingValues,
    onServerChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Server configuration", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = onServerChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server address") },
            placeholder = { Text("http://100.114.93.90:18080") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Button(onClick = onSave, enabled = !state.isBusy) {
            Text("Save server")
        }
    }
}

@Composable
private fun LoginScreen(
    state: AppUiState,
    padding: PaddingValues,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onServer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Login", style = MaterialTheme.typography.headlineSmall)
        Text(state.serverUrl, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Username") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onLogin, enabled = !state.isBusy) {
                Text("Login")
            }
            OutlinedButton(onClick = onServer) {
                Text("Change server")
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: AppUiState,
    padding: PaddingValues,
    onSync: () -> Unit,
    onDownload: (BookDto) -> Unit,
    onOpenLocal: (LocalBook) -> Unit,
) {
    val localById = state.localBooks.associateBy { it.id }
    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Server library", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = onSync, enabled = !state.isBusy) {
                    Text("Refresh")
                }
            }
        }
        if (state.remoteBooks.isEmpty()) {
            item { EmptyMessage("No server books yet.") }
        } else {
            items(state.remoteBooks, key = { it.id }) { book ->
                RemoteBookRow(
                    book = book,
                    localBook = localById[book.id],
                    onDownload = { onDownload(book) },
                    onOpenLocal = onOpenLocal,
                )
            }
        }
    }
}

@Composable
private fun ShelfScreen(
    state: AppUiState,
    padding: PaddingValues,
    onOpenLocal: (LocalBook) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Local shelf", style = MaterialTheme.typography.headlineSmall) }
        if (state.localBooks.isEmpty()) {
            item { EmptyMessage("Downloaded EPUBs will appear here.") }
        } else {
            items(state.localBooks, key = { it.id }) { book ->
                LocalBookRow(book = book, onOpen = { onOpenLocal(book) })
            }
        }
    }
}

@Composable
private fun RemoteBookRow(
    book: BookDto,
    localBook: LocalBook?,
    onDownload: () -> Unit,
    onOpenLocal: (LocalBook) -> Unit,
) {
    BookCard(
        title = book.title,
        subtitle = metadata(book.author, book.fileSize),
        action = {
            if (localBook == null) {
                Button(onClick = onDownload) { Text("Download") }
            } else {
                Button(onClick = { onOpenLocal(localBook) }) { Text("Read") }
            }
        },
    )
}

@Composable
private fun LocalBookRow(book: LocalBook, onOpen: () -> Unit) {
    BookCard(
        title = book.title,
        subtitle = metadata(book.author, book.fileSize) + " · Chapter ${book.currentChapterIndex + 1}",
        action = {
            Button(onClick = onOpen) { Text("Read") }
        },
    )
}

@Composable
private fun BookCard(
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            action()
        }
    }
}

@Composable
private fun ReaderScreen(
    state: AppUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val reader = state.reader
    if (reader == null) {
        EmptyMessage("No book is open.")
        return
    }
    val chapterScrollState = remember(reader.currentChapterIndex) { ScrollState(0) }
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(reader.title, style = MaterialTheme.typography.titleLarge)
        Text(
            "${reader.currentChapterIndex + 1} / ${reader.chapters.size}: ${reader.currentChapter.title}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            reader.currentChapter.text,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(chapterScrollState),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text("Shelf") }
            OutlinedButton(onClick = onPrevious, enabled = reader.currentChapterIndex > 0) { Text("Previous") }
            Button(onClick = onNext, enabled = reader.currentChapterIndex < reader.chapters.lastIndex) { Text("Next") }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

private fun metadata(author: String, fileSize: Long): String {
    val size = when {
        fileSize < 1024 -> "1 KB"
        fileSize < 1024 * 1024 -> "${(fileSize + 1023) / 1024} KB"
        else -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
    }
    return listOf(author, size).filter { it.isNotBlank() }.joinToString(" · ")
}
