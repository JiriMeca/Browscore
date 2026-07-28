# Brows

A modern, minimal, Chrome-inspired open-source Android browser with **multiple
tabs**, **"open in new tab"**, **NO incognito / private-browsing mode**, and
**NO way to delete browsing history**. History is recorded permanently in a
local SQLite database.

This project was created to satisfy a very specific set of constraints:

| Requirement | How this project meets it |
|---|---|
| Modern & minimal (Chrome-like) | Clean white UI with a pill-shaped omnibox (security icon + URL + refresh), a thin progress bar, a 5-button bottom toolbar, and a full-screen tab switcher grid. No heavy toolbars, no clutter. |
| Multiple tabs | Each tab owns its own `WebView` stacked in a `FrameLayout`; only the active tab is visible. Tap the tabs button (top-right, with count badge) to open a full-screen tab-switcher grid. Add/close tabs freely. |
| Open in new tab | Long-press any link → context menu: "Open in new tab", "Open in background tab", "Copy link", "Share link". `target="_blank"` links and `window.open()` also open a new tab via `onCreateWindow`. |
| No incognito mode | Android `WebView`'s private-browsing constructor was deprecated in API 17 and removed. A plain `WebView(this)` always uses the normal shared profile. Every tab — including ones created by `onCreateWindow` — uses the same normal profile and records history identically. There is no "private tab" anywhere in the UI or data model. |
| No way to delete history | The `HistoryDao` exposes **only `insert` and `select`** operations. There is no `@Delete` method and no `DELETE FROM` SQL anywhere in the codebase. The UI has no "Clear history", "Clear data", or trash button anywhere. The history database file is never deleted or truncated. |
| Compilable on Windows | Pure Gradle + Kotlin project. Builds with `gradlew.bat` on any Windows 10/11 machine. No native toolchain needed. |
| Under 3 GB of storage | Project source: ~5 MB. JDK 17: ~300 MB. Android command-line tools + SDK platform 34 + build-tools: ~1 GB. Gradle distribution (auto-downloaded): ~150 MB. Total well under 2 GB. |
| Real, functional .APK | Produces a standard `app-debug.apk` / `app-release-unsigned.apk` that installs and runs on Android 7.0+ (API 24+). |

---

## Why tabs don't break the "no incognito" guarantee

This is the key design question: if multi-window support is enabled (required
for `target="_blank"` and `window.open()`), doesn't that allow private browsing?

**No.** Here's why:

1. Android `WebView` has **no private-browsing constructor anymore** (removed
   in API 17). Every `WebView` instance — no matter how it's created — uses
   the **same normal shared profile**. There is no API to request a private
   profile.
2. In `onCreateWindow`, we create a **new normal `WebView`** and attach the
   **same `HistoryRecordingWebViewClient`** to it. The new tab records history
   identically to every other tab.
3. There is no "private tab" / "incognito tab" option in the tab switcher, the
   overflow menu, or anywhere in the UI. The only way to open a new tab is via
   the "+" button, `target="_blank"`, or "Open in new tab" — all of which
   create normal, history-recording tabs.

So multi-tab browsing is fully supported without compromising the core
guarantee.

---

## Why not just modify Firefox or Chrome?

Both are too large to build inside a 3 GB storage budget on Windows:

- **Firefox for Android (Fenix)** — official docs require **≥ 40 GB** of free disk space (`firefox-source-docs.mozilla.org/setup/windows_build.html`). The GeckoView engine + Rust toolchain + Gradle cache alone exceeds 3 GB before you even start compiling.
- **Chromium / Chrome / Brave / Bromite / Cromite** — the source checkout is ~30 GB and a full build needs **~100 GB+**. Completely out of scope.
- **Forking an existing WebView browser (e.g. SmartCookieWeb, Lightning)** — possible, but those projects *deliberately include* a private-browsing mode. Removing every code path that touches it across a large existing codebase is error-prone, and you inherit all of their dependencies.

