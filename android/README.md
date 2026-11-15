# Symphony No. 9 — Android project

This is an Android Studio / Gradle wrapper around the supplied Symphony No. 9 HTML app. The supplied artwork is installed as the launcher icon. The WebView uses a native Android media-session service and a MediaStyle notification with Previous / Play-Pause / Next controls.

## Build
- Open this folder in Android Studio, or install JDK 17 and Gradle 8.9.
- Run `gradle assembleDebug` from this folder.
- APK output: `app/build/outputs/apk/debug/app-debug.apk`.
- A GitHub Actions workflow builds and uploads the debug APK artifact on push, pull request, or manual run.

## Install
Transfer the debug APK to the phone and install it. Android may ask you to allow installation from that file manager/browser. Grant notification permission when prompted so playback controls can appear in the notification shade / lock screen.

## Storage permissions and limitations
- The app does **not** request unrestricted `MANAGE_EXTERNAL_STORAGE` access.
- The Android file picker uses the system Storage Access Framework (`ACTION_OPEN_DOCUMENT`) so you explicitly choose the audio/media/documents the app can read. This is the supported privacy-preserving Android approach.
- The original web app's full File System Access API folder handle / recursive live folder synchronization is available in compatible desktop browsers, but Android WebView does not implement `showDirectoryPicker()` / `FileSystemObserver`. Therefore, Android folder-wide live sync is not claimed as supported in this wrapper; selected files are available to the app through Android's picker.
- Playback notification integration observes HTML audio/video playback. Track metadata is currently derived from the page title; app-specific track metadata can be wired to the bridge later for richer album/artist artwork.


## Startup crash fix (Android 8–14)
The playback foreground service now calls `startForeground()` immediately from
`onStartCommand()`. Android terminates apps that call `startForegroundService()`
but do not promote the service within the required startup window. The activity
also initializes its active-instance reference before the WebView can dispatch
media callbacks. The duplicate `Activity` import was removed.
