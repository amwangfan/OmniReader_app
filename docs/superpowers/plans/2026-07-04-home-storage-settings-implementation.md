# Home, Storage, and Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make OmniReader local-first, add Settings, support internal and SAF-managed EPUB storage, import and delete local books, and queue imported books for server upload.

**Architecture:** Keep `AppViewModel` as the UI orchestrator, but separate persisted preferences, serializable book state, managed file access, API transport, and Compose screens. `LocalBookStore` owns the index and transactional file lifecycle; an Android-backed `ManagedBookFiles` handles internal files and SAF document URIs. Imported books become immediately readable local records and use a persisted pending-upload state for app-driven retry.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose Material 3, Android Storage Access Framework, AndroidX DocumentFile, OkHttp/MockWebServer, kotlinx.coroutines, kotlinx.serialization, JUnit 4.

---

## File Structure

- `app/src/main/java/com/amwangfan/omnireader/AppViewModel.kt`: app state, navigation, import/download/delete commands, and sync orchestration.
- `app/src/main/java/com/amwangfan/omnireader/MainActivity.kt`: activity setup only.
- `app/src/main/java/com/amwangfan/omnireader/data/Models.kt`: API DTOs and backward-compatible local book schema.
- `app/src/main/java/com/amwangfan/omnireader/data/StorageDestination.kt`: destination types and precedence.
- `app/src/main/java/com/amwangfan/omnireader/data/AppPreferences.kt`: server session, default tree URI, and auto-upload preference.
- `app/src/main/java/com/amwangfan/omnireader/data/OmniApi.kt`: login, catalog, stream download, and multipart upload.
- `app/src/main/java/com/amwangfan/omnireader/data/ManagedBookFiles.kt`: internal/SAF creation, materialization, and deletion.
- `app/src/main/java/com/amwangfan/omnireader/data/LocalBookStore.kt`: index migration and transactional import/download/delete/update operations.
- `app/src/main/java/com/amwangfan/omnireader/ui/OmniReaderApp.kt`: scaffold, bottom navigation, picker launchers, and screen routing.
- `app/src/main/java/com/amwangfan/omnireader/ui/ShelfScreen.kt`: shelf, import, read, sync status, and delete confirmation.
- `app/src/main/java/com/amwangfan/omnireader/ui/OnlineLibraryScreen.kt`: signed-out prompt, server list, and download destination menu.
- `app/src/main/java/com/amwangfan/omnireader/ui/SettingsScreen.kt`: server, account, default folder, and auto-upload controls.
- `app/src/main/java/com/amwangfan/omnireader/ui/ReaderScreen.kt`: chapter reader plus import action.
- `app/src/main/java/com/amwangfan/omnireader/ui/BookComponents.kt`: repeated book metadata and row components.
- `app/src/test/java/com/amwangfan/omnireader/data/LocalBookIndexTest.kt`: migration, removal, and pending-state behavior.
- `app/src/test/java/com/amwangfan/omnireader/data/StorageDestinationTest.kt`: destination precedence.
- `app/src/test/java/com/amwangfan/omnireader/data/OmniApiTest.kt`: multipart upload and stream download contracts.
- `app/src/test/java/com/amwangfan/omnireader/data/LocalBookStoreTest.kt`: transactional file/index behavior with a fake file backend.

### Task 1: Extend and Migrate the Local Book Schema

**Files:**
- Modify: `app/src/main/java/com/amwangfan/omnireader/data/Models.kt`
- Modify: `app/src/test/java/com/amwangfan/omnireader/data/LocalBookIndexTest.kt`

- [ ] **Step 1: Write failing schema and migration tests**

Add tests that decode the existing JSON shape, normalize it, remove a record, and query pending uploads:

