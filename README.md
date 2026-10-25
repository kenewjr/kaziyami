# Kaziyami: Adult Anime Extension Repository

[![Kaziyami CI](https://github.com/kenewjr/kaziyami-extensions/actions/workflows/ci.yml/badge.svg)](https://github.com/kenewjr/kaziyami-extensions/actions/workflows/ci.yml)
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
| **Aniyomi (Stable & Preview)** | **Supported** | Full compatibility with standard `AnimeExtensionLoader` APK contract. |
| **Kuukiyomi** | **Supported** | Verified upstream fork sharing identical APK loader and DEX runtime. |
| **Kototoro** | *Unsupported* | Incompatible architecture. Kototoro uses in-app compiled scrapers rather than Android APK extensions (see [docs/KOTOTORO.md](docs/KOTOTORO.md)). |

---

## 2. Installation in Aniyomi

To add Kaziyami directly to Aniyomi:

1. Open **Aniyomi** on your Android device.
2. Navigate to **More** -> **Settings** -> **Browse** -> **Anime extension repos**.
3. Tap **Add repository** (+).
4. Enter the raw repository URL:
   ```text
   https://raw.githubusercontent.com/kenewjr/kaziyami-extensions/repo/index.min.json
   ```
5. Return to the **Browse** -> **Extensions** tab and pull down to refresh.
6. The extensions will appear under the **Kaziyami** family label (e.g., `Kaziyami: Nekopoi`). Ensure the **18+** filter toggle is enabled in your extension list settings.

### Trusting the Signing Key

When prompted by Android or Aniyomi to verify the extension repository signing certificate, verify that the SHA-256 fingerprint matches the release certificate:

```text
SHA-256: bedd5a8fc5096e76dc0b4acea77d57376cca29c087c6dacab508a0c689c3e44e
```

*(If building with your own private release key, verify the fingerprint produced by `python tools/get_apk_fingerprint.py <built_apk>` as described in [docs/SIGNING.md](docs/SIGNING.md)).*

---

## 3. Catalog of Sources

All 6 adult sources are configured with permanent 64-bit Source IDs locked in [docs/SOURCE_IDS.md](docs/SOURCE_IDS.md):

| Source Label | Language | Engine / CMS | Base URL | Primary Stream Providers |
|---|---|---|---|---|
| **Kaziyami: Nekopoi** | Indonesian (`id`) | WordPress (ThemeNekopoi) | `https://nekopoi.care` | StreamPoi HLS (1080p/720p/480p), PlayMogo, Mp4Upload |
| **Kaziyami: IndoHentai** | Indonesian (`id`) | Custom SSR Card Grid | `https://indohentai.net` | Loadvid, XXContent CDN |
| **Kaziyami: NontonHentai** | Indonesian (`id`) | WordPress (AnimeStream) | `https://nontonhentai.net` | HentaiCop, HepiDrive, GDPlayer, LocalStream |
| **Kaziyami: Hentaimama** | English (`en`) | WordPress (DooPlay) | `https://hentaimama.io` | Direct 1080p/720p MP4, GDVid |
| **Kaziyami: JAVSubbed** | English (`en`) | WordPress (RetroTube) | `https://javsubbed.net` | Streamtape, VOE, Doodstream |
| **Kaziyami: Hanime** | English (`en`) | Astro / Vue SSR | `https://hanime.tv` | Direct HLS (`master.m3u8` 1080p/720p) |

---

## 4. Frequently Asked Questions (FAQ)

### Cloudflare "Open in WebView"
- **Why do I see a challenge error?** Adult sites frequently use Cloudflare Managed Challenges or Turnstile to prevent automated scraping.
- **How to resolve**: Tap the overflow menu (⋮) on the anime details screen and select **Open in WebView**. Complete the Cloudflare verification checkbox once. Aniyomi stores the clearance cookie (`cf_clearance`), and future requests will play automatically.

### Mirror & Domain Override
- If an internet service provider blocks a default domain, navigate to **Settings** -> **Browse** -> **Extensions** -> tap the extension -> **Custom Base URL**. Enter any operational mirror listed in [docs/SOURCES.md](docs/SOURCES.md).

### Quality & Video Host Preferences
- Each extension provides configurable preferences for preferred server hosts (e.g. prioritize StreamPoi or Streamtape) and default video resolution (1080p, 720p, 480p).

---

## 5. Development & Verification

### Running Offline Tests & Code Coverage
All tests run 100% offline using deterministic HTML fixtures under `src/<lang>/<source>/src/test/resources/`:

```bash
# Run all unit tests and generate JaCoCo coverage
./gradlew check jacocoTestReport
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

## 6. Architecture & Contributing

- System design details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Technology versions: [docs/STACK.md](docs/STACK.md)
- Keystore setup & CI secrets: [docs/SIGNING.md](docs/SIGNING.md)
- Release procedures: [docs/RELEASING.md](docs/RELEASING.md)
- Troubleshooting & issue reporting: [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
- Contributing guidelines: [CONTRIBUTING.md](CONTRIBUTING.md)
