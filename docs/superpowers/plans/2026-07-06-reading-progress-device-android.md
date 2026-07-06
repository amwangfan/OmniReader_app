# Reading Progress and Device Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add stable Android device identity, block-level EPUB locators, local reading state/time totals, and synchronization with the OmniReader server.

**Architecture:** The parser produces chapters and reading blocks with stable hashes. A JSON-backed `ReadingStateStore` owns locators, daily totals, and dirty state; `OmniApi` implements the shared server contract; `AppViewModel` orchestrates sync while Compose reports visible-block changes and foreground reading time.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, coroutines, OkHttp, JUnit

---

## Shared Contract

Use the exact camelCase fields and locator v1 shape in `E:\Codex\Projects\OmniReader\docs\superpowers\specs\2026-07-06-reading-progress-device-management-design.md`. Do not introduce client-only wire names.

### Task 1: Add revision, device, and progress DTOs

**Files:**
- Modify: `app/src/main/java/com/amwangfan/omnireader/data/Models.kt`
- Modify: `app/src/test/java/com/amwangfan/omnireader/data/LocalBookIndexTest.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/data/ProgressModelsTest.kt`

- [ ] **Step 1: Write failing serialization tests**

Round-trip exact locator, device, and progress JSON. Verify legacy book JSON without `contentRevision` still decodes.

- [ ] **Step 2: Add serializable models**

```kotlin
@Serializable
data class ReadingLocator(
    val version: Int = 1,
    val contentRevision: String,
    val chapterHref: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int = 0,
    val textQuote: String = "",
    val textHash: String = "",
    val chapterProgress: Double = 0.0,
    val bookProgress: Double = 0.0,
)

@Serializable
data class DeviceRegistrationRequest(
    val id: String,
    val displayName: String,
    val systemName: String,
    val platform: String = "android",
    val manufacturer: String,
    val model: String,
    val appVersion: String,
)

@Serializable
data class ProgressPutRequest(
    val deviceId: String,
    val locator: ReadingLocator,
    val percentage: Double?,
    val clientUpdatedAt: String?,
    val dailyReadSeconds: Map<String, Long>,
)
```

Add `contentRevision: String = ""` to `BookDto` and `LocalBook`, plus response DTOs for device and progress payloads.

- [ ] **Step 3: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ProgressModelsTest" --tests "*LocalBookIndexTest"
git add app/src/main/java/com/amwangfan/omnireader/data/Models.kt app/src/test
git commit -m "feat: add reading sync models"
```

### Task 2: Persist UUID identity and resolve device name

**Files:**
- Modify: `app/src/main/java/com/amwangfan/omnireader/data/AppPreferences.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/data/DeviceIdentity.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/data/DeviceIdentityTest.kt`

- [ ] **Step 1: Write failing identity tests**

Test that an existing UUID is reused, an absent UUID is generated/persisted once, a nonblank system name wins, and manufacturer/model form the fallback display name.

- [ ] **Step 2: Implement identity boundaries**

`AppPreferences.getOrCreateDeviceId(uuidFactory)` persists the UUID. `DeviceIdentityProvider` reads `Settings.Global.DEVICE_NAME` when accessible, falls back to normalized `Build.MANUFACTURER` and `Build.MODEL`, and reads app version from package metadata.

- [ ] **Step 3: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*DeviceIdentityTest"
git add app/src/main/java/com/amwangfan/omnireader/data app/src/test
git commit -m "feat: add stable Android device identity"
```

### Task 3: Parse EPUBs into stable blocks

**Files:**
- Modify: `app/src/main/java/com/amwangfan/omnireader/reader/EpubParser.kt`
- Modify: `app/src/test/java/com/amwangfan/omnireader/reader/EpubParserTest.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/reader/LocatorResolver.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/reader/LocatorResolverTest.kt`

- [ ] **Step 1: Write failing parser/resolver tests**

Assert source hrefs survive, `p/h1/h2/li/blockquote/pre` become ordered blocks, normalized SHA-256 hashes are stable, and resolution follows exact hash, chapter hash search, index, chapter percentage, book percentage, then chapter-start fallback.

- [ ] **Step 2: Introduce block models**

```kotlin
data class EpubChapter(
    val href: String,
    val title: String,
    val blocks: List<EpubBlock>,
) {
    val text: String get() = blocks.joinToString("\n\n") { it.text }
}

data class EpubBlock(
    val index: Int,
    val kind: String,
    val text: String,
    val textHash: String,
)
```

- [ ] **Step 3: Implement locator generation and fallback**

`LocatorResolver.locatorFor(...)` calculates bounded quote and percentages. `resolve(...)` returns chapter/block/offset plus a reason enum used for UI messages.