```kotlin
@Test
fun normalizeLegacyBooks_assignsServerIdentityAndSyncedState() {
    val legacy = Json.decodeFromString<LocalBookIndex>(
        """{"books":[{"id":"server-1","title":"Book","author":"","fileName":"server-1.epub","fileSize":42,"checksum":"sum","downloadedAtEpochMillis":3}]}""",
    )

    val normalized = legacy.normalized()

    assertEquals("server-1", normalized.books.single().remoteBookId)
    assertEquals(BookSource.SERVER_DOWNLOAD, normalized.books.single().source)
    assertEquals(BookSyncState.SYNCED, normalized.books.single().syncState)
}

@Test
fun removeAndPendingUploads_useStableLocalId() {
    val pending = LocalBook(
        id = "local-sum",
        title = "Imported",
        fileName = "imported.epub",
        fileSize = 10,
        checksum = "sum",
        downloadedAtEpochMillis = 1,
        source = BookSource.LOCAL_IMPORT,
        syncState = BookSyncState.PENDING_UPLOAD,
    )
    val synced = LocalBook(
        id = "server-1",
        title = "Downloaded",
        fileName = "server-1.epub",
        fileSize = 20,
        checksum = "server-sum",
        downloadedAtEpochMillis = 2,
        remoteBookId = "server-1",
        syncState = BookSyncState.SYNCED,
    )
    val index = LocalBookIndex(listOf(pending, synced))

    assertEquals(listOf("local-sum"), index.pendingUploads().map { it.id })
    assertEquals(listOf("server-1"), index.remove("local-sum").books.map { it.id })
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.amwangfan.omnireader.data.LocalBookIndexTest"
```

Expected: compilation fails because `BookSource`, `BookSyncState`, `remoteBookId`, `normalized`, `remove`, and `pendingUploads` do not exist.

- [ ] **Step 3: Add backward-compatible schema fields and index operations**

Add serializable enums and defaults:

```kotlin
@Serializable enum class StorageKind { INTERNAL, DOCUMENT_URI }
@Serializable enum class BookSource { SERVER_DOWNLOAD, LOCAL_IMPORT }
@Serializable enum class BookSyncState { SYNCED, PENDING_UPLOAD, LOCAL_ONLY }

@Serializable
data class LocalBook(
    val id: String,
    val title: String,
    val author: String = "",
    val fileName: String,
    val fileSize: Long,
    val checksum: String,
    val downloadedAtEpochMillis: Long,
    val remoteBookId: String? = null,
    val storageKind: StorageKind = StorageKind.INTERNAL,
    val documentUri: String? = null,
    val source: BookSource = BookSource.SERVER_DOWNLOAD,
    val syncState: BookSyncState = BookSyncState.SYNCED,
) {
    fun normalized(): LocalBook = if (source == BookSource.SERVER_DOWNLOAD && remoteBookId == null) {
        copy(remoteBookId = id, syncState = BookSyncState.SYNCED)
    } else {
        this
    }
}

fun normalized(): LocalBookIndex = copy(books = books.map(LocalBook::normalized))
fun remove(localId: String): LocalBookIndex = copy(books = books.filterNot { it.id == localId })
fun pendingUploads(): List<LocalBook> = books.filter { it.syncState == BookSyncState.PENDING_UPLOAD }
```

- [ ] **Step 4: Run focused and full tests and verify GREEN**

Run the focused command, then:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: all unit tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/amwangfan/omnireader/data/Models.kt app/src/test/java/com/amwangfan/omnireader/data/LocalBookIndexTest.kt
git commit -m "Add local book sync metadata"
```

### Task 2: Add Storage Destination and Preferences

**Files:**
- Create: `app/src/main/java/com/amwangfan/omnireader/data/StorageDestination.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/data/StorageDestinationTest.kt`
- Modify: `app/src/main/java/com/amwangfan/omnireader/data/AppPreferences.kt`

- [ ] **Step 1: Write failing destination-precedence tests**

```kotlin
class StorageDestinationTest {
    @Test fun oneTimeTreeOverridesGlobalTree() {
        assertEquals(StorageDestination.Tree("content://one"), resolveStorageDestination("content://one", "content://global"))
    }

    @Test fun globalTreeOverridesInternalFallback() {
        assertEquals(StorageDestination.Tree("content://global"), resolveStorageDestination(null, "content://global"))
    }

