# Kaziyami Hardening & Resilience Specification

This document details the defensive engineering measures implemented across Kaziyami to protect against layout drift, network interference, domain churn, and library corruption.

---

## 1. Protection Matrix

| Vulnerability / Failure Mode | Protective Mechanism | Code Component | Verified By Test |
| :--- | :--- | :--- | :--- |
| **Site Layout Changes** | Multi-candidate ordered selectors + session caching; structured exceptions. | `SourceConfig`, `SelectorsConfig`, `GenericThemeSource` | `SelectorDriftTest` |
| **Domain Seizure / ISP Block** | Thread-safe ordered mirror failover + manual user override preference. | `MirrorManager`, `GenericThemeSource` | `MirrorResilienceTest` |
| **Mirror Hijacking** | Strict HTTPS domain allowlist rejecting unauthorized hosts. | `RemoteMirrorFetcher` | `MirrorResilienceTest` |
| **Bookmark Corruption** | Relative path storage + normalization on read stripping domains/tracking. | `UrlUtils.normalizeStoredPath` | `LibrarySafetyTest` |
| **Malformed / Truncated HTML** | Non-crashing fault-tolerant parsing for truncated BOM, broken tags, and deeply nested DOMs. | Jsoup parser integration | `MalformedHtmlCorpusTest`, `ParserFuzzTest` |
| **Parser Crashing on Unicode/Garbage** | Fuzzed string parsers for episode numbers, dates, titles, and image URLs. | `EpisodeNumberParser`, `DateParser`, `HtmlText` | `ParserFuzzTest` |
| **High Latency / Memory Spikes** | In-flight request deduplication + TTL memory caching; offline benchmarks. | `TtlCache`, `NetworkConfig` | `ParserBenchmarkTest` |
| **Network Identity Leakage** | Complete header and body audit preventing `"kaziyami"`, `"aniyomi"`, `"tachiyomi"`. | `BrowserHeaders`, `SimpleCookieJar` | `NetworkLeakTest` |
| **Sensitive Data Exposure in Logs** | Automated log scrubber redacting cookies, auth tokens, and signed video URLs. | `LogScrubber` | `LogScrubberTest` |
| **Silent Failures & Cryptic Errors** | Clear error UX mapping technical codes to actionable user messages. | `ErrorClassifier` | `ErrorClassifierTest` |

---

## 2. Residual Risks per Source

| Source | Residual Risk | Severity | Mitigation Strategy |
| :--- | :--- | :---: | :--- |
| **Nekopoi** (`id`) | Rapid mirror domain turnover by Indonesian regulatory blocks. | Medium | Built-in mirror failover (`nekopoi.app`, `nekopoi.link`) + custom domain override preference. |
| **IndoHentai** (`id`) | Cloudflare managed challenge presented to datacenter/cloud IP ranges. | Medium | Browser-like headers minimize triggers; user prompted with clear message to complete challenge in WebView. |
| **NontonHentai** (`id`) | Video host embed domain churn (e.g. HepiDrive / LocalStream rotating URLs). | Low | Multi-server fallback parser decodes Base64 option streams dynamically. |
| **Hentaimama** (`en`) | WordPress admin-ajax endpoint rate limits rapid bursts of video requests. | Low | `TtlCache` deduplicates player requests; backoff interceptor handles HTTP 429. |
| **JAVSubbed** (`en`) | Third-party tube video hosts (Streamtape, VOE, DoodStream) change token obfuscation. | Medium | Modular extractors isolated in `core/extractors/`; independent updates without breaking core source logic. |
| **Hanime** (`en`) | Cloudflare Turnstile token verification required for video API endpoints. | High | Direct HLS extraction fallback; clear WebView clearance message when Turnstile token expires. |
