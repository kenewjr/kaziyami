# Kaziyami: NSFW Anime Extension Repository for Aniyomi

Clean, maintainable Aniyomi adult anime extension repository written in Kotlin & Gradle. Implements a shared multisrc engine architecture, anti-bot browser-level networking, offline unit & contract tests, and automated repository indexing.

---

## 1. Catalog of Sources

| Source | Language | CMS / Engine | Primary Base URL | Video Stream Extractor |
|---|---|---|---|---|
| **Nekopoi** | Indonesian (`id`) | WordPress (ThemeNekopoi) | `https://nekopoi.care` | PlayMogo, StreamPoi, Mp4Upload, Pixeldrain |
| **IndoHentai** | Indonesian (`id`) | Custom SSR Card Grid | `https://indohentai.net` | Loadvid, XXContent CDN |
| **NontonHentai** | Indonesian (`id`) | WordPress (AnimeStream) | `https://nontonhentai.net` | HentaiCop, HepiDrive, GDPlayer, LocalStream |
| **Hentaimama** | English (`en`) | WordPress (DooPlay) | `https://hentaimama.io` | Direct 1080p/720p MP4, GDVid |
| **JAVSubbed** | English (`en`) | WordPress (RetroTube) | `https://javsubbed.net` | Streamtape, VOE, Doodstream |
| **Hanime** | English (`en`) | Astro / Vue SSR | `https://hanime.tv` | Direct HLS (m3u8 1080p/720p) |

Full research, mirror list, and Cloudflare evaluation available in [docs/SOURCES.md](docs/SOURCES.md).

---

## 2. Installation in Aniyomi

To install extensions from this repository directly in Aniyomi:
1. Open **Aniyomi Settings** -> **Browse** -> **Anime extension repos**.
2. Tap **Add repository**.
3. Enter the raw repository URL:
   ```text
   https://raw.githubusercontent.com/<user>/kaziyami/repo/index.min.json
   ```
4. Return to the **Extensions** tab and pull down to refresh. All 6 adult extensions will appear under the Adult (18+) filter.

---

## 3. Architecture & Multisrc Engine

Extensions share core networking, anti-bot safeguards, and selector parsers through modular libraries:

```text
kaziyami/
├── core/                  # Models (SAnime, SEpisode, Video), Network stack, Parsers, Extractors
├── lib-multisrc/
│   ├── generic/          # SourceConfig, GenericThemeSource (declarative selector parser)
│   ├── dooplay/          # DooPlayTheme (AJAX player decoder, /tvshows/, /episodes/)
│   ├── animestream/      # AnimeStreamTheme (Base64 option server decoder)
│   └── retrotube/        # RetroTubeTheme (Sourcetabs & single-video fallback)
├── src/
│   ├── id/               # Indonesian extensions (nekopoi, indohentai, kuronime)
│   └── en/               # English extensions (hentaimama, javsubbed, hanime)
├── tools/                # Fixture refresh script & index.json generator
└── repo/                 # index.json & index.min.json Aniyomi repo index
```

### Adding a New Site in ~15 Lines

Adding a new website that uses an existing engine requires declaring a `SourceConfig`:

```kotlin
package eu.kanade.tachiyomi.animeextension.id.mysite

import app.kaziyami.multisrc.animestream.AnimeStreamTheme
import app.kaziyami.multisrc.generic.SourceConfig

class MyAnimeStreamSite : AnimeStreamTheme(
    SourceConfig(
        name = "MyAnimeStreamSite",
        lang = "id",
        baseUrl = "https://myanimestreamsite.com",
        mirrors = listOf("https://myanimestreamsite.com", "https://mirror.myanimestreamsite.org"),
        itemSelector = ".listupd .bsx",
        itemTitle = ".tt",
        itemThumbnail = "img",
        itemUrl = "a",
        nextPageSelector = ".pagination .next",
        popularUrl = "/anime/?order=popular&page=%d",
        latestUrl = "/anime/?order=update&page=%d",
        searchUrl = "/?s=%s&page=%d"
    )
)
```

### Adding a New Engine Base