    @Test fun blankTreesUseInternalStorage() {
        assertEquals(StorageDestination.Internal, resolveStorageDestination("", ""))
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Expected: compilation fails because the destination API does not exist.

- [ ] **Step 3: Implement destination resolution and persisted settings**

```kotlin
sealed interface StorageDestination {
    data object Internal : StorageDestination
    data class Tree(val uri: String) : StorageDestination
}

fun resolveStorageDestination(oneTimeTreeUri: String?, defaultTreeUri: String?): StorageDestination =
    oneTimeTreeUri?.takeIf(String::isNotBlank)?.let(StorageDestination::Tree)
        ?: defaultTreeUri?.takeIf(String::isNotBlank)?.let(StorageDestination::Tree)
        ?: StorageDestination.Internal
```

Add `defaultDownloadTreeUri` and `autoUploadImports` to `AppPreferences`; `autoUploadImports` defaults to `true` when no value is stored.

- [ ] **Step 4: Run focused and full unit tests and verify GREEN**

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/amwangfan/omnireader/data/StorageDestination.kt app/src/main/java/com/amwangfan/omnireader/data/AppPreferences.kt app/src/test/java/com/amwangfan/omnireader/data/StorageDestinationTest.kt
git commit -m "Add configurable book storage destination"
```

### Task 3: Add Stream Downloads and Multipart Uploads

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/amwangfan/omnireader/data/Models.kt`
- Modify: `app/src/main/java/com/amwangfan/omnireader/data/OmniApi.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/data/OmniApiTest.kt`

- [ ] **Step 1: Add MockWebServer and write failing API tests**

Add:

```kotlin
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

Test that `downloadBook` writes response bytes to a `ByteArrayOutputStream`, and `uploadBook` sends `POST /api/v1/books`, bearer auth, `title`, and an EPUB file part before decoding `{"book": ...}`.

```kotlin
class OmniApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OmniApi

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        server = MockWebServer().also(MockWebServer::start)
        api = OmniApi()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

@Test
fun uploadBook_sendsAuthenticatedMultipartEpub() = runTest {
    server.enqueue(
        MockResponse().setResponseCode(201).setBody(
            """{"book":{"id":"remote-1","title":"Imported","author":"","format":"epub","fileSize":4,"checksum":"sum","createdAt":"now","updatedAt":"now"}}""",
        ),
    )
    val file = temporaryFolder.newFile("sample.epub").apply { writeBytes("epub".toByteArray()) }

    val result = api.uploadBook(server.url("/").toString(), "token", "Imported", file)
    val request = server.takeRequest()

    assertEquals("POST", request.method)
    assertEquals("/api/v1/books", request.path)
    assertEquals("Bearer token", request.getHeader("Authorization"))
    assertTrue(request.body.readUtf8().contains("Imported"))
    assertEquals("remote-1", result.id)
}
}
```

- [ ] **Step 2: Run `OmniApiTest` and verify RED**

Expected: compilation fails because upload and stream-download signatures are missing.

- [ ] **Step 3: Implement the transport contracts**

Add `BookResponse(val book: BookDto)` and implement:

```kotlin
suspend fun downloadBook(baseUrl: String, accessToken: String, bookId: String, output: OutputStream): Long
suspend fun uploadBook(baseUrl: String, accessToken: String, title: String, file: File): BookDto
```

Use `MultipartBody.Builder().setType(MultipartBody.FORM)`, a `title` form field, and `file.asRequestBody("application/epub+zip".toMediaType())`. Return the byte count from `InputStream.copyTo(output)` and never close the caller-owned output stream.

- [ ] **Step 4: Run API and full unit tests and verify GREEN**

- [ ] **Step 5: Commit**

```powershell
git add app/build.gradle.kts app/src/main/java/com/amwangfan/omnireader/data/Models.kt app/src/main/java/com/amwangfan/omnireader/data/OmniApi.kt app/src/test/java/com/amwangfan/omnireader/data/OmniApiTest.kt
git commit -m "Add EPUB upload and stream download API"
```

### Task 4: Implement Managed Internal and SAF Book Files

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/amwangfan/omnireader/data/ManagedBookFiles.kt`
- Modify: `app/src/main/java/com/amwangfan/omnireader/data/LocalBookStore.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/data/LocalBookStoreTest.kt`

- [ ] **Step 1: Define a fakeable file boundary and write failing lifecycle tests**

Add tests using a fake `ManagedBookFiles` that verify:

- successful download writes bytes before adding the index record;
- failed download discards the incomplete output and leaves the index unchanged;
- duplicate imported checksum discards the new copy and retains one shelf record;
- successful deletion deletes bytes before removing the index entry;
- failed deletion preserves the index;
- missing bytes remove a stale record.

Use this production-facing boundary:

```kotlin
interface ManagedBookFiles {
    fun create(destination: StorageDestination, displayName: String): ManagedOutput
    fun materialize(book: LocalBook): File
    fun delete(book: LocalBook): ManagedDeleteResult
}

data class ManagedOutput(
    val fileName: String,
    val storageKind: StorageKind,
    val documentUri: String?,
    val outputStream: OutputStream,
    val discard: () -> Unit,
)

enum class ManagedDeleteResult { DELETED, MISSING, FAILED }
```

- [ ] **Step 2: Run `LocalBookStoreTest` and verify RED**

Expected: compilation fails because managed files and lifecycle methods do not exist.

- [ ] **Step 3: Implement Android managed file access**

Add:

```kotlin
implementation("androidx.documentfile:documentfile:1.0.1")
```

`AndroidManagedBookFiles` uses `filesDir/epubs` for internal files and `DocumentFile.fromTreeUri(...).createFile("application/epub+zip", displayName)` for SAF. Exact document URIs are stored in `LocalBook.documentUri`. SAF books are copied to `cacheDir/reader/<local-id>.epub` before parsing or upload.

- [ ] **Step 4: Refactor `LocalBookStore` around transactional operations**

Implement these signatures:

```kotlin
suspend fun download(
    remote: BookDto,
    destination: StorageDestination,
    writer: suspend (OutputStream) -> Long,
): LocalBook

suspend fun importBook(
    source: InputStream,
    sourceName: String,
    destination: StorageDestination,
    autoUpload: Boolean,
    parser: EpubParser,
): LocalBook

suspend fun markUploaded(localId: String, remote: BookDto): LocalBook
suspend fun delete(localId: String): Boolean
suspend fun pendingUploads(): List<LocalBook>
suspend fun materialize(book: LocalBook): File
```

Copy imports in buffered chunks while updating `MessageDigest.getInstance("SHA-256")`. Use `local-$checksum` as the imported local ID. Parse the managed copy before index insertion and discard it on invalid EPUB.

- [ ] **Step 5: Run lifecycle and full tests and verify GREEN**

- [ ] **Step 6: Commit**

```powershell
git add app/build.gradle.kts app/src/main/java/com/amwangfan/omnireader/data/ManagedBookFiles.kt app/src/main/java/com/amwangfan/omnireader/data/LocalBookStore.kt app/src/test/java/com/amwangfan/omnireader/data/LocalBookStoreTest.kt
git commit -m "Add managed EPUB import and deletion"
```

### Task 5: Make ViewModel Local-First and Add Upload Retry

**Files:**
- Modify: `app/src/main/java/com/amwangfan/omnireader/AppViewModel.kt`

- [ ] **Step 1: Add a failing pure sync-state test before orchestration changes**

Extend `LocalBookStoreTest` so an upload failure leaves `PENDING_UPLOAD`, while `markUploaded` records `remoteBookId` and `SYNCED`. Run it and verify the new success assertion fails before implementing the transition.

- [ ] **Step 2: Add local-first state and commands**

Change the initial screen to `Shelf` unconditionally. Extend `AppUiState` with:

```kotlin
val defaultDownloadTreeUri: String = ""
val autoUploadImports: Boolean = true
val pendingDeleteBook: LocalBook? = null
```

Add `showSettings`, `setDefaultDownloadTree`, `clearDefaultDownloadTree`, `setAutoUploadImports`, `importBook(uri)`, `download(book, oneTimeTreeUri)`, `requestDelete`, `cancelDelete`, and `confirmDelete`.

- [ ] **Step 3: Implement pending-upload retry**

At the start of authenticated `sync()`:

```kotlin
val uploadFailures = mutableListOf<String>()
for (book in localBookStore.pendingUploads()) {
    runCatching {
        val file = localBookStore.materialize(book)
        val remote = api.uploadBook(state.serverUrl, state.accessToken, book.title, file)
        localBookStore.markUploaded(book.id, remote)
    }.onFailure {
        uploadFailures += book.title
    }
}
```

Continue with remote catalog fetch even when one upload fails. Trigger the same sync after login and attempt it immediately after an auto-upload import when authenticated.

- [ ] **Step 4: Update reader and download access**

Open books through `localBookStore.materialize(book)`. Resolve download destination using one-time URI, global preference, then internal fallback. Match server rows using `remoteBookId`.

- [ ] **Step 5: Run full unit tests and compile Kotlin**

```powershell
.\gradlew.bat testDebugUnitTest compileDebugKotlin
```

Expected: success.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/amwangfan/omnireader/AppViewModel.kt app/src/test/java/com/amwangfan/omnireader/data/LocalBookStoreTest.kt
git commit -m "Add local-first navigation and upload retry"
```

### Task 6: Build Shelf, Online Library, Settings, and Reader UI

**Files:**
- Modify: `app/src/main/java/com/amwangfan/omnireader/MainActivity.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/ui/OmniReaderApp.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/ui/ShelfScreen.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/ui/OnlineLibraryScreen.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/ui/SettingsScreen.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/ui/ReaderScreen.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/ui/BookComponents.kt`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add Material icons and reduce `MainActivity` to app setup**

Add the Compose BOM-managed `androidx.compose.material:material-icons-extended` dependency. Keep `MainActivity` responsible only for `setContent`, theme, and `OmniReaderApp()`.

- [ ] **Step 2: Implement scaffold and SAF launchers**

In `ui/OmniReaderApp.kt`, use:

```kotlin
rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(viewModel::importBook)
}
rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri?.let { persistTreePermission(context, it); viewModel.setDefaultDownloadTree(it.toString()) }
}

