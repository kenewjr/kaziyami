# Kaziyami: Adult Anime Extension Repository

[![Kaziyami CI](https://github.com/kenewjr/kaziyami/actions/workflows/ci.yml/badge.svg)](https://github.com/kenewjr/kaziyami/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

**Kaziyami** is a curated, high-performance NSFW adult anime extension repository engineered for [Aniyomi](https://aniyomi.org/) and compatible Android video clients. Built with modular Kotlin DSL, shared multisrc themes, anti-bot browser-level networking, and fully automated repository indexing.

---

> [!CAUTION]
> ### 18+ Mandatory Disclaimer
> - **Adult Content Only (18+)**: This repository contains adult, hentai, and uncensored video extensions intended exclusively for mature audiences of legal age in their jurisdiction.
> - **No Hosting / Public Indexer Only**: Kaziyami hosts no media, streams, videos, or files on its servers. Extensions function strictly as lightweight scrapers that parse publicly accessible third-party HTML/APIs.
> - **No Affiliation & Volatility**: Kaziyami is not affiliated with any indexed websites or stream hosts. Third-party video hosts and mirror domains may change, break, or disappear without warning.
> - **Legal Compliance**: Users are solely responsible for ensuring their consumption and use complies with all applicable local, regional, and national laws.

---

## 1. Supported Applications

| Client Application | Support Status | Verification Notes |
|---|---|---|
| **Aniyomi (Stable & Preview)** | **Supported** | Full compatibility with standard `AnimeExtensionLoader` APK contract (verified on device after binary-compat fix). |
| **Kuukiyomi** | *Untested* | Unverified fork; theoretical compatibility via shared anime extension contract (see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)). |
| **Kototoro (Variant A: APK)** | **Supported** | Compatible via Kototoro's built-in `AniyomiExtensionLoader` (see [docs/KOTOTORO.md](docs/KOTOTORO.md)). |
| **Kototoro (Variant B: JAR)** | **Not Supported** | Discontinued for video. Kototoro's `ContentParser` only models manga pages and novel text; video playback routes strictly through `AniyomiExtensionLoader` APKs (see [docs/KOTOTORO_JAR_FORMAT.md](docs/KOTOTORO_JAR_FORMAT.md)). |

---

## 2. Installation in Aniyomi

To add Kaziyami directly to Aniyomi:

1. Open **Aniyomi** on your Android device.
2. Navigate to **More** -> **Settings** -> **Browse** -> **Anime extension repos**.
3. Tap **Add repository** (+).
4. Enter the raw repository URL:
   ```text
   https://raw.githubusercontent.com/kenewjr/kaziyami/repo/index.min.json
   ```
5. Return to the **Browse** -> **Extensions** tab and pull down to refresh.
6. The extensions will appear under the **Kaziyami** family label (e.g., `Kaziyami: Nekopoi`). Ensure the **18+** filter toggle is enabled in your extension list settings.

### Extension Signing & Verification

Release APKs are signed with the maintainer's dedicated release keystore. When installing or updating extensions:

1. Verify the certificate SHA-256 fingerprint displayed by Aniyomi matches your release key fingerprint.
2. To extract and verify the fingerprint of any built APK:
   ```bash
   python tools/get_apk_fingerprint.py <path_to_apk>
   ```
   See [docs/SIGNING.md](docs/SIGNING.md) for instructions on creating and configuring production release keys.

---

### Installation in Kototoro (v2.3+)

Kototoro supports Kaziyami video extensions exclusively via its built-in `AniyomiExtensionLoader` (Variant A: APK):

1. Open **Kototoro** on your Android device.
2. Tap **Explore** on the bottom navigation bar.
3. Tap **Manage** (the sliders / layers icon at the top right) to open the Sources screen.
4. Select the **Repositories** tab.
5. Tap **+** (floating action button).
6. In the repository type prompt, select **ANIYOMI**.
7. Paste the repository URL:
   ```text
   https://raw.githubusercontent.com/kenewjr/kaziyami/repo/index.min.json
   ```
8. Tap **Add** / **Confirm** and trust the repository certificate.
9. Switch to the **Aniyomi** extensions section, select any Kaziyami extension (e.g. `Kaziyami: Nekopoi`), and tap **Install**.

> [!NOTE]
> **Why is there no JAR plugin for Kototoro?**
> Kototoro's native JAR parser interface (`ContentParser`) only models static manga page images and novel text. Kototoro delegates all video playback to its built-in `AniyomiExtensionLoader`. Attempting to load video sources via JAR triggers `"manga is not supported"` or `"This video source is not supported"`. Always install Kaziyami as an Aniyomi repository in Kototoro.

> [!TIP]
> **Troubleshooting: Extensions Installed but Not Showing in Sources?**
> - **Space Kind**: Ensure Kototoro is set to the **Anime** space (or Content Type: **All** / **Hentai Video**).
> - **NSFW Filter**: Kototoro defaults to hiding 18+ sources. Tap the filter/funnel icon in **Sources** and switch **NSFW Filter** to **All** or **NSFW**.
> - **Language Filter**: All Kaziyami sources are tagged as Indonesian (`id`), English (`en`), or Chinese (`zh`). In Kototoro filter settings, ensure **Indonesian**, **English**, and **Chinese** are checked in active extension languages.
> - **Enabled State**: Newly installed extensions default to disabled in Kototoro. Scroll down to the **Disabled** section in the Sources tab and toggle them **ON**.
> - **Clear Extension Cache**: If updating from an earlier broken build, go to Android **Settings -> Apps -> Kototoro -> Storage -> Clear cache** (do not clear storage/data) and restart Kototoro. See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).

For detailed update workflows, troubleshooting, and rollback steps, see [docs/UPDATING.md](docs/UPDATING.md).

---

## 3. Catalog of Sources

All 6 active adult sources are configured with permanent 64-bit Source IDs locked in [docs/SOURCE_IDS.md](docs/SOURCE_IDS.md):

| Source Label | Language | Version | Engine / CMS | Base URL | Primary Stream Providers |
|---|---|---|---|---|---|
| **Kaziyami: Nekopoi** | Indonesian (`id`) | `v14.15` | WordPress (ThemeNekopoi) | `https://nekopoi.care` | StreamPoi HLS (1080p/720p/480p), PlayMogo 720p, Mp4Upload |
| **Kaziyami: Hentaimama** | English (`en`) | `v14.13` | WordPress (DooPlay) | `https://hentaimama.io` | Direct progressive MP4 (GDVid), DooPlay AJAX streams |
| **Kaziyami: Hanime** | English (`en`) | `v14.14` | Astro / Vue SSR | `https://hanime.tv` | Direct HLS (`master.m3u8` 1080p/720p), WebView fallback |
| **Kaziyami: Rule34Video** | English (`en`) | `v14.14` | Kernel Video Sharing (KVS) | `https://rule34video.com` | Direct CDN MP4 (360p, 480p, 720p, 1080p, 4K) |
| **Kaziyami: Hanime1** | Chinese (`zh`) | `v14.21` | Custom Vue / Hembed | `https://hanime1.me` | Direct CDN MP4 / HLS streams, WebView Turnstile fallback |
| **Kaziyami: 3xchina** | Chinese (`zh`) | `v14.14` | WordPress (RetroTube) | `https://3xchina.page` | Direct iframes / Streamtape, VOE, DoodStream, WebView fallback |

> [!NOTE]
> **Discontinued & Retired Sources**: `NontonHentai` and `RuleVid` were retired per user request; `IndoHentai` and `JAVSubbed` were discontinued due to permanent Indonesian ISP DNS sinkholes (`103.10.223.15:443`). All permanent 64-bit IDs remain permanently locked in `docs/SOURCE_IDS.md`.

---

## 4. Network & Stream Resilience

Kaziyami extensions incorporate battle-tested networking hygiene to survive hostile anti-scraping and volatile mobile networks:

- **Smart DNS with IPv4 Prioritization**: Resolves hosts via custom `SmartDns` sorting `Inet4Address` first. This eliminates 10-20s mobile IPv6 black-hole connection timeouts on carrier networks.
- **Connection Pooling & Socket Reuse**: Shared `ConnectionPool(10, 5, TimeUnit.MINUTES)` eliminates repeated TLS handshakes across catalog browsing and pagination.
- **Search Query Caching (TTL 3m)**: Caches search responses in-memory to prevent duplicate network round-trips when host apps paginate or re-query.
- **Embed Host DNS Pre-Validation**: Dynamically checks host reachability via `canResolveHost` before appending stream candidates, skipping dead or ephemeral embed host mirrors.
- **Stream Playback Hygiene**: Strips navigation headers (`Sec-Fetch-Dest: document`) and preserves browser `User-Agent` and valid `Referer` to ensure CDN media delivery (HTTP 403/404 prevention).
- **Rate-Limiting & Micro-Jitter**: Per-host token bucket limits requests to <=3 req/s with randomized delays to avoid burst triggers.

---

## 5. Frequently Asked Questions (FAQ)

### Cloudflare "Open in WebView"
- **Why do I see a challenge error?** Adult sites frequently use Cloudflare Managed Challenges or Turnstile to prevent automated scraping.
- **How to resolve**: Tap the overflow menu (⋮) on the anime details screen and select **Open in WebView**. Complete the Cloudflare verification checkbox once. Aniyomi stores the clearance cookie (`cf_clearance`), and future requests will play automatically.

### Mirror & Domain Override
- If an internet service provider blocks a default domain, navigate to **Settings** -> **Browse** -> **Extensions** -> tap the extension -> **Custom Base URL**. Enter any operational mirror listed in [docs/SOURCES.md](docs/SOURCES.md).

### Quality & Video Host Preferences
- Each extension provides configurable preferences for preferred server hosts (e.g. prioritize StreamPoi or Streamtape) and default video resolution (1080p, 720p, 480p).

### Sorting Order in Kototoro (Android vs Desktop PC)
- **Top Bar Sort (⇅) vs Filter Dialog (⚙)**:
  - Kototoro's top toolbar sort icon only toggles between **Popularity** and **Latest Updates** (hardcoded in Kototoro's `AniyomiAnimeRepository`).
  - All extension-specific custom sort orders (e.g. **Most Viewed**, **Top Rated**, **Rating**, **Title A-Z**, **Duration**, **Upload Date**) are located inside the **Filter sheet** (the funnel/filter icon).
  - Tap the **Filter** icon, select your desired chip in the **Urutan (Sorting Order)** section, and tap **Apply (Terapkan)**. The catalogue immediately updates with the requested sort.

