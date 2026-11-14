# Kaziyami & Kototoro Extension Skills

Comprehensive reference and operational runbook for developing, debugging, testing, and packaging video extensions for **Kototoro** and **Aniyomi**.

This guide combines Kototoro's source testing standards, Aniyomi extension binary ABI requirements, and Kaziyami's multisrc architecture.

> [!NOTE]
> The automated Antigravity / AI Agent skill is located at [.agents/skills/kaziyami-extension/SKILL.md](file:///e:/project/kaziyami/.agents/skills/kaziyami-extension/SKILL.md).

---

## 1. Quick Invariants Checklist

1. **Permanent 64-bit Source IDs**: See [docs/SOURCE_IDS.md](file:///e:/project/kaziyami/docs/SOURCE_IDS.md). Never alter source IDs; it corrupts user bookmarks and watch history.
2. **Compile-Only Host ABI & Zero Host Classes**: Extensions compile against official `com.github.aniyomiorg:extensions-lib:14` as `compileOnly`. Host packages (`eu.kanade.*`, `okhttp3.*`, `okio.*`, `kotlin.*`, `androidx.*`) must never be packaged into APKs (`verify_no_host_classes.py`).
3. **Display Label vs Source Name**: `AnimeSource.name` must be the raw site name (e.g. `Nekopoi`, `Hanime1`), while manifest label and repo index use `Kaziyami: <Site>`.
4. **Independent Monotonic Versioning**: Only bump `extVersionCode` and `extVersion` on extensions with actual modifications.
5. **No Third-Party Automated Captcha Solvers**: Cloudflare Turnstile tokens bind to device TLS fingerprints and WebView sessions. Route challenges via host WebView solver (`network.cloudflareClient`).
6. **Strict Content Safety**: Zero tolerance for minors, voyeurism, and non-consensual content ([core/src/main/kotlin/app/kaziyami/ContentPolicy.kt](file:///e:/project/kaziyami/core/src/main/kotlin/app/kaziyami/ContentPolicy.kt)).

---

## 2. Kototoro 7-Step Verification Suite (`SourceTester`)

Kototoro tests extensions across 7 sequential stages:

| Step | Action | Requirements & Watchouts |
|:-----|:-------|:--------------------------|
| `LIST` | Catalogue fetch | Return page 1 immediately when `animes.isNotEmpty()` to preserve 20s budget. |
| `COVERS` | Cover extraction | Must extract valid non-empty thumbnail; support `.nk-thumb-crop` with inline CSS `background-image: url(...)`. |
| `DETAILS` | Metadata & synopsis | Fallback to `meta[property='og:image']` and `meta[property='og:description']`. |
| `CHAPTERS` | Episode listing | Handle single-video posts by synthesizing single episode (`"Full Episode"`). |
| `PAGES` | Video stream extraction | Kototoro peeks 4KB from `video.videoUrl` and checks `looksLikeVideoStream`. **Never return HTML URLs**. If direct extractors fail, supply fallback `Video(url, "Stream (Open in WebView if blocked)", url, headers)`. |
| `RANDOM` | Random item sample | Samples item from catalogue. |
| `SEARCH` | Keyword search | Kototoro tests query `"a"`. Backends with $\ge 2$ char rewrites return 404; pad single-character queries: `cleanQuery + cleanQuery`. |

---

## 3. Video Stream & Playback Hygiene

```kotlin
// Video headers MUST use BrowserHeaders.mediaHeaders to guarantee User-Agent
// and strip document navigation headers (preventing CDN 403 & 404)
val headers = BrowserHeaders.mediaHeaders(
    referer = baseUrl,
    extraHeaders = headers
)
```

- **User-Agent**: Required for video CDNs (e.g. RuleVid, Hanime1). Missing UA causes HTTP 403.
- **Header Sanitization**: Strip `Sec-Fetch-Dest: document` and `Upgrade-Insecure-Requests: 1` on media requests. Presence causes video CDNs to return HTTP 404.
- **DNS Pre-Validation**: Validate ephemeral embed domains with `canResolveHost(host)` before returning streams.

---

## 4. Multisrc Engines & Extensions

| Source | Lang | Multisrc Engine | Package |
|:-------|:-----|:----------------|:--------|
| `nekopoi` | `id` | `lib-multisrc:generic` | `eu.kanade.tachiyomi.animeextension.id.nekopoi` |
| `nontonhentai` | `id` | `lib-multisrc:animestream` | `eu.kanade.tachiyomi.animeextension.id.nontonhentai` |
| `hentaimama` | `en` | `lib-multisrc:dooplay` | `eu.kanade.tachiyomi.animeextension.en.hentaimama` |
| `hanime` | `en` | `lib-multisrc:generic` | `eu.kanade.tachiyomi.animeextension.en.hanime` |
| `rulevid` | `en` | `lib-multisrc:kvs` | `eu.kanade.tachiyomi.animeextension.en.rulevid` |
| `rule34video` | `en` | `lib-multisrc:kvs` | `eu.kanade.tachiyomi.animeextension.en.rule34video` |
| `3xchina` | `zh` | `lib-multisrc:retrotube` | `eu.kanade.tachiyomi.animeextension.zh.threexchina` |
| `hanime1` | `zh` | `lib-multisrc:generic` | `eu.kanade.tachiyomi.animeextension.zh.hanime1` |

---

## 5. Verification Commands

Before every push, run the full verification pipeline:

```bash
# 1. Gradle Check (Unit tests, Kototoro harness, stream hygiene, APK assembly)
./gradlew check

# 2. Host Class Isolation (0 host classes in APKs)
python tools/verify_no_host_classes.py

# 3. Host Classpath Linkage
python tools/verify_host_api_binding.py

# 4. Stream Hygiene Verifier
python tools/verify_stream_hygiene.py

# 5. Monotonic Update Policy Simulation
python tools/test_update_simulation.py

# 6. Diagnostic Preflight & Sanitizer
python tools/test_preflight.py
python tools/test_collect_diagnostics.py

# 7. Secret Scanner (0 leaks)
python tools/scan_secrets.py
```
