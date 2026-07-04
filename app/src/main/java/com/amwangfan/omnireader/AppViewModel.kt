package com.amwangfan.omnireader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amwangfan.omnireader.data.AppPreferences
import com.amwangfan.omnireader.data.BookDto
import com.amwangfan.omnireader.data.LocalBook
import com.amwangfan.omnireader.data.LocalBookStore
import com.amwangfan.omnireader.data.OmniApi
import com.amwangfan.omnireader.data.normalizeServerBaseUrl
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
                preferences.serverUrl = normalized
                _uiState.update {
                    it.copy(
                        serverUrl = normalized,
                        screen = if (it.accessToken.isBlank()) AppScreen.Login else AppScreen.Library,
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
        preferences.clearSession()
        _uiState.update {
            it.copy(
                accessToken = "",
                refreshToken = "",
                remoteBooks = emptyList(),
                screen = AppScreen.Login,
                errorMessage = null,
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
            val state = _uiState.value
            if (state.serverUrl.isBlank() || state.accessToken.isBlank()) {
                return@launch
            }
            setBusy(true)
            runCatching {
                api.listBooks(state.serverUrl, state.accessToken)
            }.onSuccess { books ->
                _uiState.update {
                    it.copy(
                        remoteBooks = books,
                        lastSyncMessage = "Synced ${books.size} books",
                        errorMessage = null,
                    )
                }
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
                api.downloadBook(state.serverUrl, state.accessToken, book.id, file)
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
                        reader = ReaderUiState(title = document.title, chapters = document.chapters),
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
        _uiState.update { state ->
            val reader = state.reader ?: return@update state
            state.copy(reader = reader.copy(currentChapterIndex = (reader.currentChapterIndex + 1).coerceAtMost(reader.chapters.lastIndex)))
        }
    }

    fun previousChapter() {
        _uiState.update { state ->
            val reader = state.reader ?: return@update state
            state.copy(reader = reader.copy(currentChapterIndex = (reader.currentChapterIndex - 1).coerceAtLeast(0)))
        }
    }

    private suspend fun refreshLocalBooks() {
        val books = localBookStore.loadBooks()
        _uiState.update { it.copy(localBooks = books) }
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
    val title: String,
    val chapters: List<EpubChapter>,
    val currentChapterIndex: Int = 0,
) {
    val currentChapter: EpubChapter
        get() = chapters[currentChapterIndex]
}
