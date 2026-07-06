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
import com.amwangfan.omnireader.data.DeviceIdentityProvider
import com.amwangfan.omnireader.data.OmniReadingSyncGateway
import com.amwangfan.omnireader.data.ReadingStateRecord
import com.amwangfan.omnireader.data.ReadingStateStore
import com.amwangfan.omnireader.data.ReadingSyncCoordinator
import com.amwangfan.omnireader.data.normalizeServerBaseUrl
import com.amwangfan.omnireader.data.resolveStorageDestination
import com.amwangfan.omnireader.reader.EpubChapter
import com.amwangfan.omnireader.reader.EpubParser
import com.amwangfan.omnireader.reader.LocatorResolutionReason
import com.amwangfan.omnireader.reader.LocatorResolver
import com.amwangfan.omnireader.reader.ReadingTimeTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val api = OmniApi()
    private val localBookStore = LocalBookStore(application)
    private val epubParser = EpubParser()
    private val identity = DeviceIdentityProvider(application, preferences).current()
    private val readingIo = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omnireader-reading-state")
    }.asCoroutineDispatcher()
    private val readingStateStore by lazy { ReadingStateStore(application.filesDir) }
    private val readingTimeTracker = ReadingTimeTracker()
    private var checkpointUpload: Job? = null

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
                        screen = AppScreen.Settings,
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
                screen = AppScreen.Settings,
                errorMessage = null,
                lastSyncMessage = "Signed out",
            )
        }
    }

    fun showServerConfig() {
        showSettings()
    }

    fun showSettings() {
        leaveReader(AppScreen.Settings)
    }

    fun showLibrary() {
        leaveReader(AppScreen.Library)
    }

    fun showShelf() {
        leaveReader(AppScreen.Shelf)
        viewModelScope.launch {
            refreshLocalBooks()
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
        if (_uiState.value.reader?.localBookId == book.id) checkpointCurrent(stopTimer = true)
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
                            reader = state.reader,
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
        checkpointCurrent(stopTimer = false)
        _uiState.update { state ->
            val reader = state.reader ?: return@update state
            state.copy(
                reader = reader.copy(
                    currentChapterIndex = (reader.currentChapterIndex + 1)
                        .coerceAtMost(reader.chapters.lastIndex),
                    initialBlockIndex = 0,
                    initialScrollOffset = 0,
                    positionVersion = reader.positionVersion + 1,
                ),
            )
        }
        checkpointReading(0, 0)
    }

    fun previousChapter() {
        checkpointCurrent(stopTimer = false)
        _uiState.update { state ->
            val reader = state.reader ?: return@update state
            state.copy(
                reader = reader.copy(
                    currentChapterIndex = (reader.currentChapterIndex - 1).coerceAtLeast(0),
                    initialBlockIndex = 0,
                    initialScrollOffset = 0,
                    positionVersion = reader.positionVersion + 1,
                ),
            )
        }
        checkpointReading(0, 0)
    }

    fun updateBook(book: BookDto, fallbackTreeUri: String? = null) {
        viewModelScope.launch {
            val state = _uiState.value
            if (!hasServerSession(state)) {
                _uiState.update { it.copy(errorMessage = "Configure the server and sign in first") }
                return@launch
            }
            setBusy(true)
            try {
                localBookStore.update(book, epubParser, fallbackTreeUri) { output ->
                    api.downloadBook(state.serverUrl, state.accessToken, book.id, output)
                }
                refreshLocalBooks()
                _uiState.update {
                    it.copy(lastSyncMessage = "Updated " + book.title, errorMessage = null)
                }
            } catch (error: Throwable) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Update failed") }
            } finally {
                setBusy(false)
            }
        }
    }

    fun checkpointReading(blockIndex: Int, charOffset: Int) {
        val reader = _uiState.value.reader ?: return
        val locator = LocatorResolver.locatorFor(
            reader.chapters,
            reader.contentRevision,
            reader.currentChapterIndex,
            blockIndex,
            charOffset,
        )
        val elapsed = readingTimeTracker.checkpoint()
        _uiState.update { state ->
            val current = state.reader
            if (current?.localBookId != reader.localBookId) state else state.copy(
                reader = current.copy(initialBlockIndex = blockIndex, initialScrollOffset = charOffset, positionChanged = false),
            )
        }
        viewModelScope.launch(readingIo) {
            readingStateStore.mergeElapsed(reader.progressBookId, identity.id, elapsed, locator)
            scheduleProgressUpload(reader.progressBookId)
        }
    }

    fun updateVisibleReadingPosition(blockIndex: Int, scrollOffset: Int) {
        _uiState.update { state ->
            val reader = state.reader ?: return@update state
            state.copy(reader = reader.withVisiblePosition(blockIndex, scrollOffset))
        }
    }

    fun readerActive(active: Boolean) {
        if (active && _uiState.value.screen == AppScreen.Reader) {
            readingTimeTracker.start()
        } else if (!active) {
            checkpointCurrent(stopTimer = true)
        }
    }

    fun consumeReaderNotice() {
        _uiState.update { it.copy(readerNotice = null) }
    }

    fun clearReaderAfterDispose() {
        _uiState.update { if (it.screen == AppScreen.Reader) it else it.copy(reader = null) }
    }

    private suspend fun performSync() {
        val state = _uiState.value
        if (!hasServerSession(state)) return
        val uploadFailures = processPendingUploads(localBookStore.pendingUploads()) { book ->
            val file = localBookStore.materialize(book)
            val remote = api.uploadBook(state.serverUrl, state.accessToken, book.title, file)
            localBookStore.markUploaded(book.id, remote)
            withContext(readingIo) {
                readingStateStore.migrateBookId(book.id, remote.id, identity.id, remote.contentRevision)
            }
        }
        runCatching { withContext(readingIo) { coordinator(state)?.syncAllDirty() } }
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
            withContext(readingIo) {
                readingStateStore.migrateBookId(book.id, remote.id, identity.id, remote.contentRevision)
                runCatching { coordinator(state)?.syncBook(remote.id) }
            }
        }.isSuccess
    }

    private suspend fun openBookInternal(book: LocalBook) {
        val file = localBookStore.materialize(book)
        val document = epubParser.parse(file)
        val progressBookId = book.remoteBookId ?: book.id
        val local = withContext(readingIo) { readingStateStore.get(progressBookId, identity.id) }
        val localResolution = local?.let { LocatorResolver.resolve(document.chapters, it.locator, book.contentRevision) }
        _uiState.update {
            it.copy(
                screen = AppScreen.Reader,
                reader = ReaderUiState(
                    localBookId = book.id,
                    title = document.title,
                    chapters = document.chapters,
                    progressBookId = progressBookId,
                    contentRevision = book.contentRevision,
                    currentChapterIndex = localResolution?.chapterIndex ?: 0,
                    initialBlockIndex = localResolution?.blockIndex ?: 0,
                    initialScrollOffset = localResolution?.charOffset ?: 0,
                ),
                readerNotice = localResolution?.takeIf { resolution -> resolution.revisionMismatch }
                    ?.let { "The book changed; restored the closest saved position." },
                errorMessage = null,
            )
        }
        if (book.remoteBookId != null && hasServerSession(_uiState.value)) {
            viewModelScope.launch { loadRemoteResume(book, document.chapters) }
        }
    }

    private suspend fun loadRemoteResume(book: LocalBook, chapters: List<EpubChapter>) {
        val state = _uiState.value
        val resume = withContext(readingIo) {
            coordinator(state)?.resumeFor(book.remoteBookId ?: return@withContext null)
        } ?: return
        val activeReader = _uiState.value.reader
        if (activeReader?.localBookId != book.id) return
        val elapsedWhileWaiting = readingTimeTracker.checkpoint()
        if (activeReader.positionChanged || elapsedWhileWaiting.isNotEmpty()) {
            val localLocator = LocatorResolver.locatorFor(
                activeReader.chapters,
                activeReader.contentRevision,
                activeReader.currentChapterIndex,
                activeReader.initialBlockIndex,
                activeReader.initialScrollOffset,
            )
            withContext(readingIo) {
                readingStateStore.mergeElapsed(activeReader.progressBookId, identity.id, elapsedWhileWaiting, localLocator)
            }
            return
        }
        val resolved = LocatorResolver.resolve(chapters, resume.locator, book.contentRevision)
        _uiState.update { current ->
            val reader = current.reader
            if (reader?.localBookId != book.id) current else current.copy(
                reader = reader.copy(
                    currentChapterIndex = resolved.chapterIndex,
                    initialBlockIndex = resolved.blockIndex,
                    initialScrollOffset = resolved.charOffset,
                    positionVersion = reader.positionVersion + 1,
                ),
                readerNotice = when {
                    resume.sourceDeviceName != null && (resume.revisionMismatch || resolved.revisionMismatch) ->
                        "Resumed from ${resume.sourceDeviceName}; the book changed, so the closest position was used."
                    resume.sourceDeviceName != null -> "Resumed from ${resume.sourceDeviceName}."
                    resume.revisionMismatch || resolved.revisionMismatch -> "The book changed; restored the closest saved position."
                    resolved.reason != LocatorResolutionReason.EXACT -> "Restored the closest saved position."
                    else -> null
                },
            )
        }
    }

    private fun coordinator(state: AppUiState): ReadingSyncCoordinator? {
        if (!hasServerSession(state)) return null
        val gateway = OmniReadingSyncGateway(api, state.serverUrl, state.accessToken)
        return ReadingSyncCoordinator(gateway, readingStateStore, identity)
    }

    private fun scheduleProgressUpload(bookId: String) {
        checkpointUpload?.cancel()
        checkpointUpload = viewModelScope.launch {
            delay(900)
            val state = _uiState.value
            runCatching { withContext(readingIo) { coordinator(state)?.syncBook(bookId) } }
        }
    }

    private fun checkpointCurrent(stopTimer: Boolean) {
        val reader = _uiState.value.reader ?: return
        val locator = LocatorResolver.locatorFor(
            reader.chapters,
            reader.contentRevision,
            reader.currentChapterIndex,
            reader.initialBlockIndex,
            reader.initialScrollOffset,
        )
        val elapsed = if (stopTimer) readingTimeTracker.stop() else readingTimeTracker.checkpoint()
        if (elapsed.isNotEmpty() || reader.positionChanged) {
            viewModelScope.launch(readingIo) {
                readingStateStore.mergeElapsed(reader.progressBookId, identity.id, elapsed, locator)
                if (stopTimer) scheduleProgressUpload(reader.progressBookId)
            }
        }
    }

    private fun leaveReader(target: AppScreen) {
        if (_uiState.value.screen == AppScreen.Reader) checkpointCurrent(stopTimer = true)
        _uiState.update { it.copy(screen = target, errorMessage = null) }
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

    override fun onCleared() {
        readingIo.close()
        super.onCleared()
    }
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
    val readerNotice: String? = null,
)

enum class AppScreen {
    Library,
    Shelf,
    Settings,
    Reader,
}

data class ReaderUiState(
    val localBookId: String,
    val title: String,
    val chapters: List<EpubChapter>,
    val progressBookId: String,
    val contentRevision: String = "",
    val currentChapterIndex: Int = 0,
    val initialBlockIndex: Int = 0,
    val initialScrollOffset: Int = 0,
    val positionVersion: Int = 0,
    val positionChanged: Boolean = false,
) {
    val currentChapter: EpubChapter
        get() = chapters[currentChapterIndex]
}

internal fun ReaderUiState.withVisiblePosition(blockIndex: Int, scrollOffset: Int): ReaderUiState = copy(
    initialBlockIndex = blockIndex,
    initialScrollOffset = scrollOffset,
    positionChanged = true,
)

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
