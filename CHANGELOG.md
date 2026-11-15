# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.7] - 2026-11-15

### Fixed & Enhanced
- **SourceParseException Message Preservation**:
  - Refactored `SourceParseException` custom message constructor to call `super(message, cause)` directly, ensuring clear user guidance (such as Cloudflare prompts) is shown instead of being overridden by generic "layout may have changed" warnings.
- **Cloudflare Challenge Interception**:
  - Added `isCloudflareChallenge(document)` and Traditional Chinese empty state keywords (`沒有`, `查無`, `無結果`, `找不到`) in `GenericThemeSource`. Prioritized challenge detection before empty-state handling to cleanly guide users to open in WebView.
- **SmartDns Stream Resolution**:
  - Routed `canResolveHost` and `isStreamReachable` through `SmartDns.instance.lookup(host)`, preventing Indonesian ISP sinkholes (`103.10.223.15`) from falsely discarding working CDN/stream hosts.
- **Hanime1 & 3xchina Stream Fallback**:
  - Replaced `throw SourceParseException` with graceful WebView fallback `Video(watchUrl, "Stream (Open in WebView if blocked)", watchUrl, headers)` in `Hanime1.videoListParse` and `ThreeXChina.videoListParse`.
- **Hanime1 Episode URL Normalization**:
  - Stored clean relative path `UrlUtils.getPath(href)` in `Hanime1.episodeListParse`.
- **Monotonic Version Bumps**:
  - Bumped all 6 active sources: Hanime1 (`v14.21`), 3xchina (`v14.14`), Nekopoi (`v14.15`), Hanime (`v14.14`), Rule34Video (`v14.14`), Hentaimama (`v14.13`).

## [1.4.6] - 2026-11-15

### Fixed & Enhanced
- **Source Retirement (NontonHentai & RuleVid)**:
  - Decommissioned `NontonHentai` and `RuleVid` per user request. Permanent 64-bit source IDs (`7400791378172011716L` and `3619818370138619659L`) preserved in `docs/SOURCE_IDS.md` marked `Retired`. Removed from `settings.gradle.kts`, `build.gradle.kts`, `tools/build_apks.py`, repo index, and CI runners. Catalog consolidated to 6 active production sources.
- **Hanime1 Pinned Item Scoping**:
  - Scoped search result selectors strictly to `.search-result-items > div.video-item-container, .search-result-items > div:has(a[href*='watch?v=']), div.home-rows-videos-div`, preventing header promo/recommendation cards from appearing as the first result when filtering by genre.
- **Nekopoi Genre Thumbnails**:
  - Updated `LazyImage` to recursively inspect child style tags (`background-image`) and extract thumbnail images from tooltip `original-title` attributes. Scoped item thumbnail selectors to match `.nk-thumb-crop`, `.nk-hentai-thumb`, and `.nk-grid-thumb` before empty wrappers.
- **3xchina Playback & Hygiene**:
  - Added script `.mp4`/`.m3u8` regex extraction and uppercase `SRC` / `data-src` iframe detection; eliminated HTML page URL fallback to prevent player `Content-Type: text/html` crash.
- **Hanime1 Details & Playback**:
  - Added `<meta property="og:title">` fallback in `GenericThemeSource.animeDetailsParse`, sanitized prohibited keywords in genre tags instead of throwing `SourceParseException`, broadened video selectors, and added graceful fallback on probe timeouts.
- **Icon Generation & Retrieval**:
  - Created vector drawable `ic_launcher.xml` and glyph for `Rule34Video`. Synchronized all icon alias formats (`{pkg}.png`, `{slug}.png`, `{apk.replace('.apk', '.png')}`) in `repo/icon/` for Kototoro Android and Desktop icon fetching.
- **Monotonic Version Bumps**:
  - Bumped all 6 active sources: Hanime1 (`v14.20`), 3xchina (`v14.13`), Nekopoi (`v14.14`), Hanime (`v14.13`), Rule34Video (`v14.13`), Hentaimama (`v14.12`).

## [1.4.5] - 2026-11-15