A **custom WebView-based browser** is the only approach that genuinely fits all five constraints. Android System WebView (preinstalled on every Android device since 7.0) provides the rendering engine — your app just provides the chrome, the URL bar, and the history database.

---

## Project structure

```
android-browser/
├── settings.gradle.kts          ← Gradle project config
├── build.gradle.kts             ← Top-level build (plugin versions)
├── gradle.properties            ← Gradle / AndroidX flags
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties   ← Pins Gradle 8.9
├── gradlew.bat                  ← Gradle wrapper for Windows
├── download-wrapper.bat         ← Fetches gradle-wrapper.jar (binary)
├── setup.bat                    ← One-time env check + local.properties
├── build-debug.bat              ← Builds app-debug.apk
├── build-release.bat            ← Builds app-release-unsigned.apk
├── local.properties.example     ← Copy to local.properties, edit SDK path
└── app/
    ├── build.gradle.kts         ← App module (deps: Room, Material, etc.)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/permanentbrowser/app/
        │   ├── BrowserApp.kt              ← Application class
        │   ├── MainActivity.kt            ← Tab manager + Chrome-like UI + link context menu
        │   ├── Tab.kt                     ← Tab data class (id, webView, title, url)
        │   ├── TabCardAdapter.kt          ← Adapter for the tab-switcher grid
        │   ├── WebViewClientImpl.kt       ← Records EVERY page (every tab) into history
        │   ├── WebChromeClientImpl.kt     ← onCreateWindow → new normal tab (no private mode)
        │   ├── HistoryEntry.kt            ← Room entity
        │   ├── HistoryDao.kt              ← APPEND-ONLY: insert + select only
        │   ├── HistoryDatabase.kt         ← Room database (no destructive migration)
        │   ├── HistoryActivity.kt         ← Read-only history viewer
        │   ├── HistoryAdapter.kt
        │   ├── Bookmark.kt                ← Room entity
        │   ├── BookmarkDao.kt             ← Bookmarks CAN be deleted (user-curated)
        │   ├── BookmarkDatabase.kt
        │   ├── BookmarksActivity.kt
        │   ├── BookmarkAdapter.kt
        │   ├── SettingsActivity.kt        ← Homepage, JS, popups (no "clear data")
        │   └── Prefs.kt                   ← SharedPreferences helper
        └── res/                           ← Layouts, strings, icons, themes
```

---

## How "no incognito" is enforced (defense in depth)

1. **At the WebView API level.** `WebView(this)` uses the normal profile. The
   old `WebView(context, attrs, defStyleAttr, privateBrowsing)` constructor was
   deprecated in API 17 and **removed** — there is no way to request a private
   browsing context from Android System WebView.

2. **At the window-creation level.** Multi-window support is ON so that
   `target="_blank"` and `window.open()` create new tabs. But `onCreateWindow`
   always creates a **normal-profile `WebView`** with the same
   `HistoryRecordingWebViewClient` attached — there is no code path that
   creates a private browsing context, because the API for one no longer
   exists.

3. **At the UI level.** The overflow menu contains only: History, Bookmarks,
   Share, Settings. The bottom toolbar has: back, forward, home, bookmark,
   menu. The tab switcher has: tab cards, a "+" new-tab button, and a Done
   button. There is no "New private tab", no "Incognito", no "Clear data",
   no "Clear history" item anywhere in the app.

4. **At the history-DAO level.** `HistoryDao` has only `@Insert` and `@Query
   (SELECT ...)` methods. There is no `@Delete`, no `@Update`, and no
   `DELETE FROM history` SQL anywhere. Even if you added a button, there is
   no DAO method it could call.

5. **At the database level.** `HistoryDatabase` is built WITHOUT
   `fallbackToDestructiveMigration()`, so app updates never wipe history.

---

## How to build on Windows (under 3 GB)

### Total disk budget

