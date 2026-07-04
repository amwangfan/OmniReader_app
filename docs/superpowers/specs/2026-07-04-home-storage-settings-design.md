# OmniReader Home, Storage, and Settings Design

## Context

The current Android MVP requires server configuration before the user can reach the library. It stores every downloaded EPUB in app-internal storage and has no settings screen, local import flow, selectable download destination, upload queue, or local deletion action.

This change makes local reading the primary experience while keeping the self-hosted server optional. A user can launch the app, import and read an EPUB without configuring a server, then connect and synchronize later.

## Goals

- Open directly to the local shelf on every cold start.
- Keep Shelf, Online Library, and Settings available as primary destinations.
- Support an optional persistent download folder and a per-download folder override.
- Import local EPUB files by copying them into OmniReader-managed storage.
- Upload imported books to the configured server by default without blocking local import.
- Retry pending uploads during startup sync, login sync, and manual sync.
- Delete managed local copies without deleting the source import or server copy.
- Preserve existing downloaded-book records through index migration.

## Non-Goals

- Background synchronization with WorkManager.
- Deleting or archiving books on the server from the Android app.
- Referencing an imported source file in place without copying it.
- Moving an imported source file.
- Reading directly from a ZIP stream or replacing the current EPUB parser.
- BOOX root-specific behavior.

## Navigation and Screens

### Startup and Navigation

`AppScreen.Shelf` is always the initial screen. Server configuration and authentication do not gate access to local books.

The bottom navigation contains:

- **Shelf**: the startup destination and local managed library.
- **Online Library**: the server catalog, or a connection prompt when no valid session exists.
- **Settings**: server, account, storage, and import-upload preferences.

The reader remains a focused screen outside the bottom-navigation destinations. Returning from the reader goes back to the shelf.

### Shelf

The shelf lists managed local books and displays a concise synchronization state: `Pending upload`, `Synced`, or `Local only`. It provides:

- an import action in the top app bar;
- a read action for each book;
- a delete icon for each book;
- a confirmation dialog before deletion.

An empty shelf presents an import action rather than a server-configuration requirement.

### Online Library

When the server URL or access token is missing, the screen shows a connection action that opens Settings. When authenticated, the screen shows the remote catalog and manual synchronization.

For a remote book not stored locally, the download action offers:

- **Download to default location**;
- **Choose folder for this download**.

The per-download folder overrides the global folder for that operation only. Destination priority is:

1. per-download tree URI;
2. global tree URI from Settings;
3. existing app-internal EPUB directory.

### Settings

Settings is divided into three unframed sections:

- **Server**: server URL, save action, and current connection state.
- **Account**: admin username/password login when signed out; account status and logout when signed in.
- **Storage and sync**: current default download location, choose-folder action, reset-to-app-storage action, and an `Automatically upload imported books` switch enabled by default.

Changing or clearing the global folder affects future imports and downloads only. Existing book records continue to point to their original managed files.

### Reader

The reader retains chapter navigation and adds an import action to the top app bar. Importing from the reader copies and opens the newly imported book after successful local processing. The existing book remains open when the picker is cancelled or import fails.

## Android File Selection

The app uses the Storage Access Framework and requires no broad storage permission.

- `OpenDocument` with MIME type `application/epub+zip` selects a local EPUB for import.
- `OpenDocumentTree` selects a global or one-time managed destination.
- Selected tree permissions are persisted with read and write grants so books remain readable and deletable after process restart.
- Picker cancellation is a no-op and does not show an error.

The Activity owns the Compose activity-result launchers. ViewModel events request a picker, and picker results are returned to the ViewModel as URI strings. This keeps Android launcher objects out of business logic.

## Local Book Model

`LocalBook` keeps a stable local ID and gains storage and synchronization metadata:

- `remoteBookId: String?`: server identity after download or successful upload;
- `storageKind`: `INTERNAL` or `DOCUMENT_URI`;
- `documentUri: String?`: exact SAF document URI for an externally stored managed copy;
- `source`: `SERVER_DOWNLOAD` or `LOCAL_IMPORT`;
- `syncState`: `SYNCED`, `PENDING_UPLOAD`, or `LOCAL_ONLY`;
- existing title, author, filename, size, checksum, and timestamp fields.

