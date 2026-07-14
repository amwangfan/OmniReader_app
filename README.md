# OmniReader Android

Native Kotlin + Jetpack Compose Android client for OmniReader.

## MVP scope

- Configure a self-hosted OmniReader server URL.
- Log in with the server admin account through `/api/v1/auth/login`.
- Sync the EPUB library from `/api/v1/books`.
- Download EPUB files through `/api/v1/books/{bookId}/download` into app-local storage.
- Show a local shelf of downloaded EPUB files.
- Open an EPUB with a minimal spine/XHTML text reader and previous/next chapter controls.
- Refresh expired access tokens without forcing a new login.
- Register the Android device and synchronize chapter progress with the server.
- Verify downloaded EPUB files with SHA-256 before publishing them to the local shelf.

Rooted and non-rooted devices use the same code path for this MVP. BOOX-specific root/no-root optimizations can be added after the basic reader flow is stable.

## Build

```powershell
cd E:\Codex\Projects\OmniReader_app
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```

The app enables cleartext HTTP traffic so it can connect to the current Tailscale demo server:

```text
http://100.114.93.90:18080
```

Use plain HTTP only through a trusted encrypted overlay such as Tailscale. Android backups are disabled so saved session tokens are not copied into device backup archives.

See [the 2026-07-14 change notes](CHANGELOG.md) for the latest implementation details and remaining limitations.

## Authorship

This project is authored and owned by the repository owner's GitHub account. Codex is used as an end-to-end engineering assistant, but Codex is not the code author.
