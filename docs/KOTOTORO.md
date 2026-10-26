# Kototoro Architecture, Extension Loader & Invariants (Phase 9 Verification)

Verified against official source repository: [Kototoro-app/Kototoro](https://github.com/Kototoro-app/Kototoro) (commit tag `v2.3.2`, branch `devel`).

---

## 1. Extension Types Loaded by Android App

Kototoro is a unified multiplatform reader/player that supports multiple source ecosystems:

| Ecosystem / Extension Type | Android Loader Class | Manifest Feature / Identification | Support Status |
|---|---|---|---|
| **Aniyomi Anime Extensions** | `org.skepsun.kototoro.aniyomi.AniyomiExtensionLoader` | `<uses-feature android:name="tachiyomi.animeextension" />` | **Native (Android APK)** |
| **Mihon Manga Extensions** | `org.skepsun.kototoro.mihon.MihonExtensionLoader` | `<uses-feature android:name="tachiyomi.extension" />` | **Native (Android APK)** |
| **Tsundoku Novel Extensions** | `org.skepsun.kototoro.tsundoku.TsundokuNovelRepository` | `<uses-feature android:name="tachiyomi.novelextension" />` | **Native (Android APK)** |
| **Kototoro Plugin JARs** | `org.skepsun.kototoro.source.host.PluginClassLoader` | `classes.dex` with `ContentSource` implementation | **Native (JVM/Android)** |
| **CloudStream & TVBox** | `org.skepsun.kototoro.cloudstream.CloudstreamRepository` | JSON / Dex plugins | **Multiplatform** |
| **Desktop / iOS Conversion** | `org.skepsun.kototoro.dex.ApkExtensionConverter` | Dynamically converts APK DEX/AXML into JVM JAR | **Cross-Platform Bridge** |

Citations:
- `app/src/main/kotlin/org/skepsun/kototoro/aniyomi/AniyomiExtensionLoader.kt:45-165`
- `source-host/src/main/kotlin/org/skepsun/kototoro/source/host/MihonJarInspector.kt:28-36`

---

## 2. Exact Loader Contract for Aniyomi-Style APKs

### Package & Manifest Specifications
- **Package Name Prefix**: Must match `eu.kanade.tachiyomi.animeextension.<lang>.<name>` (`AniyomiExtensionLoader.kt:47`).
- **Feature Declaration**:
  ```xml
  <uses-feature android:name="tachiyomi.animeextension" />
  ```
- **Manifest `<meta-data>` Entries**:
  - `tachiyomi.animeextension.class`: Entry point class extending `eu.kanade.tachiyomi.animesource.AnimeSource`.
  - `tachiyomi.animeextension.factory`: Entry point factory extending `eu.kanade.tachiyomi.animesource.AnimeSourceFactory` (supported; Kototoro invokes `createSources()`).
  - `tachiyomi.animeextension.version`: Extension version string (e.g. `"14.1"`).
  - `tachiyomi.animeextension.nsfw`: `"1"` for adult/18+ content, `"0"` for safe.
  - `tachiyomi.extensionLib`: Required library version identifier.
- **Required `libVersion` Range**:
  - Checked at `AniyomiExtensionLoader.kt:53-54`:
    ```kotlin
    const val LIB_VERSION_MIN = 12.0
    const val LIB_VERSION_MAX = 16.0
    ```
  - Major version parsed via `MihonJarInspector.kt:119` (`Regex("1[2-6](?:\\.[0-9]+)*")`).
  - Version name major number MUST be between 12 and 16 (e.g. `14`). Values such as `1.4` trigger `UNSUPPORTED_LIBRARY` failures.
- **Class Lookup & Instantiation**:
  - Android runtime uses `ChildFirstPathClassLoader(apkPath, parentClassLoader)` (`AniyomiExtensionLoader.kt:145-161`).
  - Desktop JVM runtime converts APK via `ApkExtensionConverter` and instantiates using `URLClassLoader`.
  - Class is cast to `eu.kanade.tachiyomi.animesource.AnimeSource` or `AnimeSourceFactory`.
- **Signature & Trust Model**:
  - `core-source/src/commonMain/kotlin/org/skepsun/kototoro/extensions/repo/ExtensionFingerprintTrust.kt`.
  - Kototoro compares APK signing certificate SHA-256 fingerprint against `signingKeyFingerprint` declared in repository `repo.json` metadata.
  - Untrusted extensions prompt the user with a `TrustRepository` confirmation dialog before installation or execution.
- **SDK Constraints**:
  - Host app runs `minSdk = 26`, `targetSdk = 37` (`app/build.gradle:50, 53`).
  - Kaziyami extensions compile with `minSdk = 21`, `targetSdk = 33`, fully satisfied by Kototoro's runtime environment.

---

## 3. Extension Repositories & Index Format

### Adding a Repository in Kototoro UI
1. Navigate to **Settings** (`SettingsActivity.kt:1518`).
2. Select **Extension management** (`R.string.extension_management`, line 2652).
3. Switch to the **Repositories** tab (`UnifiedSourcesRoute`).
4. Tap **Add repository** (`+`).
5. In the kind selector dialog (`UnifiedSourcesDialogState.AddRepositoryKind`), select **ANIYOMI** (`UnifiedSourceKind.ANIYOMI`).
6. Paste the repository URL:
   ```text
   https://raw.githubusercontent.com/kenewjr/kaziyami/repo/index.min.json
   ```
   (Both repository root URL and URL ending in `index.min.json` are supported; `R.string.add_extension_repository_message`).
7. Confirm repository trust when prompted.

### Index & Repository Metadata Contract
- Source decoder: `core-source/src/commonMain/kotlin/org/skepsun/kototoro/extensions/repo/ExtensionStoreDecoding.kt`.
- **`index.min.json` fields**:
  - `name`: Human-readable label (e.g. `"Kaziyami: Nekopoi"`).
  - `pkg`: Android package ID (`"eu.kanade.tachiyomi.animeextension.id.nekopoi"`).
  - `apk`: Filename of APK in repo (`"eu.kanade.tachiyomi.animeextension.id.nekopoi-v14.1.apk"`).
  - `lang`: Language code (`"id"`, `"en"`).
  - `code`: Version code integer (`141`).
  - `version`: Version string (`"14.1"`).
  - `nsfw`: `"1"` or `"0"`. Honored: Kototoro parses `"1"` into `ContentRating.ADULT` (`ExtensionStoreDecoding.kt:89`).
  - `sources`: Array of `{ "id": Long, "name": String, "baseUrl": String }`.
- **`repo.json` metadata**:
  - `meta`: `{ "name": "Kaziyami", "shortName": "kaziyami", "website": "...", "signingKeyFingerprint": "..." }`.
  - `index_v2`: `"index.min.json"`.
- **Asset URL Resolution**:
  - APK URL: `baseUrl + "apk/" + apk` (`ExtensionStoreDecoding.kt:124`).
  - Icon URL: `baseUrl + "icon/" + packageName + ".png"` (`ExtensionStoreDecoding.kt:125`).
  - Invariant: Icons MUST use the full package name (e.g. `icon/eu.kanade.tachiyomi.animeextension.id.nekopoi.png`).

---

## 4. Video Playback & Cloudflare Handling

### Video Playback Architecture
- Model adapter: `org.skepsun.kototoro.source.host.AniyomiModelAdapter:108-137`.
- Source runtime: `org.skepsun.kototoro.source.host.AniyomiSourceRuntime:223-229`.
- **Mapping**:
  - `Video` -> `SourcePage` (`url = video.videoUrl`).
  - `video.headers`: Transformed into request headers sent with media chunk requests.
  - `video.subtitleTracks`: Mapped to `SourceExternalTrack` (label, language, stream URL).
  - `video.quality`: Mapped to quality title (`videoTitle`).
  - HLS: `.m3u8` playlists are passed directly to ExoPlayer on Android or MPV/VLC on desktop.
  - Dynamic / Deferred streams: If `video.initialized == false`, Kototoro calls `resolveVideo(video)` before playback begins.

### Cloudflare & Anti-Bot Clearance
- Clearance coordinator: `source-host/src/main/kotlin/org/skepsun/kototoro/source/host/HostBrowser.kt` and `core-cloudflare`.
- When an extension encounters HTTP 403 or Cloudflare Managed Challenge, Kototoro triggers `HostBrowser.session(source)`:
  - Opens Android WebView using the extension's explicit `User-Agent`.
  - Solves the challenge interactively.
  - Extracts cookies via Android `CookieManager` and injects them into the OkHttp `CookieJar` via `HostBrowser.importCookies`.
- **Parser Header Precedence**:
  - Kototoro respects extension-defined headers (`headersBuilder()` / `Video.headers`) over global client defaults.
  - Kaziyami's deterministic headers (desktop Chrome `User-Agent`, `Sec-Ch-Ua`, `Accept-Language`) and token bucket rate limiter operate without interference.

---

## 5. Verification Summary & Status

| Area | Contract Requirement | Kaziyami Status | Verification Evidence |
|---|---|---|---|
| **Package Prefix** | `eu.kanade.tachiyomi.animeextension.<lang>.<name>` | **Verified** | Built APK package IDs match prefix. |
| **libVersion** | Range `12.0` .. `16.0` (major 12..16) | **Verified** | Bumped all 6 extensions to `libVersion = "14"`. |
| **Manifest metadata** | `tachiyomi.extensionLib` = `"14"` | **Verified** | Added to `build_apks.py` manifest template. |
| **Icon URL** | `icon/<package_name>.png` | **Verified** | `generate_icons.py` writes package-named icons. |
| **Repository Index** | `index.min.json` + `repo.json` index_v2 | **Verified** | `generate_repo_index.py` emits compliant JSON. |
| **Video Playback** | HLS / MP4 with OkHttp headers | **Verified** | Model adapter verified against Kototoro code. |
| **Standalone JAR** | Unnecessary / Rejected | **Verified** | Logged in `docs/DECISIONS.md` (Decision 23). |
| **App Version** | Kototoro `v2.3.2` | **Verified** | Cloned and verified against source repository. |
