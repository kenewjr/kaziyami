# Kototoro Architecture, Source Loading & Anime Support Research

## 1. Official Repository & Metadata

- **Official App Repository**: [Kototoro-app/Kototoro](https://github.com/Kototoro-app/Kototoro)
- **App Version**: `v2.3.2` (Latest release: October 24, 2026; branch: `devel`)
- **License**: `Apache License 2.0` (`Apache-2.0`)
- **Parsers Repository**: [skepsun/kototoro-parsers](https://github.com/skepsun/kototoro-parsers) (`GPL-3.0`)
- **Target Device Runtime**: Android & Multiplatform Desktop (JVM)

---

## 2. How Kototoro Loads Sources

Based on direct inspection of the codebase in `Kototoro-app/Kototoro` (branch `devel`):

Kototoro supports three distinct source ingestion mechanisms:

### (a) In-App Compiled Parsers (Kotatsu Lineage)
- Kototoro retains a Kotatsu-derived core (`parser-api`), where parsers implement `ContentSource` and related interfaces compiled into the app or bundled parser libraries.

### (b) External Plugin JARs (`build_plugin.sh`)
- `skepsun/kototoro-parsers` compiles Kotlin sources to bytecode, converts them using Android SDK `d8` into `classes.dex`, and packages them into `kototoro-parsers-plugin.jar`.
- Users import this file directly into Kototoro via **Settings -> Remote Sources**.

### (c) Native Aniyomi / Mihon APK & JAR Conversion (`source-host` module)
- Kototoro contains a dedicated runtime bridge under `source-host/` and `dex-convert/`:
  - `org.skepsun.kototoro.dex.ApkExtensionConverter`: Directly accepts Android extension APKs (with `classes.dex` and binary `AndroidManifest.xml`). Converts binary AXML into text XML and packages the bytecode for JVM execution.
  - `org.skepsun.kototoro.source.host.MihonJarInspector`: Inspects `AndroidManifest.xml`. Scans for:
    ```kotlin
    val families = listOf(
        SourceEcosystem.MIHON to "tachiyomi.extension",
        SourceEcosystem.TSUNDOKU to "tachiyomi.novelextension",
        SourceEcosystem.ANIYOMI to "tachiyomi.animeextension",
    ).filter { (_, prefix) -> "$prefix.class" in values || "$prefix.factory" in values }
    ```
    Identifies Aniyomi extensions by checking `tachiyomi.animeextension.class` or `tachiyomi.animeextension.factory`.
  - `org.skepsun.kototoro.source.host.AniyomiSourceRuntime`: Dynamically bridges Aniyomi extension classes (`AnimeHttpSource` / `ParsedAnimeHttpSource`) via reflection. Calls `getPopularAnime`, `getLatestUpdates`, `getSearchAnime`, `getAnimeDetails`, `getEpisodeList`, and `getVideoList`.
  - `org.skepsun.kototoro.source.host.AniyomiModelAdapter`: Translates Aniyomi data models (`SAnime`, `SEpisode`, `Video`) into Kototoro domain models (`SourceContent`, `SourceChapter`, `SourcePage`).

---

## 3. Video / Anime Support Verification

**Does Kototoro support video and anime streaming?**
**YES. Kototoro explicitly and fully supports video playback.**

### Concept Mapping Table

| Kaziyami / Aniyomi Concept | Kototoro Interface / Model | Mapping Details in `AniyomiSourceRuntime` / `AniyomiModelAdapter` |
|---|---|---|
| `AnimeHttpSource` | `SourceRuntime` / `SourceDescriptor` | Extension entry class is loaded into a dedicated `URLClassLoader` lease and wrapped by `AniyomiSourceRuntime`. |
| `SAnime` | `SourceContent` | Mapped via `AniyomiModelAdapter.content(anime)`: `title`, `url`, `coverUrl`, `status`, `genres` -> `tags`. Content rating is marked adult if source is adult. |
| `getPopularAnime(page)` | `runtime.popular(page)` | Returns `AnimesPage` mapped to `List<SourceContent>`. |
| `getLatestUpdates(page)` | `runtime.latest(page)` | Returns `AnimesPage` mapped to `List<SourceContent>`. |
| `getSearchAnime(page, query, filters)` | `runtime.search(page, query, filters)` | Maps Kototoro search parameters and native filters to Aniyomi `AnimeFilterList`. |
| `getAnimeDetails(anime)` | `runtime.details(content)` | Hydrates description, genres, status, and author. |
| `getEpisodeList(anime)` | `runtime.chapters(content)` | `SEpisode` list mapped to `List<SourceChapter>`. Episode numbers, names, and relative URLs preserved. |
| `getVideoList(episode)` | `runtime.pages(chapter)` | `Video` list mapped to `List<SourcePage>`. Each video stream is treated as a playable page containing the video URL, custom HTTP headers (`Referer`, `User-Agent`), quality label, and subtitle tracks (`SourceExternalTrack`). |
| HLS / MP4 Streams | In-app Video Player | Kototoro player reads `SourcePage.url` and attached request headers to stream HLS playlists or direct MP4 media. |

---

## 4. Cloudflare & Anti-Bot Protection

- **Host Mechanism**: Kototoro provides `core-cloudflare` and `HostBrowser.kt` using an Android WebView session.
- **Cookie Sharing**: Cookies cleared via WebView are maintained in the host's `CookieJar` and passed to OkHttpClient calls.
- **Extension Integration**: Kaziyami's deterministic headers (`BrowserProfile`), token bucket rate limiting, and structured `ChallengeClassifier` cooperate seamlessly with Kototoro's WebView solver prompts.

---

## 5. Distribution Format for Kototoro

Because Kototoro v2.3+ natively features `ApkExtensionConverter` and `AniyomiSourceRuntime`, users can load Kaziyami extensions into Kototoro in two ways:

1. **Direct Aniyomi Extension APK**:
   Kototoro can import the standard Kaziyami APKs directly via **Settings -> Remote Sources -> Install Extension APK**.
2. **Standalone Dex/Jar Plugin (`kaziyami-kototoro-all-v1.4.1.jar`)**:
   A dedicated all-in-one JAR packaging the engine, multi-source themes, and source definitions alongside a standard text `AndroidManifest.xml` declaring `tachiyomi.animeextension` metadata. This can be loaded into Kototoro without requiring an Android APK package installation.
