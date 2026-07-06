# OmniReader Android

Native Kotlin + Jetpack Compose Android client for OmniReader.

## Current scope

- Open directly to a local shelf without requiring server configuration.
- Configure the server, account, download folder, and import sync behavior in Settings.
- Log in through `/api/v1/auth/login` and sync the EPUB library from `/api/v1/books`.
- Download EPUB files to app storage, a persistent default SAF folder, or a one-time folder.
- Import local EPUB files into OmniReader-managed storage without modifying the source file.
- Upload imported books by default and retry pending uploads on the next app-driven sync.
- Delete managed local copies without deleting the source file or server copy.
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

Install the completed build on a connected device with:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.amwangfan.omnireader -c android.intent.category.LAUNCHER 1
```

The app enables cleartext HTTP traffic so it can connect to the current Tailscale demo server:

```text
http://100.114.93.90:18080
```

## Authorship

This project is authored and owned by the repository owner's GitHub account. Codex is used as an end-to-end engineering assistant, but Codex is not the code author.