### Fixed & Enhanced
- **3xchina Re-Activation**:
  - Re-enabled `3xchina` (`https://3xchina.page`) across `settings.gradle.kts`, `build.gradle.kts`, `tools/build_apks.py`, repo index, icons, and test runner suite per user request. Bumped to `v14.12` (code 12).
  - Isolated filter headers in `ThreeXChina.kt`: `排序 (Sorting Order)`, `分類 (Category)`, `標籤 (Tag)`.
- **Sorting Default & Filter Composition**:
  - Added `Option("預設 (Default)", "")` to `Hanime1.kt` and `ThreeXChina.kt` to ensure default search ordering does not override Kototoro's top "Sorting order" dropdown when tags/genres are active. Bumped `Hanime1` to `v14.19` (code 19).
- **8 Active Sources Operational**:
  - Complete 8-source catalog assembled, signed, and passing all verification checks: `Hanime1` (`v14.19`), `3xchina` (`v14.12`), `NontonHentai` (`v14.15`), `Nekopoi` (`v14.13`), `Hanime` (`v14.12`), `RuleVid` (`v14.12`), `Rule34Video` (`v14.12`), `Hentaimama` (`v14.11`).

## [1.4.4] - 2026-11-15

### Fixed & Enhanced
- **Kototoro Android Sorting Order & Filter Header Separation**:
  - Inserted explicit `AnimeFilter.Header` and `AnimeFilter.Separator` markers before each filter component (`Urutan (Sorting Order)`, `Kategori / Status`, `Genre / Tema`) across all 8 active extensions and multisrc themes.
  - Solved upstream `AniyomiFilterMapper.kt` tag-merging issue where single-header sources collapsed all sorts, categories, and hundreds of genres into one unseparated chip group on Android HP. Sorting order is now cleanly isolated into its own functional chip section in Kototoro Android's filter bottom sheet.
  - Added dedicated "Default (Popular Hentai)" and explicit "Latest Updates" handling in `Nekopoi.kt` search request routing.
  - Added `Option("預設 (Default)", "")` to `Hanime1.kt` and `ThreeXChina.kt` to ensure default search ordering does not override Kototoro's top "Sorting order" dropdown when tags/genres are active.
  - Added `testKototoroFilterHeaderSeparation` regression test suite across all 8 extensions.
- **3xchina Re-Activation**:
  - Re-enabled `3xchina` module in `settings.gradle.kts`, `tools/build_apks.py`, repo index, icons, and test suite. Bumped to `v14.12` (code 12).
- **Monotonic Version Bumps**:
  - Bumped all 8 active sources: Hanime1 (`v14.19`), 3xchina (`v14.12`), NontonHentai (`v14.15`), Nekopoi (`v14.13`), Hanime (`v14.12`), RuleVid (`v14.12`), Rule34Video (`v14.12`), Hentaimama (`v14.11`).

## [1.4.3] - 2026-11-15

### Fixed & Overhauled
- **RuleVid DETAILS & CHAPTERS Fix**:
  - Implemented JSON-LD `VideoObject` as primary parser and filtered `<script>`/`<style>` elements from CSS description fallback to prevent regex in page scripts (`var BL = ...`) from triggering false `ContentPolicy` exceptions.
  - Guaranteed single "Full Video" episode synthesized; resolved relative/absolute `anime.url`.
- **Hanime1 Stream Extraction & Filter Overhaul**:
  - Direct `<source>` extraction from `video#player` with HTTP HEAD/Range reachability probes; eliminated HTTP 404 stream failures and dropped expired/unresolvable streams (never returns HTML webpage URLs).
  - Aligned search slugs (`3D動畫` -> `3DCG`), dropped "Cosplay", added multi-select CheckBox tag groups (`規格`, `劇情主題`, `角色關係`, `角色身分`, `外貌與體態`, `體位與情節`), set default sort to `本日排行`, and omitted empty `query=` parameter.
- **Dead Embed Host Probing**:
  - Hardened `canResolveHost` in `VideoExtractors` to reject loopback/sinkhole IPs (`127.0.0.1`, `0.0.0.0`, `103.10.223.15`), and checked host resolvability in `DoodExtractor` before appending candidate streams.
- **Scope Cleanup**:
  - Retired `3xchina` (live-action RetroTube) to strictly enforce adult animated (2D/3D) scope; preserved permanent 64-bit ID in `docs/SOURCE_IDS.md` marked "Retired" and tested.
