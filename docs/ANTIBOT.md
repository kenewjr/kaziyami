# Kaziyami Anti-Bot & Network Resilience Architecture

This document describes how Kaziyami extensions communicate with source websites, manage anti-bot mitigations, and handle network challenges.

---

## 1. What the Extension Does

Kaziyami behaves as a polite, standard browser client:
1. **Request Budgeting (Token Bucket)**: Enforces a token bucket (3 requests / 1 second by default) per host with randomized micro-jitter (30ms–120ms) to avoid mechanical request cadences.
2. **Concurrency Cap**: Restricts in-flight requests per host to 1–2 concurrent connections. Prevents burst traffic during catalog browsing or episode indexing.
3. **Aggressive TTL Caching**: Caches popular/latest listings (5 min), detail metadata (10 min), episode lists (10 min), and filter categories (1 hour) using [TtlCache](file:///e:/project/kaziyami/core/src/main/kotlin/app/kaziyami/network/TtlCache.kt). Prevents duplicate HTTP calls during a single browsing session.
4. **Browser Navigation Referer Chain**: Reconstructs realistic user navigation chains (`list -> detail -> episode`) with matching `Referer` headers.
5. **Sequential Extractor Resolution**: Prioritizes the user's preferred host and stops on the first working stream. Only probes alternate video hosts if earlier candidates fail.
6. **Session & Cookie Consistency**: Shares cookies across calls per host. Tracks `cf_clearance` cookie validity and expiration timestamps.
7. **Consistent Browser Profile & Headers**: [BrowserProfile](file:///e:/project/kaziyami/core/src/main/kotlin/app/kaziyami/network/BrowserProfile.kt) guarantees that User-Agent and Client Hints (`Sec-Ch-Ua`, `Sec-Ch-Ua-Mobile`, `Sec-Ch-Ua-Platform`) strictly match. If a match cannot be proven, client hints are omitted. Headers are emitted in a 100% deterministic canonical order.
8. **HTTP/2 Connection Pooling**: Reuses underlying TCP/TLS connections with keep-alive via OkHttp `ConnectionPool`.
9. **Circuit Breakers**: [HostCircuitBreaker](file:///e:/project/kaziyami/core/src/main/kotlin/app/kaziyami/network/HostCircuitBreaker.kt) pauses requests to a host after 4 consecutive failures for a 30-second cooldown, preventing hammer behavior against failing origins.
10. **WebView Challenge Solving**: Handles automated JavaScript challenges (`cf-mitigated: challenge`, 5-second waiting rooms) through Android WebView, coalescing concurrent requests so only one solver runs per host.

---

## 2. What the Extension Deliberately Does NOT Do

To maintain code integrity, privacy, and user security:
- **NO Third-Party Captcha Solving Services**: Does not integrate anti-captcha, 2captcha, Capsolver, or any paid/third-party API solvers.
- **NO Fingerprint Spoofing**: Does not inject canvas, WebGL, AudioContext, or navigator-spoofing JavaScript hacks into WebViews. Standard, unmodified Android System WebView defaults are used.
- **NO Proxy or IP Rotation**: Does not bundle proxies, Tor, VPNs, or proxy scrapers. All traffic flows directly from the user's network connection.
- **NO Bypass of Logins, Paywalls, or DRM**: Does not crack premium tiers, bypass user logins, or decrypt protected DRM streams (Widevine/FairPlay).
- **NO Telemetry**: Zero analytics, zero phone-home pings, zero crash-reporting servers.

---

## 3. Known Limitations & Failure Modes

1. **Interactive Captchas (Turnstile Checkboxes, hCaptcha, reCaptcha)**:
   - When a website presents an interactive checkbox or image puzzle, the extension cannot solve it programmatically.
   - **Behavior**: Fails fast with `INTERACTIVE_CAPTCHA` ("Open site in WebView to complete check, then retry"). The user taps **Open in WebView**, solves the checkmark once, returns to Aniyomi, and refreshes. Cookies are automatically captured.
2. **IP / ISP / Regional Filtering**:
   - The extension cannot bypass regional censorship or government blocks (such as Indonesian Kominfo / Internet Positif).
   - **Fix**: The user must configure Private DNS (DNS-over-HTTPS, e.g. `one.one.one.one` or `dns.google`) or use a VPN on the device.
3. **Signed Video URL Expiry**:
   - Video URLs signed with expiring tokens (e.g. `?token=...&expires=...`) will expire after their TTL.
   - **Behavior**: The extension re-resolves the embed URL once on HTTP 403/410 to obtain a fresh signature before playback.

---

## 4. Per-Source Protection & Behavior Matrix

| Source | Engine | Observed Protection | Typical Challenge Class | Extension Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **Nekopoi** | WordPress / StreamPoi | Cloudflare CDN + StreamPoi packer | `NONE` (Clean) / `JS_CHALLENGE` | Rate-limited requests; auto-solves JS challenge if encountered; unpacks HLS stream. |
| **IndoHentai** | SSR Card Grid | Regional ISP filtering / DNS sinkhole | `ISP_BLOCK` / `NONE` | Detects ISP block page and suggests Private DNS; fails over to alternate mirrors (`indohentai.site`). |
| **NontonHentai** | AnimeStream | Cloudflare Bot Management | `NONE` (Clean) / `JS_CHALLENGE` | Single-threaded catalog queries; extracts embed iframes sequentially. |
| **Hentaimama** | DooPlay | Cloudflare Turnstile (Managed) | `INTERACTIVE_CAPTCHA` (Occasional) | Prompts user to open WebView once; persists clearance cookie for remainder of session. |
| **JAVSubbed** | RetroTube | Standard CDN / Embed Protections | `NONE` (Clean) | Sequential extractor resolution (Streamtape -> VOE -> Doodstream), stopping on first working stream. |
| **Hanime** | Custom Astro SSR | Cloudflare Turnstile / Guest Session | `NONE` (Clean) / `INTERACTIVE_CAPTCHA` | Generates guest session token; direct HLS manifest playback. |
