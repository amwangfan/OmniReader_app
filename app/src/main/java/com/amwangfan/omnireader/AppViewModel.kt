package com.amwangfan.omnireader

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amwangfan.omnireader.data.AppPreferences
import com.amwangfan.omnireader.data.ApiException
import com.amwangfan.omnireader.data.BookDto
import com.amwangfan.omnireader.data.DeviceRequest
import com.amwangfan.omnireader.data.LocalBook
import com.amwangfan.omnireader.data.LocalBookStore
import com.amwangfan.omnireader.data.OmniApi
import com.amwangfan.omnireader.data.PutProgressRequest
import com.amwangfan.omnireader.data.normalizeServerBaseUrl
import com.amwangfan.omnireader.reader.EpubChapter
import com.amwangfan.omnireader.reader.EpubParser
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val api = OmniApi()
    private val localBookStore = LocalBookStore(application)
    private val epubParser = EpubParser()
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(
        AppUiState(
            serverUrl = preferences.serverUrl,
            accessToken = preferences.accessToken,
            refreshToken = preferences.refreshToken,
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState

    init {
        val initialScreen = when {
            preferences.serverUrl.isBlank() -> AppScreen.ServerConfig
            preferences.accessToken.isBlank() -> AppScreen.Login
            else -> AppScreen.Library
        }
        _uiState.update { it.copy(screen = initialScreen) }
        viewModelScope.launch {
            refreshLocalBooks()
            if (preferences.serverUrl.isNotBlank() && preferences.accessToken.isNotBlank()) {
                sync()
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
                val serverChanged = preferences.serverUrl.isNotBlank() && preferences.serverUrl != normalized
                preferences.serverUrl = normalized
                if (serverChanged) {
                    preferences.clearSession()
                }
                _uiState.update {
                    it.copy(
                        serverUrl = normalized,
                        accessToken = if (serverChanged) "" else it.accessToken,
                        refreshToken = if (serverChanged) "" else it.refreshToken,
                        remoteBooks = if (serverChanged) emptyList() else it.remoteBooks,
                        screen = if (serverChanged || it.accessToken.isBlank()) AppScreen.Login else AppScreen.Library,
                        errorMessage = null,
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
                sync()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Login failed") }
            }
            setBusy(false)
        }
    }

    fun logout() {
        val state = _uiState.value
        clearSession()
        if (state.serverUrl.isNotBlank() && state.refreshToken.isNotBlank()) {
            viewModelScope.launch {
                runCatching { api.logout(state.serverUrl, state.refreshToken) }
            }
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
            val state = _uiState.value
            if (state.serverUrl.isBlank() || state.accessToken.isBlank()) {
                return@launch
            }
            setBusy(true)
            runCatching {
                authenticated { token ->
                    api.upsertDevice(
                        state.serverUrl,
                        token,
                        DeviceRequest(
                            id = preferences.deviceId,
                            displayName = listOf(Build.MANUFACTURER, Build.MODEL)
                                .filter { it.isNotBlank() }
                                .joinToString(" "),
                        ),
                    )
                    api.listBooks(state.serverUrl, token)
                }
            }.onSuccess { books ->
                _uiState.update {
                    it.copy(
                        remoteBooks = books,
                        lastSyncMessage = "Synced ${books.size} books",
                        errorMessage = null,
                    )
                }
                refreshLocalBooks()
                syncRemoteProgress()
                refreshLocalBooks()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Sync failed") }
            }
            setBusy(false)
        }
    }

    fun download(book: BookDto) {
        viewModelScope.launch {
            val state = _uiState.value
            setBusy(true)
            runCatching {
                val file = localBookStore.epubFile(book.id)
                authenticated { token ->
                    api.downloadBook(state.serverUrl, token, book.id, file, book.checksum)
                }
                localBookStore.recordDownloaded(book, file)
            }.onSuccess {
                refreshLocalBooks()
                _uiState.update { stateNow ->
                    stateNow.copy(lastSyncMessage = "Downloaded ${book.title}", errorMessage = null)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Download failed") }
            }
            setBusy(false)
        }
    }

    fun openBook(book: LocalBook) {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                epubParser.parse(localBookStore.fileFor(book))
            }.onSuccess { document ->
                _uiState.update {
                    it.copy(
                        screen = AppScreen.Reader,
                        reader = ReaderUiState(
                            bookId = book.id,
                            title = document.title,
                            chapters = document.chapters,
                            currentChapterIndex = book.currentChapterIndex.coerceIn(0, document.chapters.lastIndex),
                        ),
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Could not open EPUB") }
            }
            setBusy(false)
        }
    }

    fun nextChapter() {
        moveChapter(1)
    }

    fun previousChapter() {
        moveChapter(-1)
    }

    private fun moveChapter(delta: Int) {
        val current = _uiState.value.reader ?: return
        val updated = current.copy(
            currentChapterIndex = (current.currentChapterIndex + delta).coerceIn(0, current.chapters.lastIndex),
        )
        if (updated.currentChapterIndex == current.currentChapterIndex) {
            return
        }
        _uiState.update { it.copy(reader = updated) }
        persistProgress(updated)
    }

    private suspend fun refreshLocalBooks() {
        val books = localBookStore.loadBooks()
        _uiState.update { it.copy(localBooks = books) }
    }

    private suspend fun syncRemoteProgress() {
        val state = _uiState.value
        val remoteBookIds = state.remoteBooks.mapTo(mutableSetOf()) { it.id }
        state.localBooks.filter { it.id in remoteBookIds }.forEach { local ->
            val remoteResult = runCatching {
                authenticated { token -> api.getProgress(state.serverUrl, token, local.id) }
            }
            if (remoteResult.isFailure) {
                return@forEach
            }
            val remote = remoteResult.getOrNull()
            if (remote == null) {
                runCatching { uploadLocalProgress(local, state.serverUrl) }
                return@forEach
            }
            val remoteTime = runCatching { Instant.parse(remote.updatedAt).toEpochMilli() }.getOrDefault(0)
            val chapterIndex = remote.locator.substringAfter("chapter:", "").toIntOrNull() ?: return@forEach
            if (remoteTime > local.progressUpdatedAtEpochMillis) {
                localBookStore.updateProgress(local.id, chapterIndex, remoteTime)
            } else if (local.progressUpdatedAtEpochMillis > remoteTime) {
                runCatching { uploadLocalProgress(local, state.serverUrl) }
            }
        }
    }

    private suspend fun uploadLocalProgress(local: LocalBook, serverUrl: String) {
        if (local.progressUpdatedAtEpochMillis <= 0) {
            return
        }
        authenticated { token ->
            api.putProgress(
                serverUrl,
                token,
                local.id,
                PutProgressRequest(
                    deviceId = preferences.deviceId,
                    locator = "chapter:${local.currentChapterIndex}",
                    updatedAt = Instant.ofEpochMilli(local.progressUpdatedAtEpochMillis).toString(),
                ),
            )
        }
    }

    private fun persistProgress(reader: ReaderUiState) {
        val updatedAt = System.currentTimeMillis()
        viewModelScope.launch {
            localBookStore.updateProgress(reader.bookId, reader.currentChapterIndex, updatedAt)
            refreshLocalBooks()
            val result = runCatching {
                authenticated { token ->
                    api.putProgress(
                        _uiState.value.serverUrl,
                        token,
                        reader.bookId,
                        PutProgressRequest(
                            deviceId = preferences.deviceId,
                            locator = "chapter:${reader.currentChapterIndex}",
                            percentage = (reader.currentChapterIndex + 1).toDouble() / reader.chapters.size,
                            updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
                        ),
                    )
                }
            }
            if (result.isFailure) {
                _uiState.update {
                    it.copy(errorMessage = "Progress saved locally; server upload will be retried on the next sync")
                }
            }
        }
    }

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val initial = _uiState.value
        try {
            return block(initial.accessToken)
        } catch (error: ApiException) {
            if (error.statusCode != 401) {
                throw error
            }
        }
        return refreshMutex.withLock {
            val current = _uiState.value
            if (current.accessToken.isNotBlank() && current.accessToken != initial.accessToken) {
                return@withLock block(current.accessToken)
            }
            if (current.refreshToken.isBlank()) {
                clearSession("Session expired. Please log in again.")
                throw ApiException(401, "Session expired")
            }
            val refreshed = try {
                api.refresh(current.serverUrl, current.refreshToken)
            } catch (error: Throwable) {
                clearSession("Session expired. Please log in again.")
                throw error
            }
            preferences.updateAccessToken(refreshed.accessToken)
            _uiState.update { it.copy(accessToken = refreshed.accessToken, errorMessage = null) }
            try {
                block(refreshed.accessToken)
            } catch (error: ApiException) {
                if (error.statusCode == 401) {
                    clearSession("Session expired. Please log in again.")
                }
                throw error
            }
        }
    }

    private fun clearSession(message: String? = null) {
        preferences.clearSession()
        _uiState.update {
            it.copy(
                accessToken = "",
                refreshToken = "",
                remoteBooks = emptyList(),
                screen = AppScreen.Login,
                reader = null,
                errorMessage = message,
            )
        }
    }

    private fun setBusy(value: Boolean) {
        _uiState.update { it.copy(isBusy = value) }
    }
}

data class AppUiState(
    val screen: AppScreen = AppScreen.ServerConfig,
    val serverUrl: String = "",
    val username: String = "admin",
    val password: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val remoteBooks: List<BookDto> = emptyList(),
    val localBooks: List<LocalBook> = emptyList(),
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
    val bookId: String,
    val title: String,
    val chapters: List<EpubChapter>,
    val currentChapterIndex: Int = 0,
) {
    val currentChapter: EpubChapter
        get() = chapters[currentChapterIndex]
}