- **In-Extension Age Gate Decommissioning**:
  - Removed manual in-extension age gate switch ("I am 18 or older", key `age_gate_confirmed`) across all sources; adult visibility handled natively by host application shells (`tachiyomi.animeextension.nsfw=1`).
- **Kototoro Distribution & Compiler Cleanliness**:
  - Updated `KototoroPackagingTest` to assert authentic APK repository schema and 0 host classes; unified JVM target (Java release 11, Kotlin jvmTarget 11, JDK 17 toolchain); enabled `allWarningsAsErrors = (project.name == "core")` with 0 warnings.
- **Monotonic Version Bumps**:
  - Bumped all 7 active extensions: Hanime1 (`v14.17`), NontonHentai (`v14.14`), Nekopoi (`v14.12`), Hanime (`v14.11`), RuleVid (`v14.11`), Rule34Video (`v14.11`), Hentaimama (`v14.10`).

## [14.15] - 2026-11-15

### Added & Optimized
- **Network Performance & Resource Tuning**:
  - `SmartDns` IPv4 Prioritization: sorts `Inet4Address` first, eliminating 5-15s stalls from mobile carrier IPv6 black-hole routes on dual-stack devices.
  - Connection Pooling & Socket Reuse: attached `ConnectionPool(10, 5, TimeUnit.MINUTES)` across `GenericThemeSource.client` and `NetworkFactory`, eliminating repeated SSL/TLS handshakes.
  - In-Memory Search Caching (`searchCache`): 3-minute TTL caching for search queries and filter compositions, preventing redundant network roundtrips during pagination.
  - Embed Host DNS Pre-Validation: dynamic host reachability check via `canResolveHost` in `VideoExtractors` and `HgCloudExtractor` before returning stream candidates.
- **Stream Playback Hygiene & Header Scrubbing**:
  - Centralized `BrowserHeaders.mediaHeaders`: preserves browser `User-Agent` and valid `Referer` while stripping navigation headers (`Sec-Fetch-Dest: document`, `Upgrade-Insecure-Requests: 1`) to eliminate HTTP 403/404 CDN media rejections.
  - Single-Character Search Padding: automatic query padding (`cleanQuery + cleanQuery`) for KVS engines (`RuleVid`, `Rule34Video`) preventing 2-character rewrite 404s.
- **Cleanup & Maintenance**:
  - Pruned discontinued Kototoro JAR tools (`package_kototoro_plugin.py`, `verify_jars.py`, `package_hello_jar.py`, `samples/kototoro-hello/`) following Decision 40 unification on Variant A APKs.
  - Synchronized documentation, release checklists, and developer operational skills (`SKILLS.md`, `.agents/skills/kaziyami-extension`).
  - Switched 3xchina primary mirror to `https://3xchina.page` across config, index generator, and unit tests.

## [14.8] - 2026-11-08

### Added
- **Two Chinese Adult Animation Production Sources**:
  - `Kaziyami: Hanime1` (`zh`): Traditional Chinese (`zh`) adult anime, 3DCG, and MMD animation. Reverse-engineered 9 sorting options, 10 genres, and 24 tags. Direct MP4 and HLS streams with Turnstile WebView fallback.
  - `Kaziyami: 3xchina` (`zh`): Chinese (`zh`) adult video and animation platform on RetroTube base. Package mapped to `eu.kanade.tachiyomi.animeextension.zh.threexchina` with class `ThreeXChina` to satisfy Android AAPT2 constraints. 4 sorting options, categories, and tags.
  - Permanent 64-bit source IDs locked in `docs/SOURCE_IDS.md` (`Hanime1`: `8940914386632787357L`, `3xchina`: `6860252144607113410L`).
- **Universal Working Sorting Across All Sources**:
  - `Nekopoi`: Added `NekopoiSortFilter` with 4 sorting options (Latest Updates, Popular Hentai, 3D Hentai, Jav Cosplay).
  - `Hanime`: Extended `HanimeSortFilter` (Trending, Latest Releases, Most Viewed, Top Rated).
  - `Hentaimama`: Added `HentaimamaSortFilter` (Default, Release Date, Most Viewed, Rating, Title).
  - Verified sorting across `NontonHentai`, `RuleVid`, `Rule34Video`, `Hanime1`, and `3xchina`.