---

## 6. Development & Verification

### Running Offline Tests & Code Coverage
All tests run 100% offline using deterministic HTML fixtures under `src/<lang>/<source>/src/test/resources/`:

```bash
# Run all unit tests and generate JaCoCo coverage
./gradlew check jacocoTestReport
```

### Mandatory Verification Suite
Every change must pass the full 8-step verification pipeline:

```bash
# 1. Unit & Contract Tests + Host API Binding Checks + APK Verification
./gradlew check

# 2. Host Class Exclusion & Bytecode Linkage Verification
python tools/verify_no_host_classes.py
python tools/verify_host_api_binding.py

# 3. Stream Hygiene & Header Invariant Verification
python tools/verify_stream_hygiene.py

# 4. Monotonic Auto-Update Simulation
python tools/test_update_simulation.py

# 5. Preflight & Diagnostic Sanitization
python tools/test_preflight.py
python tools/test_collect_diagnostics.py

# 6. Secret Scanner (Zero Leaks Required)
python tools/scan_secrets.py
```

### Assembling & Signing Release APKs
```bash
./gradlew assembleRelease
```
Outputs signed APKs to `repo/apk/`, generates `repo/sha256sums.txt`, and builds `repo/index.json` / `repo/index.min.json` / `repo/repo.json`.

### Version Bumps
```bash
# Automatically bump affected extensions based on git diff
./gradlew bumpChanged

# CI check to ensure version codes were incremented
./gradlew checkBump
```

---

## 7. Architecture, Skills & Contributing

- Developer & AI operational skills: [SKILLS.md](SKILLS.md) / [.agents/skills/kaziyami-extension](.agents/skills/kaziyami-extension/SKILL.md)
- System design details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Technology versions: [docs/STACK.md](docs/STACK.md)
- Kototoro integration & invariants: [docs/KOTOTORO.md](docs/KOTOTORO.md)
- Auto-update guide & rollbacks: [docs/UPDATING.md](docs/UPDATING.md)
- Keystore setup & CI secrets: [docs/SIGNING.md](docs/SIGNING.md)
- Release procedures: [docs/RELEASING.md](docs/RELEASING.md)
- Troubleshooting & issue reporting: [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
- Contributing guidelines: [CONTRIBUTING.md](CONTRIBUTING.md)
