---
name: kaziyami-extension
description: >-
  Comprehensive guide and operational runbook for developing, debugging, testing,
  and packaging video extensions for Kototoro and Aniyomi in Kaziyami.
  Covers Kototoro 7-step test harness, stream hygiene, anti-bot constraints,
  host binary compatibility, and full verification pipeline.
---

# Kaziyami Extension Development Skill

Operational guide for building and maintaining video extensions targeting **Aniyomi** and **Kototoro**.

---

## 1. Core Architecture & Host Invariants

### Dual-Host Support Matrix
- **Aniyomi (APK)**: Loads extensions via `AniyomiExtensionLoader` looking for `tachiyomi.animeextension.class`, `nsfw=1`, and `aniyomix.extensionLib="14"`.
- **Kototoro (Android)**: Video playback routes exclusively through built-in `AniyomiExtensionLoader` (APK). Kototoro native JAR parser (`ContentParser`) does NOT support video streaming.

### Permanent Source IDs Lock
- 64-bit hash IDs in [docs/SOURCE_IDS.md](file:///e:/project/kaziyami/docs/SOURCE_IDS.md) are permanent.
- Modifying IDs breaks user bookmarks and watch history in host databases.
- Active Catalog (6 sources): `Nekopoi`, `Hanime1`, `3xchina`, `Hanime`, `Rule34Video`, `Hentaimama`.
- Retired/Discontinued (4 sources): `NontonHentai`, `RuleVid`, `IndoHentai`, `JAVSubbed`.
- `AnimeSource.name` MUST remain raw site name (e.g. `Nekopoi`, `Hanime1`), NEVER prefixed with `Kaziyami:`.
- Display labels in host UIs: `Kaziyami: <Site>`.

### Clean Relative Paths
- Store relative paths `UrlUtils.getPath(url)` for both `anime.url` and `episode.url`. Absolute URLs hardcode host domains and break user libraries when switching to mirror domains (`3xchina.net`, etc.).

### Host Class Isolation (Zero Vendored Classes)
- Extensions compile against official `extensions-lib:14` as `compileOnly`.
- Host packages (`eu.kanade.*`, `okhttp3.*`, `okio.*`, `kotlin.*`, `kotlinx.*`, `uy.kohesive.injekt.*`, `androidx.*`) MUST NEVER be bundled inside extension APKs.
- Test doubles live strictly in test source sets (`core/src/test/kotlin`), never in `main`.

---

## 2. Kototoro 7-Step In-App Verification Suite

Kototoro executes 7 sequential verification steps (`SourceTester`):
1. `LIST`: Popular/latest catalogue fetch.
2. `COVERS`: Cover image resolution.
3. `DETAILS`: Anime details metadata.
4. `CHAPTERS`: Episode list generation.
5. `PAGES`: Stream URL extraction and header verification.
6. `RANDOM`: Random item sample.
7. `SEARCH`: Keyword search with query `"a"`.

### Crucial Kototoro Constraints
- **Timeouts**: Max 20s per step, max 90s cumulative per source. Return page 1 immediately in `getSearchAnime` when `animes.isNotEmpty()` to preserve latency budget.
- **`LIST` Sidebar Widget Isolation**: Never include static sidebar widgets (e.g. `.nk-hentai-grid`) in `selectors.item`. Matching static widgets returns identical URLs across pages, halting infinite scroll with "No more pages". Prioritize genuine feed cards (`.nk-post-card`).
- **`COVERS` Thumbnail Extraction**: Support inline CSS `style="background-image: url('...')"` (e.g. Nekopoi `.nk-thumb-crop`), `.post-thumbnail [style*='background-image']`, and `meta[property='og:image']`. Reject empty/placeholder thumbnails.
- **`CHAPTERS` Recommendation Scroll Elimination**: Standalone video posts place horizontal recommendation scrolls (`#playlist-scroll`, `.video-playlist-wrapper`) below the player. Never scrape these as episodes. Synthesize strictly 1 `SEpisode` for the current `/watch?v=...` URL with the authentic video title.
- **`PAGES` Stream Verification**: Kototoro peeks 4KB from `video.videoUrl` and checks `looksLikeVideoStream(contentType, head)` (`video/*`, `application/*mpegurl`, `#EXTM3U`, `ftyp`).
  - **NEVER return HTML/embed URLs**. If direct streams cannot be extracted, provide fallback `Video(url, "Stream (Open in WebView if blocked)", url, headers)`.
  - **Socket & Stream Closure**: Always execute nested embed requests with `.use { ... }` to ensure OkHttp response bodies are closed and socket connections returned to pool.
  - **HLS Multi-Quality**: Unpack Dean Edwards packed streams and parse master playlists via `HlsExtractor` to expand multi-qualities (1080p, 720p).
- **`SEARCH` Single-Char Query Padding & Filter Composition**: Kototoro tests `"a"`. CMS backends (like KVS on Rule34Video) requiring $\ge 2$ characters return 404; pad single characters: `if (cleanQuery.length == 1) cleanQuery + cleanQuery else cleanQuery`. Compose category/tag slugs with `?filter=$sort` query parameters instead of dropping user sort choices.

---

## 3. Network Hygiene & Anti-Bot Rules

### Video Playback Headers
- Always construct video headers using `BrowserHeaders.mediaHeaders(referer, extraHeaders)`:
  - Guarantees browser `User-Agent` is included (prevents CDN 403).
  - Automatically strips HTML navigation headers (`Sec-Fetch-Dest: document`, `Upgrade-Insecure-Requests: 1`, `Sec-Fetch-Mode: navigate`, `Sec-Fetch-User`) to prevent CDN 404.

### Anti-Bot & Captcha Boundaries
- **No Third-Party Automated Captcha Solvers**: Cloudflare Turnstile binds clearances (`cf_clearance`) to device TLS fingerprint and host WebView session.
- **Host WebView Solver Integration**: Always prioritize `network.cloudflareClient` before `network.client` in `GenericThemeSource.client`.
- **Runtime User-Agent & Cookie Sync**: `BrowserProfile.setRuntimeUserAgent()` mirrors host WebView UA; `SimpleCookieJar` merges root-origin and URL cookies from `CookieManager`.

### Stream Resolution & DNS Pre-Validation
- Sequential extraction via `ExtractorRegistry.resolveSequential`; stop on first working playable stream.
- Ephemeral embed domains must pre-validate DNS resolution (`canResolveHost`) before appending stream candidates to avoid unresolvable host exceptions.

### Content Safety & Host NSFW Flags
- Zero tolerance for minors, CSAM, voyeurism, and non-consensual content ([core/src/main/kotlin/app/kaziyami/ContentPolicy.kt](file:///e:/project/kaziyami/core/src/main/kotlin/app/kaziyami/ContentPolicy.kt)).
- Host application manages adult filtering natively via `tachiyomi.animeextension.nsfw=1` manifest metadata.

---

## 4. Multisrc Engine Reference

| Theme | Module | Class | Active Sources |
|:------|:-------|:------|:---------------|
| `generic` | `lib-multisrc:generic` | `GenericThemeSource` | `nekopoi` (`id`), `hanime` (`en`), `hanime1` (`zh`) |
| `dooplay` | `lib-multisrc:dooplay` | `DooPlayTheme` | `hentaimama` (`en`) |
| `kvs` | `lib-multisrc:kvs` | `KvsTheme` | `rule34video` (`en`) |
| `retrotube` | `lib-multisrc:retrotube` | `RetroTubeTheme` | `3xchina` (`zh`) |

> [!NOTE]
> Decommissioned/Retired Sources: `NontonHentai` (`id`, `lib-multisrc:animestream`), `RuleVid` (`en`, `lib-multisrc:kvs`), `IndoHentai` (`id`), `JAVSubbed` (`en`). Permanent 64-bit IDs locked in `docs/SOURCE_IDS.md`.

---

## 5. Independent Version Bumping

Kototoro and Aniyomi require monotonic update detection (`remote.versionCode > installed.versionCode`).
- **Rule**: Only increment `extVersionCode` and `extVersion` in `build.gradle.kts` for extensions that have code changes or bug fixes.
- Unmodified extensions remain at their existing versions to prevent false update prompts in host UIs.

---

## 6. Verification Suite & Runbook

Execute before every commit:

```bash
# 1. Full Gradle Check (Unit Tests, Instantiation, Stream Hygiene, APK Assembly)
./gradlew check

# 2. Kototoro Local Self-Test (7-Step Harness Simulation)
./gradlew kototoroSelfTest

# 3. Host Class Exclusion (Zero Host Classes Packaged)
python tools/verify_no_host_classes.py

# 4. Host Classpath Bytecode Resolution
python tools/verify_host_api_binding.py

# 5. Stream Hygiene & Anti-Regression Verification
python tools/verify_stream_hygiene.py

# 6. Monotonic Update Simulation
python tools/test_update_simulation.py

# 7. Diagnostic Preflight & Sanitizer Validation
python tools/test_preflight.py
python tools/test_collect_diagnostics.py

# 8. Secret Scanner (Zero Leaks)
python tools/scan_secrets.py
```

---

## 7. In-App Updates & GitHub CDN Caching

- Client updates are served via GitHub Pages raw repository metadata: `https://raw.githubusercontent.com/kenewjr/kaziyami/repo/index.min.json`.
- **CDN Caching Delay**: `raw.githubusercontent.com` caches repository metadata for ~3–5 minutes after a release commit.
- **User Instruction**: In Kototoro, navigate to **Settings -> Extension management -> Repositories**, wait 3–5 minutes after release, and pull-to-refresh to pull the latest index.