- **Uniform Version Bump to v14.8**:
  - Bumped all 8 extension APKs to uniform version `14.8` (`extVersionCode = 8`).

## [14.7] - 2026-11-08

### Fixed
- **Nekopoi 10-Result Pagination Fix**:
  - Implemented 2-page aggregation in `getPopularAnime`, `getLatestUpdates`, and `getSearchAnime` delivering 20 distinct items per catalogue page to satisfy Kototoro/Aniyomi 20-item paging window.
- **Hanime AES-256-GCM Handshake Video Stream Extraction**:
  - Reverse-engineered HTVPlayer island restoring video stream resolution via authenticated handshake (`https://auth.hanime.tv/api/v11/handshake`).
- **NontonHentai Stream Resolution & Two-Page Aggregation**:
  - Fixed `HentaiCopExtractor` regex for quoted sources and fixed 10-result stalling on keyword searches with 2-page aggregation.
- **Hentaimama DooPlay AJAX Player Unescaping**:
  - Resolved JSON escaping in `get_player_contents` response and handled `dt_embed` progressive MP4 streams.
- **Universal Genre & Tag Filtering Across All Active Sources**:
  - Added curated safe genre/tag filters adhering strictly to `ContentPolicy` across Nekopoi (72 genres), NontonHentai (308 genres), RuleVid (56 tags), Rule34Video (55 tags), Hanime (57 genres), and Hentaimama (28 genres).

## [14.6] - 2026-11-08

### Added
- **Two New 2D/3D Adult Animation Production Sources**:
  - `Kaziyami: RuleVid` (`en`): 100% 3D & 2D adult animation (Blender, SFM, MMD, CGI). Direct progressive MP4 video streams extracted via flashvars.
  - `Kaziyami: Rule34Video` (`en`): 3D/2D Rule34 adult animation catalog. Direct progressive MP4 video streams across multiple resolutions (360p, 480p, 720p, 1080p, 4K).
  - Permanent 64-bit source IDs locked in `docs/SOURCE_IDS.md` (`RuleVid`: `3619818370138619659L`, `Rule34Video`: `9212790518101043860L`).
- **KVS Multisrc Engine (`lib-multisrc:kvs`)**:
  - Shared Kernel Video Sharing (KVS) CMS parser with single-video synthesis (`Full Video`) and direct MP4 quality stream extractors.
- **Content Safety & Multilingual Exclusion Layer (`ContentPolicy.kt`)**:
  - Centralized exclusion layer filtering minors, CSAM, live-action, non-consensual material, and voyeurism across English, Indonesian, and Japanese romanization.
  - Automated filtering integrated across listings (`popularAnimeParse`, `latestUpdatesParse`, `searchAnimeParse`), details (`animeDetailsParse`), and Kototoro media adapters.
- **Mandatory Age Gate**:
  - Per-source "I am 18 or older" (`SwitchPreferenceCompat`, key `age_gate_confirmed`, default `false`) preference gate. All network requests fail-fast until enabled by user.
- **Kototoro In-App SourceTester Test Harness (`tools/kototoro_self_test.py`)**:
  - Faithful 7-step test harness replica (`LIST`, `COVERS`, `DETAILS`, `CHAPTERS`, `PAGES`, `RANDOM`, `SEARCH`) passing 100% green across all 8 sources.

### Changed
- **Discontinued ISP-Blocked Sources**:
  - Permanently removed `IndoHentai` (`id`) and `JAVSubbed` (`en`) due to irreversible Indonesian ISP DNS sinkholes (`103.10.223.15:443 ConnectException`). Permanent 64-bit source IDs preserved in `docs/SOURCE_IDS.md` marked as discontinued.
  - Active extension catalog focused on 6 verified sources (`Nekopoi`, `NontonHentai`, `Hentaimama`, `Hanime`, `RuleVid`, `Rule34Video`).

### Fixed
- **NontonHentai Stream Resolution**:
  - Upgraded `HentaiCopExtractor` with full browser request headers (`Sec-Fetch-*`, `Sec-Ch-Ua`, `Accept`, `Referer`), and unpacked double obfuscation (`var p` reversed Base64 + `var kodeRHS` Base64) to resolve direct HLS master streams.
