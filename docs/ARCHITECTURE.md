# System Architecture & Design Specification

Comprehensive architecture documentation for the Aniyomi extension repository.

---

## 1. Module Map

```text
kaziyami/
├── core/                                   # Shared foundational models, network, parsers, and utilities
│   ├── src/main/kotlin/
│   │   ├── eu/kanade/tachiyomi/animesource/ # Aniyomi core interfaces & models (SAnime, SEpisode, Video)
│   │   └── app/kaziyami/
│   │       ├── filters/                   # Dynamic FilterSpec, DynamicSelect, TriState, and FilterBuilder
│   │       ├── core/                      # Utilities: UrlUtils, LazyImage, EpisodeNumberParser, DateParser,
│   │       │                              #   HtmlText, MirrorManager, SourceConfig, ExtractorRegistry
│   │       ├── extractors/                # Built-in host stream extractors (Direct, StreamTape, Dood, etc.)
│   │       └── network/                   # BrowserHeaders, RateLimitInterceptor, RetryInterceptor,
│   │                                      #   ChallengeDetector, CloudflareInterceptor, NetworkFactory
│   └── src/test/kotlin/.../               # 80%+ coverage unit & integration tests with MockWebServer
│
├── lib-multisrc/                          # Engine-specific base templates inheriting ParsedAnimeHttpSource
│   ├── generic/                           # app.kaziyami.multisrc.generic: GenericThemeSource
│   ├── dooplay/                           # app.kaziyami.multisrc.dooplay: DooPlayTheme (AJAX player decoder)
│   ├── animestream/                       # app.kaziyami.multisrc.animestream: AnimeStreamTheme (Base64 embed options)
│   └── retrotube/                         # app.kaziyami.multisrc.retrotube: RetroTubeTheme (Tube-style multi-server base)
│
├── src/                                   # Standalone runnable extension modules
│   ├── id/                                # Indonesian extensions
│   │   ├── nekopoi/                       # Custom ThemeNekopoi / WordPress source
│   │   ├── indohentai/                    # Custom SSR card grid source
│   │   └── kuronime/                      # AnimeStream engine source
│   └── en/                                # English / Multi-language extensions
│       ├── hentaimama/                    # DooPlay engine source
│       ├── javsubbed/                     # RetroTube engine source
│       └── hanime/                        # Astro / Vue SSR direct source
│
├── schemas/                               # JSON schema definitions
│   ├── source_config.schema.json          # SourceConfig declarative specification schema
│   └── index.schema.json                  # Aniyomi extension repository index.json schema
│
├── sources/                               # Declarative site configuration JSON files
│   └── demo_source.json                   # Reference implementation demonstrating schema validation
│
├── scripts/                               # Developer tooling & test harnesses
│   └── fetch_fixture.py                   # Automated fixture download & sanitization tool
│
└── tools/                                 # Build & release automations
    └── generate_repo_index.py             # Generates index.json and index.min.json for Aniyomi clients
```

---

## 2. Request & Execution Flow

Every request in the extension lifecycle follows an anti-bot resilient, failover-protected pipeline:

```
[ User Action / Aniyomi UI ]
             │
             ▼
[ AnimeHttpSource / ThemeBase ]
             │  Resolves active domain via [MirrorManager.currentBase()]
             ▼
[ OkHttpClient Instance (NetworkFactory) ]
             │
             ├──► 1. RateLimitInterceptor (Per-host token bucket + random jitter delay)
             │
             ├──► 2. RetryInterceptor (Catches 429 / 503 / timeouts, respects Retry-After, exponential backoff)
             │
             ├──► 3. BrowserHeaderInterceptor (Spoofs modern Chrome User-Agent, sec-ch-ua, platform, referer)
             │
             └──► 4. CloudflareInterceptor / ChallengeDetector
                         │
                         ├── [Status 200 OK] ──────────────────────────────────────────┐
                         │                                                             │
                         └── [cf-mitigated / 403 / 503 Challenge]                      │
                                     │                                                 │
                                     ▼                                                 │
                         [CloudflareChallengeException]                                │
                         (Notifies user to solve challenge via embedded WebView)       │
                                                                                       │
                                                                                       ▼
                                                                     [ HTML / API Response ]
                                                                                       │
                                                                                       ▼
                                                                        [ Jsoup DOM Parsing ]
                                                                        - Selector drift guard
                                                                        - Empty state check
                                                                        - LazyImage thumbnail
                                                                        - EpisodeNumberParser
                                                                        - DateParser
                                                                        - HtmlText entity cleanup
                                                                                       │
                                                                                       ▼
                                                                          [ ExtractorRegistry ]
                                                                        - Resolves embed URLs
                                                                        - Deduplicates qualities
                                                                        - Prioritizes 1080p
                                                                                       │
                                                                                       ▼
                                                                           [ List<Video> Stream ]
```

---

## 3. Selector Drift & Bot Protection Guardrails

1. **Selector Drift Detection**:
   If an HTML page returns 200 OK and is not an empty search result, but the list selector matches `0` elements, `GenericThemeSource` throws `IllegalStateException("Selector drift detected in <Source>: item selector returned 0 elements")`. This prevents silent blank screens when sites update layouts.
2. **Empty State Detection**:
   Distinguishes true "no results" from structural breaks by checking Indonesian ("tidak ada", "ditemukan") and English ("no results", "not found") empty-state markers.
3. **Relative URL Storage**:
   All `SAnime.url` and `SEpisode.url` paths are stripped of base domain (`UrlUtils.getPath`) to ensure bookmark integrity across mirror failovers.
4. **Stable Episode Ordering**:
   Episodes are automatically deduplicated by URL and sorted newest-first using parsed episode numbers or dates.

---

## 4. Adding a New Site in ~15 Lines

By inheriting [GenericThemeSource](file:///e:/project/kaziyami/lib-multisrc/generic/src/main/kotlin/eu/kanade/tachiyomi/multisrc/generic/GenericThemeSource.kt) or declaring a JSON config, pure-selector sites require minimal code:

```kotlin
package eu.kanade.tachiyomi.animeextension.en.demosite

import app.kaziyami.core.SelectorsConfig
import app.kaziyami.core.SourceConfig
import app.kaziyami.multisrc.generic.GenericThemeSource

class DemoSite : GenericThemeSource(
    SourceConfig(
        name = "DemoSite",
        lang = "en",
        baseUrl = "https://demosite.com",
        selectors = SelectorsConfig(
            popularItem = "div.film-item",
            popularTitle = "h2.title a",
            popularThumbnail = "img.poster",
            popularNextPage = "a.next-page",
            detailsTitle = "h1.entry-title",
            episodeList = "ul.episodes-list li a",
            episodeNumber = ".ep-num",
            videoEmbeds = "iframe.player-iframe",
            searchUrlTemplate = "/search?q=%s&page=%d"
        )
    )
)
```

Alternatively, the configuration can reside in `sources/demosite.json` validated against `schemas/source_config.schema.json` at build time.
