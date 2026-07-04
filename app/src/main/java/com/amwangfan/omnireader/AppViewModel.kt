package com.amwangfan.omnireader

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amwangfan.omnireader.data.AppPreferences
import com.amwangfan.omnireader.data.BookDto
import com.amwangfan.omnireader.data.BookSyncState
import com.amwangfan.omnireader.data.LocalBook
import com.amwangfan.omnireader.data.LocalBookStore
import com.amwangfan.omnireader.data.OmniApi
import com.amwangfan.omnireader.data.normalizeServerBaseUrl
import com.amwangfan.omnireader.data.resolveStorageDestination
import com.amwangfan.omnireader.reader.EpubChapter
import com.amwangfan.omnireader.reader.EpubParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val api = OmniApi()
    private val localBookStore = LocalBookStore(application)
    private val epubParser = EpubParser()

    private val _uiState = MutableStateFlow(
        AppUiState(
            serverUrl = preferences.serverUrl,
            accessToken = preferences.accessToken,
            refreshToken = preferences.refreshToken,
            defaultDownloadTreeUri = preferences.defaultDownloadTreeUri,
            autoUploadImports = preferences.autoUploadImports,
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState

    init {
        viewModelScope.launch {
            refreshLocalBooks()
            if (hasServerSession(_uiState.value)) {
                setBusy(true)
                performSync()
                setBusy(false)
            }
        }
    }

    fun updateServerUrl(value: String) {
        _uiState.update { it.copy(serverUrl = value, errorMessage = null) }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun saveServerUrl() {
        runCatching { normalizeServerBaseUrl(_uiState.value.serverUrl) }
            .onSuccess { normalized ->
                preferences.serverUrl = normalized
                _uiState.update {
                    it.copy(
                        serverUrl = normalized,
                        screen = AppScreen.Shelf,
                        errorMessage = null,
                        lastSyncMessage = "Server settings saved",
                    )
                }
            }
            .onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Invalid server address") }
            }
    }

    fun login() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Server, username, and password are required") }
            return
        }
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                api.login(state.serverUrl, state.username, state.password)
            }.onSuccess { login ->
                preferences.saveSession(login)
                _uiState.update {
                    it.copy(
                        accessToken = login.accessToken,
                        refreshToken = login.refreshToken,
                        password = "",
                        screen = AppScreen.Library,
                        errorMessage = null,
                    )
                }
                performSync()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Login failed") }
            }
            setBusy(false)
        }
    }

    fun logout() {
        preferences.clearSession()
        _uiState.update {
            it.copy(
                accessToken = "",
                refreshToken = "",
                remoteBooks = emptyList(),
                screen = AppScreen.Shelf,
                errorMessage = null,
                lastSyncMessage = "Signed out",
            )
        }
    }

    fun showServerConfig() {
        _uiState.update { it.copy(screen = AppScreen.ServerConfig, errorMessage = null) }
    }

    fun showLibrary() {
        _uiState.update { it.copy(screen = AppScreen.Library, reader = null, errorMessage = null) }
    }

    fun showShelf() {
        viewModelScope.launch {
            refreshLocalBooks()
            _uiState.update { it.copy(screen = AppScreen.Shelf, reader = null, errorMessage = null) }
        }
    }

    fun sync() {
        viewModelScope.launch {
            if (!hasServerSession(_uiState.value)) {
                _uiState.update { it.copy(errorMessage = "Configure the server and sign in first") }
                return@launch
            }
            setBusy(true)
            performSync()
            setBusy(false)
        }
    }

    fun download(book: BookDto) {
        download(book, null)
    }

    fun download(book: BookDto, oneTimeTreeUri: String?) {
        viewModelScope.launch {
            val state = _uiState.value
            if (!hasServerSession(state)) {
                _uiState.update { it.copy(errorMessage = "Configure the server and sign in first") }
                return@launch
            }
            setBusy(true)
            runCatching {
                val destination = resolveStorageDestination(
                    oneTimeTreeUri,
                    state.defaultDownloadTreeUri,
                )
                localBookStore.download(book, destination) { output ->
                    api.downloadBook(state.serverUrl, state.accessToken, book.id, output)
                }
            }.onSuccess {
                refreshLocalBooks()
                _uiState.update {
                    it.copy(lastSyncMessage = "Downloaded " + book.title, errorMessage = null)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Download failed") }
            }
            setBusy(false)
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            setBusy(true)
            val state = _uiState.value
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val sourceName = resolver.displayName(uri) ?: "imported.epub"
                val source = resolver.openInputStream(uri) ?: error("Cannot open selected EPUB")
                val destination = resolveStorageDestination(null, state.defaultDownloadTreeUri)
                localBookStore.importBook(
                    source = source,
                    sourceName = sourceName,
                    destination = destination,
                    autoUpload = state.autoUploadImports,
                    parser = epubParser,
                )
            }.onSuccess { local ->
                refreshLocalBooks()
                val uploaded = if (
                    local.syncState == BookSyncState.PENDING_UPLOAD &&
                    hasServerSession(_uiState.value)
                ) {
                    uploadLocalBook(local)
                } else {
                    false
                }
                refreshLocalBooks()
                _uiState.update {
                    it.copy(
                        lastSyncMessage = when {
                            uploaded -> "Imported and uploaded " + local.title
                            local.syncState == BookSyncState.PENDING_UPLOAD ->
                                "Imported " + local.title + "; upload pending"
                            else -> "Imported " + local.title
                        },
                        errorMessage = null,
                    )
                }
                val refreshed = localBookStore.loadBooks().firstOrNull { it.id == local.id } ?: local
                openBookInternal(refreshed)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Import failed") }
            }
            setBusy(false)
        }
    }

    fun setDefaultDownloadTree(uri: String) {
        preferences.defaultDownloadTreeUri = uri
        _uiState.update {
            it.copy(defaultDownloadTreeUri = uri, lastSyncMessage = "Default folder updated")
        }
    }

    fun clearDefaultDownloadTree() {
        preferences.defaultDownloadTreeUri = ""
        _uiState.update {
            it.copy(defaultDownloadTreeUri = "", lastSyncMessage = "Using app storage")
        }
    }

    fun setAutoUploadImports(enabled: Boolean) {
        preferences.autoUploadImports = enabled
        _uiState.update { it.copy(autoUploadImports = enabled) }
    }

    fun requestDelete(book: LocalBook) {
        _uiState.update { it.copy(pendingDeleteBook = book) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDeleteBook = null) }
    }

    fun confirmDelete() {
        val book = _uiState.value.pendingDeleteBook ?: return
        viewModelScope.launch {
            setBusy(true)
            runCatching { localBookStore.delete(book.id) }
                .onSuccess { deleted ->
                    if (!deleted) error("Could not delete " + book.title)
                    refreshLocalBooks()
                    _uiState.update { state ->
                        val wasOpen = state.reader?.localBookId == book.id
                        state.copy(
                            screen = if (wasOpen) AppScreen.Shelf else state.screen,
                            reader = if (wasOpen) null else state.reader,
                            pendingDeleteBook = null,
                            lastSyncMessage = "Deleted " + book.title,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            pendingDeleteBook = null,
                            errorMessage = error.message ?: "Delete failed",
                        )
                    }
                }
            setBusy(false)
        }
    }

    fun openBook(book: LocalBook) {
        viewModelScope.launch {
            setBusy(true)
            runCatching { openBookInternal(book) }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "Could not open EPUB") }
                }
            setBusy(false)
        }
    }

    fun nextChapter() {
        _uiState.update { state ->
            val reader = state.reader ?: return@update state
            state.copy(
                reader = reader.copy(
                    currentChapterIndex = (reader.currentChapterIndex + 1)
                        .coerceAtMost(reader.chapters.lastIndex),
                ),
            )
        }
    }

    fun previousChapter() {
        _uiState.update { state ->
            val reader = state.reader ?: return@update state
            state.copy(
                reader = reader.copy(
                    currentChapterIndex = (reader.currentChapterIndex - 1).coerceAtLeast(0),
                ),
            )
        }
    }

    private suspend fun performSync() {
        val state = _uiState.value
        if (!hasServerSession(state)) return
        val uploadFailures = processPendingUploads(localBookStore.pendingUploads()) { book ->
            val file = localBookStore.materialize(book)
            val remote = api.uploadBook(state.serverUrl, state.accessToken, book.title, file)
            localBookStore.markUploaded(book.id, remote)
        }
        runCatching {
            api.listBooks(state.serverUrl, state.accessToken)
        }.onSuccess { books ->
            refreshLocalBooks()
            _uiState.update {
                it.copy(
                    remoteBooks = books,
                    lastSyncMessage = if (uploadFailures.isEmpty()) {
                        "Synced " + books.size + " books"
                    } else {
                        "Synced; " + uploadFailures.size + " upload(s) pending"
                    },
                    errorMessage = null,
                )
            }
        }.onFailure { error ->
            refreshLocalBooks()
            _uiState.update { it.copy(errorMessage = error.message ?: "Sync failed") }
        }
    }

    private suspend fun uploadLocalBook(book: LocalBook): Boolean {
        val state = _uiState.value
        return runCatching {
            val file = localBookStore.materialize(book)
            val remote = api.uploadBook(state.serverUrl, state.accessToken, book.title, file)
            localBookStore.markUploaded(book.id, remote)
        }.isSuccess
    }

    private suspend fun openBookInternal(book: LocalBook) {
        val file = localBookStore.materialize(book)
        val document = epubParser.parse(file)
        _uiState.update {
            it.copy(
                screen = AppScreen.Reader,
                reader = ReaderUiState(
                    localBookId = book.id,
                    title = document.title,
                    chapters = document.chapters,
                ),
                errorMessage = null,
            )
        }
    }

    private suspend fun refreshLocalBooks() {
        val books = localBookStore.loadBooks()
        _uiState.update { it.copy(localBooks = books) }
    }

    private fun setBusy(value: Boolean) {
        _uiState.update { it.copy(isBusy = value) }
    }

    private fun hasServerSession(state: AppUiState): Boolean =
        state.serverUrl.isNotBlank() && state.accessToken.isNotBlank()
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Shelf,
    val serverUrl: String = "",
    val username: String = "admin",
    val password: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val remoteBooks: List<BookDto> = emptyList(),
    val localBooks: List<LocalBook> = emptyList(),
    val defaultDownloadTreeUri: String = "",
    val autoUploadImports: Boolean = true,
    val pendingDeleteBook: LocalBook? = null,
    val reader: ReaderUiState? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncMessage: String? = null,
)

enum class AppScreen {
    ServerConfig,
    Login,
    Library,
    Shelf,
    Reader,
}

data class ReaderUiState(
    val localBookId: String,
    val title: String,
    val chapters: List<EpubChapter>,
    val currentChapterIndex: Int = 0,
) {
    val currentChapter: EpubChapter
        get() = chapters[currentChapterIndex]
}

internal suspend fun processPendingUploads(
    books: List<LocalBook>,
    upload: suspend (LocalBook) -> Unit,
): List<String> {
    val failures = mutableListOf<String>()
    books.forEach { book ->
        runCatching { upload(book) }
            .onFailure { failures += book.title }
    }
    return failures
}

private fun android.content.ContentResolver.displayName(uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }
}