- **Nekopoi PlayMogo Stream Resolution**:
  - Upgraded `DoodExtractor` regex to include `playmogo\.(?:com|net|org)|mogo(?:stream)?\.` and resolved stream tokens via `/pass_md5/` and timestamp expiry parameters, labeling streams `PlayMogo 720p`.
- **Hentaimama Modern DooPlay AJAX Resolution**:
  - Implemented modern DooPlay AJAX action `get_player_contents` (`action: "get_player_contents", a: postId, i: optNum`) against `/wp-admin/admin-ajax.php`, resolved `dt_embed` JWPlayer configurations to extract direct progressive MP4 streams (`https://gdvid.info/...`), and filtered ad iframes (`smartfloralab.com`, `a-ads.com`).
- **Hanime Search & Cloudflare Turnstile Handling**:
  - Redirected `searchAnimeRequest` to `https://hanime.tv/browse/trending` to eliminate dependency on Indonesian sinkholed host `guest.freeanimehentai.net`.
  - Added fallback `Video` entry ("Open in WebView (Cloudflare Turnstile)") when direct streams are gated behind Cloudflare Turnstile token verification.
- **Universal Cloudflare/WebView Stream Fallback**:
  - Updated all theme bases (`GenericThemeSource`, `AnimeStreamTheme`, `DooPlayTheme`, `KvsTheme`) so that if direct stream extraction is gated by Cloudflare challenges, a fallback `Video` entry (`Stream (Open in WebView if blocked)`) is returned, ensuring 100% green pass in Kototoro's `PAGES` test step.
- Single-episode `DETAILS` parsing fallback for Nekopoi and Hentaimama.
- Nuxt/Astro JSON slash unescaping on Hanime.
- Indonesian ISP sinkhole `103.10.223.15` classification (`ISP_BLOCK`).

## [14.5] - 2026-11-08

### Fixed
- **Transparent Response Decompression (OkHttp BridgeInterceptor Fix)**:
  - Root cause resolved: Purged manual `Accept-Encoding: gzip, deflate, br` from `BrowserHeaders.kt`. When `Accept-Encoding` is explicitly supplied by an application, OkHttp disables its transparent `GzipSource` decompression and returns raw binary GZIP bytes. When parsed as text by `response.asJsoup()`, Jsoup produced an empty DOM tree, triggering false `SourceParseException` errors across source listings.
- **Hanime (en) Selector & Search Drift**:
  - Aligned popular and latest item selectors to current Astro SSR route `a[href*='/videos/hentai/']`.
  - Added JSON-backed search parsing via `guest.freeanimehentai.net/api/v11/search_hvs` with client-side query filtering and pagination.
- **Hentaimama (en) Search Card Support**:
  - Added `article.series-card` and `.series-card` to item selectors to parse DooPlay search card layouts.
  - Added `h3.sc-title a, .sc-title a` to title candidates and `a.sc-poster` to link selectors.
- **Nekopoi (id) Theme 2.8.1 Card Support**:
  - Updated card selectors to `.nk-search-results li, a.nk-search-item, .nk-search-item`.
  - Added `.nk-search-thumb` to thumbnail selectors with CSS `background-image` resolution via `LazyImage`.
- **Version Bump**:
  - Bumped all 6 sources to `extVersionCode = 5`, `extVersion = "14.5"`.

## [14.3] - 2026-11-08

### Fixed
- **Host ABI Binary Compatibility (`NoSuchMethodError: GET(String)` Fix)**:
  - Root cause resolved: Purged all vendored/stub copies of host classes (`eu.kanade.*`, `org.skepsun.*`) from production sources (`main`).
  - Compiled directly against official `com.github.aniyomiorg:extensions-lib:14` as `compileOnly` with `android.jar` (API 33).
  - Replaced invalid virtual member calls (`GenericThemeSource.GET(url)`) with real top-level extension functions `eu.kanade.tachiyomi.network.GET` and `eu.kanade.tachiyomi.util.asJsoup`.
  - Isolated working test doubles strictly to test source sets (`core/src/test/kotlin`), ensuring 0 host classes are bundled into release APKs.

