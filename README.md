# OmniReader Android

Native Kotlin and Jetpack Compose client for the self-hosted [OmniReader server](https://github.com/amwangfan/OmniReader).

Read [HANDOFF.md](HANDOFF.md) before continuing or merging historical preview work.

## Current scope

- Open directly to a local shelf without requiring server configuration.
- Configure the server, account, download folder, and import sync behavior in Settings.
- Log in through `/api/v1/auth/login`, refresh expired access tokens, and clear session state on logout.
- Sync the EPUB library from `/api/v1/books`.
- Download EPUB files to app storage, a persistent default SAF folder, or a one-time folder.
- Download through a temporary file, verify SHA-256 and publish only after the EPUB parses successfully.
- Import local EPUB files into OmniReader-managed storage without modifying the source file.
- Upload imported books by default and retry pending uploads on the next app-driven sync.
- Delete managed local copies without deleting the source file or server copy.
- Open an EPUB with a minimal spine/XHTML text reader and previous/next chapter controls.
- Keep a random, preference-backed Android device identity without hardware identifiers.
- Persist block-level EPUB progress and local-date reading totals in an atomic local state file.
- Resume from the latest server position across devices while retaining independent per-device rows.
- Render stable hashed reading blocks and restore changed EPUBs with ordered locator fallbacks.
- Detect server EPUB revisions and replace downloaded copies only after the new file parses successfully.
- Schedule network-constrained WorkManager progress synchronization every six hours.

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

Android release/debug validation is usually performed on the N100 builder as user `n100`. Source, Android SDK and Gradle caches should be placed in a temporary build directory; downloads can use the builder-local `127.0.0.1:5081` proxy. Preserve `/home/n100/.android/debug.keystore` for stable debug signing.

The final builder command is:

```sh
./gradlew clean testDebugUnitTest assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

After the APK is copied back to Windows, remove the temporary N100 source, SDK and Gradle directories.

## Server configuration and security

Enter the URL of a running OmniReader server in the app. Cleartext HTTP is enabled only to support private-network testing. Use it exclusively over a trusted encrypted overlay such as Tailscale. Use HTTPS for any internet-reachable deployment.

Android backup is disabled, but tokens remain in app-private preferences rather than Keystore-backed encrypted storage.

Do not install or replace the APK during intermediate development if the Android device is disconnected; leave device installation to the final verification phase.

## Verification status

The feature branch has previously passed N100 Linux validation with:

- `clean`
- `testDebugUnitTest`
- `assembleDebug`

Current Android installation is intentionally deferred until the end of the broader server/client work.

## Important limitations

- EPUB rendering is minimal and does not fully support CSS, images, links, footnotes or advanced layout.
- There is no table-of-contents UI, typography/theme configuration, bookmark, highlight or note support.
- Foreground and WorkManager synchronization still need deeper end-to-end testing.
- Release signing and an upgrade distribution mechanism are not configured.

## Project handoff

[HANDOFF.md](HANDOFF.md) contains the Android code map, validation matrix, known risks and recommended continuation order. The server repository remains the cross-repository source of truth for API behavior.

## Authorship

The project and commits belong to the repository owner's GitHub identity. Codex has been used as an engineering assistant; it is not presented as the code author.
