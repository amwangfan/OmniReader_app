# OmniReader Android changes

## 2026-07-14

### Added

- Automatic access-token refresh after an authenticated request returns HTTP 401.
- Server-side refresh-token revocation during logout.
- A stable generated device ID and device registration during synchronization.
- Local chapter-position persistence and bidirectional server progress reconciliation.
- Resume from the saved chapter when reopening a downloaded book.
- Temporary `.part` downloads, SHA-256 verification, and atomic replacement of completed EPUB files.
- Serialized and atomic local-book index updates.
- Saved chapter display on the local shelf.
- A GitHub Actions workflow for unit testing and building a debug APK on test branches and pull requests.
- Network-constrained periodic background synchronization every six hours through WorkManager.
- A shared, unit-tested last-write-wins progress policy used by foreground and background synchronization.

### Changed

- Changing to a different server clears tokens from the previous server and requires a fresh login.
- Chapter changes reset the reader scroll position to the top.
- Android application backup is disabled to reduce accidental token exposure.
- The Kotlin toolchain now uses Java 17, matching the app bytecode target and Android Gradle Plugin requirement.

### Remaining

- Progress currently uses chapter indexes, not a Readium-compatible locator or within-chapter scroll position.
- Tokens remain stored in app-private preferences; a Keystore-backed credential layer is still recommended.
- HTTPS certificate handling and release signing are not implemented yet.
