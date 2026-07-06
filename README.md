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
- Keep a random, preference-backed Android device identity without hardware identifiers.
- Persist block-level EPUB progress and local-date reading totals in an atomic local state file.
- Resume from the latest server position across devices while retaining independent per-device rows.
- Render stable hashed reading blocks and restore changed EPUBs with ordered locator fallbacks.
- Detect server EPUB revisions and replace downloaded copies only after the new file parses successfully.

Rooted and non-rooted devices use the same code path for this MVP. BOOX-specific root/no-root optimizations can be added after the basic reader flow is stable.

## Build

Android release/debug validation is performed on the N100 builder as user `n100`. Source, Android SDK,
and Gradle caches are placed in a temporary build directory, downloads use the builder-local
`127.0.0.1:5081` proxy, and `/home/n100/.android/debug.keystore` is preserved for stable debug signing.
The final builder command is:

```sh
./gradlew clean testDebugUnitTest assembleDebug
```

After the APK is copied back to Windows, remove the temporary N100 source, SDK, and Gradle directories.

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
