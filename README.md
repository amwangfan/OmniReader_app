# OmniReader Android

Native Kotlin and Jetpack Compose client for the self-hosted [OmniReader server](https://github.com/amwangfan/OmniReader).

> Development status (2026-07-15): the latest Android work is in [Draft PR #1](https://github.com/amwangfan/OmniReader_app/pull/1) on `agent/android-sync-preview`. It builds and passes unit tests, but has not been exercised on an emulator or physical device. The corresponding server work is [OmniReader Draft PR #2](https://github.com/amwangfan/OmniReader/pull/2).

Read [HANDOFF.md](HANDOFF.md) before continuing or merging.

## Implemented on the preview branch

- Configure one self-hosted OmniReader server and log in.
- Refresh expired access tokens and revoke the refresh session on logout.
- List server books and download normalized EPUB files.
- Download through a temporary file, verify SHA-256 and atomically publish to app-local storage.
- Show a local shelf and open books in a minimal spine/XHTML text reader.
- Persist the current chapter and resume it after reopening.
- Register a stable device identity and reconcile progress with the server.
- Schedule network-constrained WorkManager synchronization every six hours.
- Retry transient background failures and clear an invalid session.
- Disable Android backup to reduce accidental token export.

The server may accept EPUB, MOBI, AZW/AZW3, TXT, PDF and HTML inputs, but it converts non-EPUB files before the app downloads them. This app is EPUB-only.

## Requirements and build

- Android SDK 35.
- JDK 17.
- Gradle 8.10.2 is downloaded by the wrapper.
- Minimum Android version: API 26.
- Target Android version: API 35.

Linux/macOS:

```bash
chmod +x gradlew
./gradlew testDebugUnitTest assembleDebug
```

Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The Debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Server configuration and security

Enter the URL of a running OmniReader server in the app. Cleartext HTTP is enabled only to support private-network testing. Use it exclusively over a trusted encrypted overlay such as Tailscale. Use HTTPS for any internet-reachable deployment.

No active demo address is documented because no temporary deployment has been verified. Android backup is disabled, but tokens remain in app-private preferences rather than Keystore-backed encrypted storage.

## Verification status

The preview branch has passed:

- `testDebugUnitTest`;
- `assembleDebug`;
- unit tests covering local-index compatibility and progress conflict decisions.

Latest recorded Android run: [GitHub Actions run 29303113280](https://github.com/amwangfan/OmniReader_app/actions/runs/29303113280).

The following remain unverified:

- installation and interaction on an emulator or physical device;
- login/download/read/resume against a running preview server;
- token expiry and refresh on a real runtime;
- manual and six-hour background synchronization;
- two-device conflict behavior;
- interrupted download and checksum-failure UI.

Keep PR #1 as Draft until these checks are completed.

## Important limitations

- Reading position is a chapter index only; there is no within-chapter scroll restoration.
- EPUB rendering is minimal and does not fully support CSS, images, links, footnotes or advanced layout.
- There is no table-of-contents UI, typography/theme configuration, bookmark, highlight or note support.
- Foreground and WorkManager synchronization still need a process-wide coordinator/file lock.
- Local delete, re-download, repair and server-archived-book status flows are not implemented.
- Release signing and an upgrade distribution mechanism are not configured.

## Project handoff

[HANDOFF.md](HANDOFF.md) contains the Android code map, validation matrix, known risks and recommended continuation order. The cross-repository source of truth is [the server handoff](https://github.com/amwangfan/OmniReader/blob/agent/server-sync-hardening/docs/HANDOFF.md).

## Authorship

The project and commits belong to the repository owner's GitHub identity. Codex has been used as an engineering assistant; it is not presented as the code author.