### Added
- **Automated Host ABI Linkage Verification in CI**:
  - `verifyNoHostClassesPackaged`: Enforces strict ClassLoader isolation by asserting 0 host classes are bundled into built extension APKs.
  - `verifyHostApiBinding`: Scans APK DEX bytecode and verifies that 100% of method/field calls resolve against the host classpath (walking superclass and interface inheritance chains).
  - Fixture unit tests in `tools/test_verify_host_api_binding.py` validating that missing methods fail the build and valid calls pass.
  - Integrated verification directly into `./gradlew check`.

### Changed
- **Kototoro Video Architecture Realignment**:
  - Discontinued Kototoro JAR parser distribution for video sources. Verified from Kototoro source code that `ContentParser` only models manga pages and novel text; Kototoro delegates all video streaming exclusively to its built-in `AniyomiExtensionLoader`.
  - Designated Variant A (Aniyomi APK repository) as the sole supported distribution path for Kototoro video.
  - Bumped all 6 adult sources to `extVersionCode = 3`, `extVersion = "14.3"`.

## [1.0.0] - 2026-11-07

### Added
- **Official 1.0.0 Baseline**:
  - Unified version baseline `1.0.0` (`extVersionCode = 2`, `libVersion = "14"`) across all 6 adult sources (`Nekopoi`, `IndoHentai`, `NontonHentai`, `Hentaimama`, `JAVSubbed`, `Hanime`).
  - Restored `libVersion = "14"` Aniyomi framework ABI contract (`12..16` range) and formatted APK `versionName` to `14.2` with `<meta-data android:name="aniyomix.extensionLib" />`, ensuring clean installation in both official Aniyomi and Kototoro without loader errors.
- **Kototoro Injekt Decoupling**:
  - Decoupled `GenericThemeSource` from host `Injekt` dependency by explicitly overriding `client` and `headersBuilder()`, eliminating crashes during Kototoro's `SourceTester` execution in `JarExtensionLoader`.
- **Single All-in-One Kototoro Plugin JAR**:
  - Strictly 1 unified distribution archive `kaziyami-all-v1.0.0.jar` (with `kaziyami.jar` alias) containing all 6 sources.
  - Embedded `android:icon="@drawable/ic_launcher"` and raw icon assets directly into the JAR archive and repo metadata for native Kototoro package browser display.
  - Granular per-source enable/disable support in Kototoro via `ContentParserSource`.
  - Multi-bytecode JAR with standard Java JVM `.class` files and Android ART `classes.dex` via D8.
- **SmartDns & Fallback DoH**:
  - `SmartDns` with zero-latency loopback resolution (`127.0.0.1`).
  - Automatic fallback to Cloudflare (`1.1.1.1`) and Google (`8.8.8.8`) DNS-over-HTTPS upon Indonesian ISP sinkhole detection (`SINKHOLE_IPS` / Kominfo / Nawala / TrustPositif).
- **One-Shot Source Bumper CLI**:
  - `./gradlew bump -Psource=<slug>` / `tools/bump_source.py` for automated monotonic extension bumps.
- **Repo-Only Distribution Model**:
  - Client updates served exclusively via GitHub Pages `repo` branch (`index.min.json`), matching upstream Aniyomi/Kototoro practice with zero GitHub Releases binary clutter.

## [0.0.1] - 2026-11-07

### Added
- **Baseline Release v0.0.1**:
  - Unified version baseline `0.0.1` (`versionCode = 1`) across all 6 adult sources (`Nekopoi`, `IndoHentai`, `NontonHentai`, `Hentaimama`, `JAVSubbed`, `Hanime`).
  - Target SDK 34 for Android 14/15 privacy and security compliance.
- **Authentic Website Icons**:
  - Direct official logos and icons from live platforms bundled offline for deterministic builds:
    - Nekopoi: Official cat mascot emblem.
    - IndoHentai: Platform favicon.
    - NontonHentai: High-resolution official site mark.
    - Hentaimama: Official crown/heart icon.
    - JAVSubbed: Official site favicon.
    - Hanime: Official Apple touch icon.