private fun persistTreePermission(context: Context, uri: Uri) {
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
}
```

Use a second tree launcher plus remembered pending `BookDto` for per-download overrides. Persist `FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION` before sending a tree URI to the ViewModel.

The scaffold always exposes Shelf, Online Library, and Settings bottom navigation outside the reader. Use familiar Material icons with content descriptions.

- [ ] **Step 3: Implement the shelf and delete confirmation**

Shelf top app bar has an import icon. Each stable-size book row shows metadata, sync state, read action, and trash icon. `AlertDialog` names the book and calls `confirmDelete` only from the destructive button.

- [ ] **Step 4: Implement online-library signed-out and download states**

When server/token is absent, show a compact connection prompt and Settings action. Authenticated rows expose a download menu with `Default location` and `Choose folder`. Locally present rows expose Read.

- [ ] **Step 5: Implement Settings**

Use unframed full-width sections for Server, Account, and Storage & sync. Include server address, login/logout state, default-folder choose/reset controls, and a Material `Switch` for automatic import upload.

- [ ] **Step 6: Implement reader import action and chapter controls**

Keep reading content scrollable and chapter controls stable. Add an import icon in the reader top app bar. Picker cancellation leaves the current reader untouched.

- [ ] **Step 7: Compile and run full unit tests**

```powershell
.\gradlew.bat testDebugUnitTest compileDebugKotlin
```

Expected: success with no test failures.

- [ ] **Step 8: Commit**

```powershell
git add app/build.gradle.kts app/src/main/java/com/amwangfan/omnireader/MainActivity.kt app/src/main/java/com/amwangfan/omnireader/ui
git commit -m "Add shelf and settings experience"
```

### Task 7: Version, Build, Install, and Device Acceptance

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`

