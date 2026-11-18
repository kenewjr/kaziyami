# Kaziyami Agent Guidelines & Operational Invariants

Mandatory instruction document for AI assistants and automated coding agents modifying the Kaziyami codebase. Every agent MUST read and adhere strictly to these rules to ensure that iterations and improvisations do not cause regressions, break contracts, or drop existing features.

---

## 1. Zero Feature Regression Rules

1. **Preserve Existing Capabilities**:
   - Never remove alternate selectors, fallback stream extractors, or caching mechanisms without explicit instructions.
   - All improvements and modifications must be additive and bolster resilience.
2. **Permanent Source ID Lock (`docs/SOURCE_IDS.md`)**:
   - The 64-bit hash IDs of all 6 active sources (`Nekopoi`, `Hanime1`, `3xchina`, `Hanime`, `Rule34Video`, `Hentaimama`) and 4 retired/discontinued sources (`NontonHentai`, `RuleVid`, `IndoHentai`, `JAVSubbed`) are permanent.
   - The internal source name `AnimeSource.name` MUST remain the raw site name (e.g., `Nekopoi`, `3xchina`, `Hanime1`), NEVER prefixed with `Kaziyami:`.
   - Modifying source IDs corrupts bookmarks and watch history in the host application database.
3. **Display Label Naming Conventions**:
   - Display labels in host UIs (`android:label` in the manifest and `name` in `index.min.json`): `Kaziyami: <Site>` (e.g., `Kaziyami: Nekopoi`, `Kaziyami: Hanime1`, `Kaziyami: 3xchina`).
4. **Android Package & Class Naming Invariants**:
   - Android package identifiers cannot begin with numbers (`3xchina` uses package `eu.kanade.tachiyomi.animeextension.zh.threexchina` and class `ThreeXChina`, while keeping raw source name `"3xchina"`).
5. **Universal Sorting, Language & Filtering Integrity**:
   - Every source must implement authentic, working sort filters and curated safe genre/category/tag filters adhering strictly to `ContentPolicy` (zero tolerance for minors, voyeurism, non-consensual content).
   - In unit tests, avoid reflective manipulation of private fields; use `source.preferences.edit()` directly on `GenericThemeSource.preferences`.

---

## 2. Host Compatibility Invariants (Aniyomi & Kototoro)

This repository produces and distributes video extensions targeting Aniyomi and Kototoro:

### Variant A: Aniyomi Extension (APK)
- **Root Package**: Must maintain the `eu.kanade.tachiyomi.animesource.*` and `eu.kanade.tachiyomi.animeextension.*` namespaces.
- **Manifest**: Must include metadata `tachiyomi.animeextension.class`, `tachiyomi.animeextension.nsfw=1`, and `aniyomix.extensionLib="14"`.
- **Repository Index**: Aniyomi extension array schema at `repo/index.min.json`.
- **Compile-Only Host ABI & Zero Vendored Classes**: Extensions compile against official `extensions-lib:14` as `compileOnly`. Host packages (`eu.kanade.*`, `okhttp3.*`, `okio.*`, `kotlin.*`, `kotlinx.*`, `uy.kohesive.injekt.*`, `androidx.*`) MUST NEVER be bundled inside extension APKs (`verifyNoHostClassesPackaged`).
- **Test Doubles Isolation**: Working test doubles for host interfaces exist strictly in test source sets (`core/src/test/kotlin`), never in `main`.

### Variant B: Kototoro JAR Discontinuation
- Kototoro's native JAR parser interface (`ContentParser`) only models static manga page images and novel text. Kototoro delegates all video playback to its built-in `AniyomiExtensionLoader` (Variant A APK). JAR parser plugins cannot support video (see `docs/KOTOTORO_JAR_FORMAT.md`).

---

## 3. Network Resilience & Anti-Bot Constraints

1. **Clean & Anonymous Request Headers**:
   - Never inject custom identifying headers (such as `User-Agent: Kaziyami` or `X-Extension-Version`).
   - Maintain `User-Agent` and Client Hints consistency centrally via `BrowserProfile`.
