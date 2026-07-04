# OmniReader Android

Native Kotlin + Jetpack Compose Android client for OmniReader.

## MVP scope

- Configure a self-hosted OmniReader server URL.
- Log in with the server admin account through `/api/v1/auth/login`.
- Sync the EPUB library from `/api/v1/books`.
- Download EPUB files through `/api/v1/books/{bookId}/download` into app-local storage.
- Show a local shelf of downloaded EPUB files.
- Open an EPUB with a minimal spine/XHTML text reader and previous/next chapter controls.

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

## Authorship

This project is authored and owned by the repository owner's GitHub account. Codex is used as an end-to-end engineering assistant, but Codex is not the code author.