| Component | Approx. size |
|---|---|
| This project source | ~5 MB |
| JDK 17 (Temurin) | ~300 MB |
| Android command-line tools | ~150 MB |
| Android SDK Platform 34 + build-tools 34 | ~250 MB |
| Gradle 8.9 distribution (auto-downloaded on first build) | ~150 MB |
| Gradle build cache + `.gradle/` | ~200 MB |
| Final APK output | ~8 MB |
| **Total** | **~1.1 GB** ✅ |

### Step-by-step

1. **Install JDK 17.**
   - Download Temurin 17 from https://adoptium.net/temurin/releases/?version=17
   - Run the `.msi` installer.
   - Set `JAVA_HOME` to the JDK install folder, e.g.
     `C:\Program Files\Eclipse Adoptium\jdk-17.0.11.9-hotspot`
   - Open a NEW Command Prompt and verify: `java -version`

2. **Install the Android SDK (command-line tools only, NOT full Studio).**
   - Go to https://developer.android.com/studio#command-line-tools-only
   - Download the Windows ZIP (`commandlinetools-win-...zip`).
   - Create `C:\Android\Sdk\cmdline-tools\` and extract the ZIP so that you have
     `C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat`.
   - Open a Command Prompt and install the needed SDK packages:
     ```bat
     "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=C:\Android\Sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"
     ```
   - Accept the licenses when prompted.

3. **Copy this project folder** to your Windows machine, e.g. `C:\PermanentBrowser\`.

4. **Run setup** (one-time):
   ```bat
   cd C:\PermanentBrowser
   setup.bat
   ```
   This checks Java + the SDK, writes `local.properties`, and downloads the
   Gradle wrapper JAR.

5. **Build a debug APK:**
   ```bat
   build-debug.bat
   ```
   The APK appears at:
   ```
   C:\PermanentBrowser\app\build\outputs\apk\debug\app-debug.apk
   ```

6. **Install on your phone** (enable USB debugging in Developer Options first):
   ```bat
   "C:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
   ```

### Building a release (signed) APK

A release APK must be signed before Android will install it.

```bat
REM 1. Create a keystore ONCE (remember the passwords you choose):
keytool -genkey -v -keystore permanent.keystore -alias permanent -keyalg RSA -keysize 2048 -validity 10000

REM 2. Build the release variant:
build-release.bat

REM 3. Sign the APK:
"C:\Android\Sdk\build-tools\34.0.0\apksigner.bat" sign --ks permanent.keystore --out app-release.apk app\build\outputs\apk\release\app-release-unsigned.apk
```

The signed `app-release.apk` is ready to install on any device.

---

## Verifying the "no deletion" guarantee

After installing and browsing a few pages:

1. Open the menu → **History**. Every page you visited is listed with a
   timestamp.
2. Long-press any entry. Nothing happens — there is no context menu, no
   "Delete" option, no trash icon.
3. Open the menu → **Settings**. There is no "Clear browsing data", no
   "Clear history", no "Clear cache" toggle.
4. Force-stop the app and reopen it. History is still there.
5. (Optional) Inspect the database on a rooted device or via `adb`:
   ```bat
   adb shell run-as com.permanentbrowser.app ls databases/
   adb shell run-as com.permanentbrowser.app sqlite3 databases/permanent_history.db "SELECT COUNT(*) FROM history;"
   ```
   The count only ever goes up.

---

## Customising

- **Homepage:** Settings → Homepage field.
- **App name / icon:** edit `res/values/strings.xml` and
  `res/drawable/ic_launcher_foreground.xml`.
- **Block more:** add a URL filter inside `HistoryRecordingWebViewClient`.
- **Export history:** add a new `@Query` to `HistoryDao` (read-only is fine) and
  an export button. **Do not** add a delete method — that would defeat the
  purpose of this browser.

---

## License

This source is released under the MIT License. See `LICENSE` if present, or
treat it as MIT. The AndroidX / Material / Room / Kotlin dependencies retain
their own licenses.