- [ ] **Step 4: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EpubParserTest" --tests "*LocatorResolverTest"
git add app/src/main/java/com/amwangfan/omnireader/reader app/src/test/java/com/amwangfan/omnireader/reader
git commit -m "feat: add block-level EPUB locators"
```

### Task 4: Persist reading state and daily seconds

**Files:**
- Create: `app/src/main/java/com/amwangfan/omnireader/data/ReadingStateStore.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/reader/ReadingTimeTracker.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/data/ReadingStateStoreTest.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/reader/ReadingTimeTrackerTest.kt`

- [ ] **Step 1: Write failing store/time tests**

Test atomic JSON round-trip, state per book/device, dirty/clean transitions, empty/corrupt files, active-only elapsed time, idempotent stop, and splitting seconds across a local-date boundary.

- [ ] **Step 2: Implement `ReadingStateStore`**

Write `reading_state.json.tmp`, flush/close it, then rename it over `reading_state.json`. Preserve corrupt input as `.corrupt-<timestamp>` before returning empty state. Each record stores locator, daily absolute totals, dirty state, and last server timestamp.

- [ ] **Step 3: Implement `ReadingTimeTracker`**

Inject monotonic elapsed time and local date providers. `start`, `checkpoint`, and `stop` return seconds to merge into the correct daily totals; repeated `stop` returns zero.

- [ ] **Step 4: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ReadingStateStoreTest" --tests "*ReadingTimeTrackerTest"
git add app/src/main app/src/test
git commit -m "feat: persist reading state and daily time"
```

### Task 5: Implement device and progress HTTP calls

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/amwangfan/omnireader/data/OmniApi.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/data/OmniApiProgressTest.kt`

- [ ] **Step 1: Add MockWebServer and failing tests**

```kotlin
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

Assert HTTP method, path, bearer header, and exact JSON for registration and progress GET/PUT. Cover server `400`, `403`, and `404` mapping to `ApiException`.

- [ ] **Step 2: Implement calls**

```kotlin
suspend fun registerDevice(baseUrl: String, token: String, request: DeviceRegistrationRequest): DeviceDto
suspend fun getProgress(baseUrl: String, token: String, bookId: String, deviceId: String): ProgressResponse
suspend fun putProgress(baseUrl: String, token: String, bookId: String, request: ProgressPutRequest): ProgressResponse
```

- [ ] **Step 3: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*OmniApiProgressTest"
git add app/build.gradle.kts app/src/main/java/com/amwangfan/omnireader/data/OmniApi.kt app/src/test
git commit -m "feat: add device and progress API client"
```

### Task 6: Orchestrate resume and sync

**Files:**
- Modify: `app/src/main/java/com/amwangfan/omnireader/AppViewModel.kt`
- Create: `app/src/main/java/com/amwangfan/omnireader/data/ReadingSyncCoordinator.kt`
- Create: `app/src/test/java/com/amwangfan/omnireader/data/ReadingSyncCoordinatorTest.kt`

- [ ] **Step 1: Write failing coordinator tests**

With fake API/store/identity boundaries, prove registration precedes sync, dirty local state uploads first, clean state uses global only for resume, another device's global row does not mark local dirty, failed uploads remain dirty, and successful uploads store the server timestamp.

- [ ] **Step 2: Implement coordinator**

Expose `syncBook`, `syncAllDirty`, and `resumeFor`. Return the resume source device name and locator-resolution reason. Never require network to open a local book.

- [ ] **Step 3: Connect ViewModel**

After login register and sync dirty states. On open parse locally, request resume when authenticated, and fall back locally on errors. Checkpoints persist immediately and launch a debounced upload; close/background uploads without blocking navigation.

- [ ] **Step 4: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ReadingSyncCoordinatorTest"
git add app/src/main app/src/test
git commit -m "feat: synchronize reading progress"
```

### Task 7: Render and report block-level progress

**Files:**
- Modify: `app/src/main/java/com/amwangfan/omnireader/MainActivity.kt`
- Modify: `app/src/main/java/com/amwangfan/omnireader/AppViewModel.kt`

- [ ] **Step 1: Use a keyed `LazyColumn`**

Render each block as a stable keyed item. Observe `LazyListState.firstVisibleItemIndex` and offset using `snapshotFlow`, debounce it, and call the ViewModel checkpoint API. Keep chapter controls outside the scrolling list.

- [ ] **Step 2: Restore and notify**

Initialize list state from the resolved block/offset. Show a snackbar when resuming from another device or using a revision fallback.

- [ ] **Step 3: Count foreground reader time**

Start tracking only while the reader is visible and lifecycle resumed. Checkpoint on pause, close, and chapter change. Never count shelf/settings time.

- [ ] **Step 4: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
git add app/src/main
git commit -m "feat: track block-level reader progress"
```

### Task 8: Cross-device acceptance

**Files:**
- Verify all files above

- [ ] **Step 1: Run clean verification**

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug
```

- [ ] **Step 2: Install on available devices**

Use `adb devices -l`, install the debug APK, confirm UUID persistence and system-derived name, and preserve existing app data unless migration testing explicitly needs a clear.

- [ ] **Step 3: Run the two-device scenario**

A reads/uploads; B resumes A and uploads further; A's row remains unchanged until A reads; A resumes from B; web totals match absolute daily totals.

- [ ] **Step 4: Inspect state**

```powershell
git diff --check
git status --short --branch
```

Expected: clean Android feature branch with passing tests and APK build.