To introduce a new engine base:
1. Create a module under `lib-multisrc/<theme-name>/`.
2. Inherit from [GenericThemeSource](lib-multisrc/generic/src/main/kotlin/app/kaziyami/multisrc/generic/GenericThemeSource.kt).
3. Override engine-specific logic (e.g. custom AJAX episode endpoints or proprietary video player decoders):
   ```kotlin
   open class NewTheme(config: SourceConfig) : GenericThemeSource(config) {
       override fun videoListParse(response: Response): List<Video> {
           val document = response.asJsoup()
           val iframeUrl = document.selectFirst("iframe.custom-player")?.attr("abs:src")
           return iframeUrl?.let { ExtractorRegistry.extract(it) } ?: emptyList()
       }
   }
   ```

---

## 4. Networking & Anti-Bot Protection

This repository strictly employs legitimate browser-level networking practices:
- **Consistent Fingerprinting**: Uses standard Chromium `User-Agent` identical across OkHttp and Android WebView.
- **Header Order & Coherence**: Sends legitimate Sec-Fetch (`sec-fetch-dest`, `sec-fetch-mode`, `sec-fetch-site`), `Accept-Language`, and context-accurate `Referer`/`Origin` headers.
- **Automatic Rate Limiting**: Interceptor throttles outgoing requests per host to prevent traffic bursts (`RateLimitInterceptor`).
- **Exponential Backoff**: Respects HTTP `429 Too Many Requests` and `503 Service Unavailable`, honoring server `Retry-After` headers.
- **Cloudflare Challenge Interception**: Detects `cf-mitigated: challenge` and Cloudflare Turnstile/Interstitial HTML markers. Automatically dispatches the request to the WebView engine to solve clearance cookies before proceeding.
- **Failover Mirror Manager**: Remembers healthy mirror domains and seamlessly fails over on connection/DNS timeouts.

### Anti-Bot Limitations
- Client-side techniques cannot bypass servers enforcing strict IP reputation bans, datacenter ASN blocking, or invasive fingerprinting scripts that require active user pointer interaction.
- If a Cloudflare challenge cannot be solved automatically, an exception is thrown prompting the user to solve the challenge once via the built-in WebView.

---

## 5. Development & Testing

### Running Offline Unit & Contract Tests
All unit and contract tests run completely offline using deterministic HTML fixtures under `src/test/resources/<source>/`:

```bash
# Run all tests across all modules
./gradlew test

# Generate JaCoCo coverage report
./gradlew jacocoTestReport
```

### Refreshing HTML Fixtures
When target site selectors change or upstream updates markup, run the automated fixture downloader:

```bash
python tools/refresh_fixtures.py
```

### Live Smoke Integration Tests
Live tests hit target sites directly and verify `popular -> details -> episodes -> video stream 200/206`. They are tagged `@Tag("live")` and excluded from regular test runs:

```bash
./gradlew liveSmokeTest
```

---

## 6. Repository Index Generation & Schema Validation

To build `index.json` and `index.min.json` for Aniyomi repository hosting:

```bash
python tools/generate_repo_index.py
# Or via Gradle:
./gradlew generateRepoIndex
```

Output schemas conform to the Aniyomi custom repository format validated by [IndexSchemaTest](core/src/test/kotlin/app/kaziyami/schema/IndexSchemaTest.kt) and JSON Schema at [schemas/index.schema.json](schemas/index.schema.json).

---

## 7. CI/CD & APK Signing Setup

### GitHub Actions Workflows
- **`.github/workflows/build.yml`**: Runs `./gradlew test jacocoTestReport`, generates `index.json`, and deploys repo assets to the `repo` branch upon merge to `main`.
- **`.github/workflows/live-smoke.yml`**: Runs scheduled weekly live smoke tests (`./gradlew liveSmokeTest`) every Sunday at 04:00 UTC and automatically opens a GitHub issue if any stream fails.

### APK Signing Setup
**Never commit keystore files or passwords.** Configure GitHub repository secrets:
- `SIGNING_KEY_BASE64`: Base64 encoded release keystore file (`kaziyami.keystore`).
- `KEY_ALIAS`: Alias of the signing key (`kaziyami`).
- `KEY_PASSWORD`: Password for the signing key.
- `KEYSTORE_PASSWORD`: Password for the keystore.

In Gradle release tasks:
```kotlin
signingConfigs {
    create("release") {
        val keystoreBase64 = System.getenv("SIGNING_KEY_BASE64")
        if (!keystoreBase64.isNullOrEmpty()) {
            val keystoreFile = File(buildDir, "kaziyami.keystore")
            keystoreFile.writeBytes(java.util.Base64.getDecoder().decode(keystoreBase64))
            storeFile = keystoreFile
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: "kaziyami"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
}
```