2. **Rate Limiting & Anti-Burst**:
   - Always use `TokenBucketRateLimiter` (maximum 3 req/s per host) with a concurrency semaphore (cap 1-2 per host) and micro-jitter.
3. **Sequential Stream Extraction**:
   - Resolve embed streams sequentially (prioritizing user's preferred host). Terminate immediately on the first successful playable stream; do not hammer embed hosts in parallel.
4. **Security & Legal Boundaries**:
   - Never integrate or bypass third-party paid/automated captcha-solving services.
   - On Cloudflare challenges or ISP sinkholes, route users cleanly via `SourceParseException` ("Open in WebView" or switch mirror domain).

---

## 4. Mandatory Runbook Per Prompt

Before modifying code, follow this sequence:

1. **Check Ground Truth**:
   - Read [docs/DECISIONS.md](file:///e:/project/kaziyami/docs/DECISIONS.md) to understand existing architectural constraints.
   - Review [docs/PROGRESS.md](file:///e:/project/kaziyami/docs/PROGRESS.md) for current implementation status.
2. **Minimal Diff (Lazy Senior Dev Principle)**:
   - Implement the smallest viable change. Avoid unnecessary abstractions, boilerplate, or unrequested dependencies.
   - Do not touch files unrelated to the user's prompt.
3. **Execute Verification Suite (Must Be 100% Green)**:
   Run the full verification suite in the terminal:
   ```bash
   # 1. Unit & Contract Tests + Host API Binding Checks + APK Verification
   ./gradlew check

   # 2. Host Class Exclusion & Bytecode Linkage Verification
   python tools/verify_no_host_classes.py
   python tools/verify_host_api_binding.py

   # 3. Monotonic Auto-Update Simulation
   python tools/test_update_simulation.py

   # 4. Preflight & Diagnostic Sanitization
   python tools/test_preflight.py
   python tools/test_collect_diagnostics.py

   # 5. Secret Scanner (Zero Leaks Required)
   python tools/scan_secrets.py
   ```
4. **Document Decisions**:
   - Record architectural choices or tradeoffs in `docs/DECISIONS.md` using the format:
     `[Decision]: [Reason]. Alternative rejected: [Alternative].`
   - Update task items in `docs/PROGRESS.md`.
5. **Zero Secret Leaks**:
   - Never stage or commit `.keystore`, `.jks`, `.properties` containing credentials, or AI session logs.

---

## 5. Lessons Learned & Anti-Regression Invariants (Kototoro & Live Source Tests)

1. **Kototoro 7-Step In-App Verification Suite (`SourceTester`)**:
   - Kototoro executes 7 sequential steps: `LIST`, `COVERS`, `DETAILS`, `CHAPTERS`, `PAGES`, `RANDOM`, `SEARCH`.
   - **Timeout Constraints**: Maximum 20 seconds per step, 90 seconds cumulative per source.
   - **Step 5 (`PAGES`) Stream Verification**: Kototoro peeks 4KB from `video.videoUrl` and strictly checks `looksLikeVideoStream(contentType, head)` (`video/*`, `application/*mpegurl`, `#EXTM3U`, `ftyp`).
   - **NEVER Return Webpage/HTML URLs as Video Streams**: Returning HTML embed page URLs or empty video lists triggers `Stream failed: Content-Type: text/html`.
2. **Video Playback Header Hygiene**:
   - Video headers (`Video.headers`) MUST include browser `User-Agent` and valid `Referer` (missing UA causes HTTP 403 on video CDNs such as RuleVid).
   - Video headers MUST NEVER include document navigation headers (`Sec-Fetch-Dest: document`, `Upgrade-Insecure-Requests: 1`), which causes video CDNs to reject media streams with HTTP 404 (as observed on Hanime1).
3. **Thumbnail Parsing Beyond `<img>` Elements**:
   - Never assume thumbnails exist solely in `<img>` tags. Themes like Nekopoi place cover images in inline CSS `style="background-image: url('...')"` on `.nk-thumb-crop`. If `thumbnail_url` is empty, Kototoro `COVERS` times out (20s).
   - Details thumbnails must always include fallback candidates like `meta[property='og:image']`.
4. **DNS Pre-Validation on Dynamic & Ephemeral Embed Hosts**:
   - Embed hosts (e.g., `hentaicop.com` on NontonHentai) often unpack temporary, expired, or blocked domains (such as `9385b44eef82e4b1ce998.site`).
   - Video extractors must pre-validate host resolvability (`InetAddress.getByName(host)`) before appending extracted stream candidates to avoid unresolvable host exceptions in players.
5. **Search Query Padding & Single-Page Latency Budget**:
   - Kototoro's search test queries single characters (`"a"`). Sites with URL rewrites requiring minimum query lengths (RuleVid requires >= 2 chars) return HTTP 404; pad single-character queries (`cleanQuery + cleanQuery`).
   - High-latency backends (WordPress queries taking 10-17s) exceed Kototoro's 20s step timeout if two pages are fetched sequentially. In `getSearchAnime` and `fetchSearchAnime`, return page 1 immediately when `page1.animes.isNotEmpty()`.
6. **Independent Version Bumping**:
   - Only increment `extVersionCode` and `extVersion` for extensions that have actual code changes or bug fixes. Unmodified extensions must remain at their existing versions to prevent false update prompts in host UIs.
7. **Static Sidebar Widget vs Chronological Feed Isolation (Nekopoi)**:
   - WordPress/tube themes often feature a static 5-item sidebar or popular widget (e.g., `.nk-hentai-grid`) on every page.
   - Never include static sidebar widgets in `selectors.item`. Matching sidebar items causes page 1 and page 2 to return identical URLs, leading Kototoro deduplication to see 0 new items on page 2 and immediately stop infinite pagination ("No more pages"). Always prioritize genuine post cards (`.nk-post-card`).
8. **Recommendation / Next Watch Scroll vs Episode Isolation (Hanime1)**:
   - Standalone single-video portals (such as `hanime1.me`) place horizontal recommendation scrolls (`#playlist-scroll`, `.video-playlist-wrapper`) below the player.
   - Never parse recommendation/related video containers as episodes or playlists. Doing so populates the episode list with 20-35 unrelated video titles with view/like ratio strings (`03:17 thumb_up 99% 33.5萬次`), causing players to launch random recommended videos instead of the user's requested video. Synthesize strictly 1 `SEpisode` for the current `/watch?v=...` URL.
9. **Gateway Failover & Dean Edwards Packed HLS Extraction (3xchina / HgCloud)**:
   - Obfuscated video embed loaders (such as `hgcloud.to`, `dhcplay.com`) serve JavaScript rotators without inline media. Map video embed IDs to backend streaming mirrors (`hanerix.com`, `vibuxer.com`, etc.) and unpack Dean Edwards p.a.c.k.e.r scripts to extract `master.m3u8`.
   - Use `HlsExtractor` to expand master HLS playlists into selectable player qualities (1080p, 720p).
   - Always wrap OkHttp response execution on nested embed pages with `.use { ... }` to prevent socket and connection leaks in the pool.
10. **Multi-Filter Parameter Composition**:
    - Avoid mutually exclusive `when` branches in `searchAnimeRequest` that discard user sort preferences when category or tag filters are active. Compose query paths and parameters (`/category/$cat/?filter=$sort`).
11. **Relative Path Storage for Library Migration**:
    - Always store clean relative paths `UrlUtils.getPath(url)` for both `anime.url` and `episode.url`. Absolute URLs hardcode host domains and break user libraries when switching to mirror domains (`3xchina.net`, etc.).
12. **GitHub Raw CDN Update Delay**:
    - GitHub Pages raw CDN (`raw.githubusercontent.com`) caches index and metadata for ~3-5 minutes. Instruct users to pull-to-refresh the repository in Kototoro after this cache window expires.