New serialized fields have defaults so the old JSON index remains decodable. On load, records from the old schema are normalized as internal server downloads with `remoteBookId = id` and `syncState = SYNCED`, then the migrated index is persisted.

Imported-book IDs are derived from the copied file SHA-256 checksum. Reimporting identical content updates the existing shelf record instead of creating a duplicate.

## Storage Architecture

`LocalBookStore` remains the owner of the index and delegates byte storage through a small destination abstraction:

- internal destinations create ordinary files under `filesDir/epubs`;
- SAF destinations create EPUB documents under a persisted tree URI;
- both expose an output stream for copy or network download;
- failed operations delete their incomplete output before returning an error.

The store exposes operations for:

- resolving destination priority;
- importing a content URI into managed storage;
- recording a completed server download;
- materializing a managed book as a readable local file;
- deleting the managed copy and then its index record;
- finding and updating pending uploads.

For reading, internal files are passed directly to `EpubParser`. A SAF document is copied to an app cache file because the existing parser uses random-access ZIP APIs. Cache files are replaceable and are not shelf records.

## Import and Upload Flow

1. The user selects an EPUB.
2. The ViewModel resolves the global destination or app-internal fallback.
3. `LocalBookStore` copies the source to a temporary managed output while calculating SHA-256.
4. The copied EPUB is parsed to validate it and obtain title and author metadata.
5. The temporary output is promoted to the managed book and the index is updated.
6. The original source URI is closed and never modified.
7. If automatic upload is enabled, the book is marked `PENDING_UPLOAD`; otherwise it is `LOCAL_ONLY`.
8. If a valid server session exists, upload is attempted immediately after local import completes.
9. Upload success stores the returned server book ID and changes the state to `SYNCED`.
10. Upload failure keeps the local book readable and pending, and reports a non-blocking message.

`OmniApi` adds a multipart `POST /api/v1/books` operation with title and EPUB file parts. It also changes download writing to accept an output stream so HTTP data can be written to either internal storage or SAF without an intermediate duplicate.

## Synchronization

Startup synchronization runs only when server URL and access token are present, but local shelf loading always runs. Login success and the manual sync action use the same sequence:

1. upload pending imported books one at a time;
2. update each successful local record with its server ID and `SYNCED` state;
3. leave failed records pending and continue processing the remaining queue;
4. fetch the current remote catalog;
5. refresh the local shelf.

Downloaded server books are matched to remote catalog entries by `remoteBookId`, not local ID assumptions.

WorkManager retries remain out of scope; retries occur only during app-driven synchronization.

## Deletion

Deletion requires explicit confirmation. `LocalBookStore` deletes the managed copy first and removes the index entry only after deletion succeeds.

- An internal managed file is deleted from app storage.
- A SAF managed document is deleted through its persisted document URI.
- If the managed file is already missing, the stale index record is removed.
- If deletion fails for another reason, the record remains and the error is shown.
- The source file used for import is never touched.
- The corresponding server book is never archived or deleted.
- Deleting the currently open book returns the UI to the shelf after success.

## Error Handling

- Invalid EPUB content removes the incomplete managed copy and does not create an index record.
- Network download failure removes the incomplete managed copy and does not create an index record.
- Revoked SAF access produces an actionable storage-access error while preserving the shelf record.
- Upload failure leaves the book in `PENDING_UPLOAD` and does not block reading.
- Authentication or catalog errors remain visible as snackbar messages and do not redirect away from the shelf.
- UI busy state is scoped to the active operation; cancelling a picker never enters a busy state.

## Testing

Unit tests cover:

- destination precedence: one-time tree, global tree, then internal;
- old-index migration and serialization defaults;
- imported-book checksum identity and duplicate upsert behavior;
- synchronization transitions from pending to synced and retention after failure;
- deletion ordering, missing-file cleanup, and failure preservation;
- multipart upload request path, bearer authorization, title field, and EPUB part;
- existing server URL, local index, and EPUB parser behavior.

Android-specific picker wiring is verified through compilation and device acceptance. The release gate is:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.amwangfan.omnireader
adb shell monkey -p com.amwangfan.omnireader -c android.intent.category.LAUNCHER 1
```

Device acceptance checks startup without configuration, Settings login, global and one-time folders, local import, pending upload retry, reading, and deletion.