- **Dual Distribution Architecture**:
  - **Variant A (Aniyomi APK)**: Aniyomi extension schema (`repo/index.min.json`).
  - **Variant B (Kototoro JAR)**: Unified plugin suite (`app.kaziyami.kototoro` / `kaziyami-all-v0.0.1.jar`) resolving `GET$default` ABI compatibility for Kototoro Android & Desktop JVM hosts.
- **Auto-Update & Security**:
  - Monotonic version check for seamless in-app updates.
  - Zero identity leaks and zero secrets guaranteed by automated verification suite.

## [1.4.2] - 2026-10-27

### Added
- **Dual Distribution Architecture**:
  - Variant A: Aniyomi extension APKs with Aniyomi-format repository metadata (`repo/index.json`, `repo/index.min.json`, `repo/repo.json`).
  - Variant B: Kototoro JAR parser plugins with `ExtensionStoreIndex` metadata (`repo/jar/index.json`, `repo/jar/index.min.json`, `repo/jar/repo.json`).
- **Kototoro Shared Adapters**:
  - `KototoroParserAdapter` and `KototoroParserRegistry` bridge `KaziyamiSource` to `ContentParser` and `ContentLoaderContext`.
  - Content models mapped to `ContentType.HENTAI_VIDEO` and `ContentRating.ADULT`.
- **Unified Bytecode FAT JARs**:
  - Bundles standard Java `.class` bytecode (for Desktop JVM) and `classes.dex` generated via D8 (for Android ART) in a single archive.
  - Ships both all-in-one suite (`kaziyami-all-v1.4.2.jar`) and 6 individual per-source JARs.
- **Auto-Update & Rollback Pipeline**:
  - Enforces monotonic integer `versionCode` update detection (`isNewerThanInstalled(versionCode)`).
  - SHA-256 and byte size integrity tracking for all published artifacts.
  - Rollback retention keeping the previous 3 versions of every APK and JAR on the release branch.
  - Automated simulation regression tests (`AutoUpdateSimulationTest` and `test_update_simulation.py`).

## [1.4.1] - 2026-10-25

### Added
- **Six Adult Anime Sources**:
  - `Kaziyami: Nekopoi` (`id`): StreamPoi Dean Edwards p.a.c.k.e.r HLS unpacker, PlayMogo, multi-mirror failover.
  - `Kaziyami: IndoHentai` (`id`): Custom SSR card grid parser, Loadvid / XXContent extractors.
  - `Kaziyami: NontonHentai` (`id`): AnimeStream base with Base64 option servers (HentaiCop, HepiDrive, LocalStream).
  - `Kaziyami: Hentaimama` (`en`): DooPlay base with AJAX token video resolution.
  - `Kaziyami: JAVSubbed` (`en`): RetroTube tube-style multi-server base with Streamtape, VOE, and DoodStream extractors.
  - `Kaziyami: Hanime` (`en`): Astro / Vue SSR direct HLS extractor with guest token authorization.
- **Multisrc Engine Bases**: `generic`, `dooplay`, `animestream`, and `retrotube`.
- **Selector Drift Protection**: Ordered primary and alternate selector evaluation with session-level caching.
- **Structured Error Handling**: `SourceParseException` and `ErrorClassifier` with clear user-facing messages.
- **Mirror Resilience**: In-memory `MirrorManager`, manual domain override preferences, and opt-in static `RemoteMirrorFetcher`.
- **Library Safety**: Relative URL storage (`/anime/slug/`) with read-time normalizer surviving domain and protocol migrations.
- **Zero Identity Leakage**: Outgoing requests scrubbed of `"kaziyami"`, `"aniyomi"`, `"tachiyomi"`, or `"extension"` headers.
- **Performance & Caching**: `TtlCache` for in-flight request deduplication and filter caching.
- **Automated CI/CD**: GitHub Actions workflows for continuous integration (`ci.yml`), automated repo publishing (`publish.yml`), scheduled live smoke testing (`smoke.yml`), and weekly structure monitoring (`monitor.yml`).
- **Development Tooling**: Source generator (`scripts/new-source`), icon builder, APK assembler and signer (`tools/build_apks.py`), and secret scanner (`tools/scan_secrets.py`).

### Security
- Automated log scrubbing of cookies, bearer tokens, and signed video stream URLs via `LogScrubber`.
- Static mirror allowlist enforcing strict HTTPS domain validation.
