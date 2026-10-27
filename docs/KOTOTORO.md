# Kototoro Architecture, Extension Loader & Invariants (Phase 10 Verification)

Verified against official source repository: [Kototoro-app/Kototoro](https://github.com/Kototoro-app/Kototoro) (commit `414e58f`, branch `devel`).

---

## 1. Extension Ecosystems in Kototoro

Kototoro is a unified multiplatform reader/player supporting multiple source ecosystems:

| Ecosystem / Extension Type | Host Loader / Inspector | Runtime Dispatcher | Manifest / Entry Identification | Support Status |
|---|---|---|---|---|
| **Aniyomi Anime Extensions (Variant A)** | `org.skepsun.kototoro.aniyomi.AniyomiExtensionLoader` | `source-host/AniyomiSourceRuntime` | `<uses-feature android:name="tachiyomi.animeextension" />` | **Native (Android APK & Desktop JVM)** |
| **Mihon Manga Extensions** | `org.skepsun.kototoro.mihon.MihonExtensionLoader` | `source-host/MihonSourceRuntime` | `<uses-feature android:name="tachiyomi.extension" />` | **Native (Android APK & Desktop JVM)** |
| **Kototoro Plugin JARs (Variant B)** | `parser-host/ParserPluginInspector` & `ParserPluginRegistry` | `parser-host/ParserSourceRuntime` | `org/skepsun/kototoro/parsers/ContentParserFactoryKt.class` | **Native (Desktop JVM & Android ART)** |
| **Tsundoku Novel Extensions** | `org.skepsun.kototoro.tsundoku.TsundokuNovelRepository` | `source-host/MihonSourceRuntime` | `<uses-feature android:name="tachiyomi.novelextension" />` | **Native (Android APK & Desktop JVM)** |
| **TVBox & CloudStream** | `source-tvbox/MacCmsSourceRuntime` | `source-tvbox` | JSON / Dex descriptors | **Cross-Platform** |

Citations:
- `parser-host/src/main/kotlin/org/skepsun/kototoro/parserhost/ParserPluginInspector.kt:9-34, 108-112`
- `parser-host/src/main/kotlin/org/skepsun/kototoro/parserhost/ParserPluginRegistry.kt:58-95, 215-234`
- `source-host/src/main/kotlin/org/skepsun/kototoro/source/host/AniyomiSourceRuntime.kt:40-83, 130-190`
- `app/src/main/res/values/strings.xml:431-450, 2644-2700`

---

## 2. Kototoro JAR Plugin Architecture & Contract (Variant B)

### 2.1 Entrypoint & Factory
Kototoro detects and loads standalone JAR parser plugins via reflection without requiring Android manifest files or Dex conversion on Desktop:
- **Factory Entry Class**: `org.skepsun.kototoro.parsers.ContentParserFactoryKt` (must exist as zip entry `org/skepsun/kototoro/parsers/ContentParserFactoryKt.class`).
- **Method Signature**:
  ```kotlin
  fun newParser(source: ContentParserSource, context: ContentLoaderContext): ContentParser
  ```
- **Context Interface**: `org.skepsun.kototoro.parsers.ContentLoaderContext` (provides `httpClient: OkHttpClient`, `cookieJar: CookieJar`, `requestBrowserAction`, `getConfig(source)`).
- **Source Enum**: `org.skepsun.kototoro.parsers.model.ContentParserSource` implementing `org.skepsun.kototoro.parsers.model.ContentSource`.
  Every JAR plugin defines its own enum; the host reads enum constants reflectively.
- Citations: `ParserPluginInspector.kt:18-22, 108-112`, `ParserPluginRegistry.kt:216-234`.

### 2.2 Bytecode Format & Execution Environments
- **Desktop JVM (Windows, macOS, Linux)**:
  Kototoro's `ParserPluginInspector.inspect(Path)` verifies Java `.class` bytecode up to the host JVM class version ceiling (Java 11 / Java 17).
  If a JAR contains ONLY `.dex` files without `.class` files, Desktop JVM throws `ParserPluginFailure.DEX_ONLY` (`ParserPluginInspector.kt:105`).
- **Android Runtime (ART)**:
  Android's `JarExtensionLoader` requires `classes.dex` inside the root of the JAR archive.
- **Unified Fat JAR Requirement**:
  A single distribution JAR containing BOTH standard Java `.class` files AND `classes.dex` (generated via Android SDK `d8`) executes natively across Desktop JVM AND Android devices without dynamic conversion!
- Citations: `ParserPluginInspector.kt:99-107`, `docs/architecture/dynamic_plugin_system.md:38-45`, `parser-host/src/test/kotlin/org/skepsun/kototoro/parserhost/DexPluginRoundTripTest.kt:22-77`.

### 2.3 ClassLoader Hierarchy & Isolation
- `ParserPluginClassLoader` loads plugins child-first for plugin-owned classes:
  - `PLUGIN_OWNED`: `ContentParserFactoryKt`, `ContentParserSource`, and plugin implementations.
  - Host-provided parent-delegated classes: `parser-api` interfaces, `okhttp3.*`, `org.jsoup.*`, `kotlin.*`, `kotlinx.coroutines.*`.
- Plugins MUST declare `parser-api`, `okhttp3`, and `jsoup` as `compileOnly` to avoid class cast collisions across ClassLoader boundaries.
- Citations: `ParserPluginClassLoader.kt:7-65`, `docs/plugin_development_guide.md:21-49`.

---

## 3. UI Navigation & Repository Paths

All UI paths and strings verified against Kototoro `devel` branch source:

### 3.1 Variant A: Adding Aniyomi Repository (APK)
1. Open **Settings** (`SettingsActivity.kt`).
2. Tap **Extension management** (`R.string.extension_management`, `strings.xml:2652`).
3. Select **Repositories** tab (`R.string.repositories_tab_title`, `strings.xml:2695`).
4. Tap **+** (**Add repository**, `R.string.add_repository_with_kind`, `strings.xml:2678`).
5. Select Kind: **ANIYOMI** (`UnifiedSourceKind.ANIYOMI`).
6. Enter repository URL:
   ```text
   https://raw.githubusercontent.com/kenewjr/kaziyami/repo/index.min.json
   ```
7. Confirm trust when prompted.

### 3.2 Variant B: Importing JAR or Adding JAR Repository
- **Local JAR File Import**:
  1. Open **Settings** -> **Extension management** (`R.string.extension_management`).
  2. Switch to **Packages** tab (`R.string.packages_tab_title`, `strings.xml:2696`).
  3. Tap **Import local JAR** chip (`R.string.import_local_jar`, `strings.xml:2686`; `UnifiedSourcesLists.kt:1112-1124`).
  4. Select `kaziyami-all-v1.4.1.jar` or individual source JAR.
  5. The plugin is instantly inspected, validated, and registered.
- **Remote JAR Repository**:
  1. Under **Repositories** tab, tap **+** (**Add repository**).
  2. Enter JAR repository index URL:
     ```text
     https://raw.githubusercontent.com/kenewjr/kaziyami/repo/jar/index.min.json
     ```

*Correction Note*: Older documentation mistakenly referenced non-existent paths like *"Settings -> Remote Sources -> Import Plugin JAR"*. The verified UI label is **Settings -> Extension management -> Packages -> Import local JAR**.

---

## 4. Video & Streaming Model in Kototoro

Kototoro natively models video content in its `parser-api` and `source-host`:

### 4.1 Content Models
- **Content Type**: `ContentType.HENTAI_VIDEO` (value 27) and `ContentType.VIDEO` (value 37) in `org.skepsun.kototoro.parsers.model.ContentType`.
- **Media Object**: `Content(id, title, url, publicUrl, coverUrl, source, contentRating = ContentRating.ADULT)`.
- **Episode Object**: `ContentChapter(id, title, number, url, source)`.

### 4.2 Stream & Subtitle Mapping
- In Kototoro, playable video streams map onto `ContentPage`:
  - `ContentPage.url`: Direct playable media stream URL (e.g. HLS `.m3u8` or MP4 stream).
  - `ContentPage.playbackLabel`: Human-readable quality/server label (e.g. `"1080p - Fembed"`, `"720p - StreamWish"`).
  - `ContentPage.playbackQuality`: Integer vertical resolution (e.g. `1080`, `720`, `480`).
  - `ContentPage.headers`: HTTP headers required for stream playback (e.g. `{"Referer": "https://..."}`).
  - `ContentPage.externalSubtitleTracks`: List of `ContentExternalTrack(url, lang, headers)` for soft subtitles.
- `ContentParser.getPageUrl(page)` returns `page.url` directly.
- Citations: `ContentPage.kt:6-40, 95-99`, `AniyomiSourceRuntime.kt:37-39, 180-190`.

---

## 5. Anti-Bot & Cloudflare Challenge Handling

Kototoro detects Cloudflare Turnstile / Managed Challenges via `CloudFlareHelper`:
- **Detection**: Checks `cf-ray`, `server`, `cf-mitigated: challenge`, HTTP 403/503 status, and HTML challenge signatures (`Just a moment`, `/cdn-cgi/challenge-platform/`, `__cf_chl_opt`).
- **Challenge Resolution Protocol**:
  1. Parser or web client detects challenge and obtains target URL via `CloudFlareHelper.getBrowserChallengeUrl(url)`.
  2. The parser triggers browser resolution via `context.requestBrowserAction(parser, challengeUrl)`.
  3. On Android, Kototoro displays an in-app WebView dialog allowing the user to solve the Turnstile challenge interactively.
  4. Upon completion, Kototoro captures the `cf_clearance` cookie and persists it to `context.cookieJar`.
  5. On Desktop JVM (without embedded browser), it surfaces a user-facing challenge warning.
- **Kaziyami Strict Rule**: Kaziyami never uses third-party captcha bypass services or headless spoofing. It relies exclusively on legitimate user browser interaction via `context.requestBrowserAction`.
- Citations: `parser-api/src/androidMain/kotlin/org/skepsun/kototoro/parsers/network/CloudFlareHelper.kt:18-65, 115-120`, `ContentLoaderContext.kt:57-59`.

---

## 6. Repository Index Schema (Variant B JAR Repository)

Kototoro supports both modern `ExtensionStoreIndex` JSON and legacy minified JSON:

### 6.1 `repo/jar/index.json` & `repo/jar/index.min.json`
```json
{
  "name": "Kaziyami Kototoro JAR Repository",
  "badgeLabel": "kaziyami-jar",
  "signingKey": "",
  "contact": {
    "website": "https://github.com/kenewjr/kaziyami"
  },
  "extensionList": {
    "extensions": [
      {
        "name": "Kaziyami: Adult Anime Suite",
        "packageName": "app.kaziyami.kototoro",
        "resources": {
          "apkUrl": "",
          "iconUrl": "icon/app.kaziyami.kototoro.png",
          "jarUrl": "jar/kaziyami-all-v1.4.1.jar"
        },
        "extensionLib": "1.4",
        "versionCode": 141,
        "versionName": "1.4.1",
        "contentWarning": "NSFW",
        "sources": [
          { "id": 7073589375897212735, "name": "Nekopoi", "language": "id", "homeUrl": "https://nekopoi.care" },
          { "id": 7369497329911250289, "name": "IndoHentai", "language": "id", "homeUrl": "https://indohentai.net" },
          { "id": 7400791378172011716, "name": "NontonHentai", "language": "id", "homeUrl": "https://nontonhentai.net" },
          { "id": 8058423919997530503, "name": "Hentaimama", "language": "en", "homeUrl": "https://hentaimama.io" },
          { "id": 9192959167101688940, "name": "JAVSubbed", "language": "en", "homeUrl": "https://javsubbed.net" },
          { "id": 2150907185602526076, "name": "Hanime", "language": "en", "homeUrl": "https://hanime.tv" }
        ]
      }
    ]
  }
}
```
- Citations: `core-source/src/commonMain/kotlin/org/skepsun/kototoro/extensions/repo/ExtensionStoreIndex.kt:8-60`, `ExtensionStoreDecoding.kt:25-57`.

---

## 7. Auto-Update Mechanism & Update Policy (Phase 11 Verification)

Verified from upstream codebase `Kototoro-app/Kototoro` (`devel` branch, commit `414e58f`):

### 7.1 Package Identification & Version Rule
- **Identifying Fields**:
  - `packageName: String` uniquely identifies the extension package (`UnifiedSourcesViewModel.kt:1915`).
  - Evaluated **per-package**, not per-source.
- **Version Fields**:
  - `versionCode: Long`: Monotonic integer build code (`ExtensionStoreIndex.kt:30`, `ExtensionIndexDto:844`).
  - `versionName: String`: Human-readable version string, e.g. `"1.4.2"` (`ExtensionStoreIndex.kt:31`, `ExtensionIndexDto:845`).
- **Exact Decision Rule (`isNewerThanInstalled`)**:
  - Source: `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionUpdatePolicy.kt:5-7`:
    ```kotlin
    internal fun RepoAvailableExtension.isNewerThanInstalled(installedVersionCode: Long?): Boolean {
        return installedVersionCode != null && versionCode > installedVersionCode
    }
    ```
  - **Invariant**: Strictly numeric greater-than (`>`) on `versionCode`.
  - **Downgrades & Re-publishes**: If `versionCode <= installedVersionCode`, the method returns `false` and the state remains `INSTALLED`. Same-version republishing is never flagged.

### 7.2 UI Presentation, Background Checks & Extension Groups
- **Packages Tab Presentation**:
  - File: `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/unified/UnifiedSourcesLists.kt:992-1100, 1425-1470`.
  - When `updateAvailableCount > 0`, a prominent `"Update all"` (`R.string.update_all_packages`) button appears in the packages header.
  - Individual package rows display:
    - Current installed version: `CompactTag(stringResource(R.string.installed_version_pattern, item.installedVersionName))`
    - Primary action button: `"Update"` (`R.string.update`).
- **Background Checks**:
  - **Verified**: Kototoro does **NOT** run a background `WorkManager` worker for extension updates (unlike manga chapter track or download workers).
  - Updates are checked on-demand whenever the user opens the Extensions / Sources management screen or performs pull-to-refresh (`refreshPackageCatalog()`).
  - Updates are **manual tap-to-install**: updates never install automatically without user action.
- **Extension Groups Snapshots**:
  - File: `UnifiedSourcesViewModel.kt:516-520` (`refreshPackagesAfterGroupApply()`).
  - When an Extension Group is restored or applied, source enable/disable states are persisted, installed extensions are reloaded from disk, and UI snapshots are refreshed without overwriting group configurations.

### 7.3 File Replacement, Storage & Trust Verification
- **Storage Path**:
  - Installed JAR files are stored at: `${context.filesDir}/plugins/${extension.pkgName}.jar` (`ExtensionInstallService.kt:121-124`).
- **Replacement Execution**:
  - File download: Written to temporary file in `cacheDir/extension-installs/`, then copied to `${context.filesDir}/plugins/${extension.pkgName}.jar` using `outputFile.copyTo(jarFile, overwrite = true)`.
- **Version Tracking**:
  - Stored in `SharedPreferences` `"jar_plugin_versions"`:
    - Key `${extension.pkgName}` -> `versionCode: Long`
    - Key `${extension.pkgName}:repo` -> `repoUrl: String`
- **Dynamic Reload**:
  - Immediately executes `GlobalExtensionManager.initialize(context)` to re-index and reload active parsers (`ExtensionInstallService.kt:131`).
- **Disagreement between Index and JAR**:
  - Kototoro relies on the index `versionCode` for UI badge display. Upon download, it updates `jar_plugin_versions` with the index's `versionCode`. If the JAR bytecode fails to instantiate, it is caught as broken by `JarBrokenSourceCache`.

### 7.4 Android Runtime Requirements (ART classes.dex)
- Android's `JarExtensionLoader.kt:147-152` instantiates `PluginClassLoader` (a `DexClassLoader` derivative) to load plugins.
- Android ART **strictly requires** Dalvik executable bytecode (`classes.dex`) in the root of the `.jar` archive. Loading a pure JVM `.class` archive on Android throws `ClassNotFoundException` / dex format errors.
- Desktop JVM strictly requires `.class` files (DEX-only throws `ParserPluginFailure.DEX_ONLY`).
- **Kaziyami Build Pattern**:
  `tools/package_kototoro_plugin.py` compiles standard Java `.class` files AND invokes Android SDK `d8` to package `classes.dex`, bundling both into a unified dual-runtime FAT JAR.

### 7.5 Variant A (Aniyomi APK) Detection
- Aniyomi extension APK update detection:
  - Compares `manifest.versionCode` against `repo/index.json` `code`.
  - Rule: `remote.code > installed.code`.
- Signature Continuity:
  - Kototoro checks `signatureValidator.isTrusted(packageName, signatureHash)` (`UnifiedSourcesViewModel.kt:1900-1904`). Mismatches flag `UNTRUSTED`.
  - Android OS Package Manager enforces signature matching on APK update; mismatched certificates fail installation with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

