# OmniReader Android handoff

Last updated: 2026-07-15

This is the client-specific handoff. The cross-repository overview, API contract, deployment checklist and new-account continuation prompt live in [the server handoff](https://github.com/amwangfan/OmniReader/blob/agent/server-sync-hardening/docs/HANDOFF.md).

## Current state

- Repository: [amwangfan/OmniReader_app](https://github.com/amwangfan/OmniReader_app)
- Active branch: `agent/android-sync-preview`
- Pull request: [Draft PR #1](https://github.com/amwangfan/OmniReader_app/pull/1)
- Server counterpart: [OmniReader Draft PR #2](https://github.com/amwangfan/OmniReader/pull/2)
- Build target: Android API 35, minimum API 26, JDK 17, Gradle 8.10.2
- Application version: `0.1.0` / version code `1`

The branch has passed unit tests and Debug APK assembly on GitHub Actions. It has not been installed or tested interactively on an emulator or device.

## Important code locations

| Area | Path |
|---|---|
| UI state, foreground sync and reader orchestration | `app/src/main/java/com/amwangfan/omnireader/AppViewModel.kt` |
| Compose screens and activity | `app/src/main/java/com/amwangfan/omnireader/MainActivity.kt` |
| Server/token/device preferences | `app/src/main/java/com/amwangfan/omnireader/data/AppPreferences.kt` |
| API models | `app/src/main/java/com/amwangfan/omnireader/data/Models.kt` |
| HTTP/auth/refresh/download client | `app/src/main/java/com/amwangfan/omnireader/data/OmniApi.kt` |
| Local EPUB files and JSON index | `app/src/main/java/com/amwangfan/omnireader/data/LocalBookStore.kt` |
| Periodic background sync | `app/src/main/java/com/amwangfan/omnireader/sync/BackgroundSyncWorker.kt` |
| Shared progress conflict policy | `app/src/main/java/com/amwangfan/omnireader/sync/ProgressSyncPolicy.kt` |

## Behavior added on the preview branch

- Automatic access-token refresh after an authenticated request returns 401.
- Server refresh-session revocation during logout.
- Credential clearing when the configured server changes.
- Stable generated device identity and server registration.
- Local chapter persistence and resume-on-open.
- Foreground and background bidirectional progress reconciliation.
- Unique WorkManager periodic work every six hours, constrained to network connectivity.
- Retry for I/O and server failures; invalid-session clearing.
- `.part` downloads, SHA-256 verification and atomic replacement.
- Serialized/atomic local index updates and legacy-index compatibility.
- Java 17 toolchain, disabled backup and CI build/test coverage.

## Validation matrix

| Check | Status |
|---|---|
| `testDebugUnitTest` | Passed in GitHub Actions |
| `assembleDebug` | Passed in GitHub Actions |
| Legacy local-index decoding | Unit tested |
| Timestamp conflict policy | Unit tested |
| APK installation | Not tested |
| Login to live server | Not tested |
| EPUB download/read | Not tested |
| Restart/resume | Not tested |
| Access-token expiry/refresh | Not tested on Android runtime |
| Manual progress sync | Not end-to-end tested |
| WorkManager execution | Not observed on device |
| Two-device conflict | Not tested |
| Interrupted/corrupt download UI | Not tested |

Recorded run: [29303113280](https://github.com/amwangfan/OmniReader_app/actions/runs/29303113280).

## Priority risks

1. `AppViewModel` foreground sync and `BackgroundSyncWorker` may overlap.
2. Each `LocalBookStore` instance owns its own mutex, so it does not guarantee process-wide mutual exclusion.
3. Device timestamps determine last-write-wins; future-skewed clocks can block subsequent progress.
4. Progress is only `chapter:N`; it cannot restore within a long chapter.
5. Server archive/removal is not visible as an explicit state on the local shelf.
6. Tokens are not protected by a Keystore-backed credential layer.
7. Cleartext HTTP is permitted globally for private Tailscale testing.
8. The reader is deliberately minimal and does not satisfy full EPUB presentation expectations.

## Recommended continuation order

1. Obtain or deploy a private preview server from the matching server branch.
2. Install the Debug APK on an emulator or BOOX/Android device.
3. Complete the end-to-end matrix below and record non-sensitive evidence in PR #1.
4. Add one process-wide sync coordinator and file locking/atomic transaction coverage.
5. Add fixed JSON contract fixtures shared with the server tests.
6. Add local delete/re-download/repair and remote archive-state UI.
7. Move secrets to a Keystore-backed layer and restrict cleartext networking.
8. Upgrade position semantics and reader presentation.

## End-to-end acceptance matrix

Use at least one small EPUB and one server-converted TXT/MOBI/PDF result.

1. Configure the private server URL.
2. Log in and list server books.
3. Download, verify and open an EPUB.
4. Move to a known chapter, close the app and confirm resume.
5. Trigger manual synchronization and confirm the server web page shows the device and chapter.
6. Change progress remotely/from a second device and confirm the newer update wins.
7. Expire or invalidate the access token and confirm refresh occurs once without a login loop.
8. Disconnect the network, trigger sync, restore the network and observe retry.
9. Let WorkManager run or invoke it through an instrumentation/debug path.
10. Corrupt or interrupt a download and confirm no incomplete EPUB appears on the shelf.

Do not mark the PR ready solely because the Gradle build passes.