- [ ] **Step 1: Update version and documentation**

Set `versionCode = 2` and `versionName = "0.2.0"`. Update README with local-first startup, Settings, SAF destination behavior, import upload retry, deletion boundaries, build command, and APK path.

- [ ] **Step 2: Run the release gate from a clean build**

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug
```

If the Windows SDK environment remains unavailable, copy the repository to one timestamped N100 directory under `/tmp`, install SDK components only inside that directory, run the same Gradle tasks, copy the APK back, and delete the entire remote directory.

Expected: all tests pass and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Install and launch on the connected device**

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.amwangfan.omnireader
adb shell monkey -p com.amwangfan.omnireader -c android.intent.category.LAUNCHER 1
adb shell dumpsys package com.amwangfan.omnireader | Select-String "versionCode=|versionName="
```

Expected: device `AMRF026325004615` is online, install reports `Success`, and version `0.2.0` is reported.

- [ ] **Step 4: Run manual acceptance checklist**

- Cold launch reaches Shelf without server configuration.
- Import from Shelf copies and opens a valid EPUB.
- Import from Reader opens the newly imported EPUB.
- Auto-upload-disabled import shows `Local only`.
- Offline auto-upload import shows `Pending upload`; later manual sync changes it to `Synced`.
- Settings persists server URL, session, auto-upload switch, and global folder.
- Default folder and one-time folder downloads remain readable after app restart.
- Delete removes the managed copy but leaves the source and server catalog entry intact.

- [ ] **Step 5: Commit**

```powershell
git add app/build.gradle.kts README.md
git commit -m "Prepare Android 0.2.0 device build"
```
