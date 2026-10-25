# Kaziyami Troubleshooting Guide

This guide covers common playback issues, anti-bot challenges, network mirror blocks, and how to report issues cleanly without leaking authentication tokens or personal session cookies.

---

## 1. Cloudflare Challenge Pages & Turnstile

Adult anime streaming portals frequently deploy Cloudflare Managed Challenges or Turnstile verification to stop headless scrapers.

### Symptoms
- Error message: `CloudflareChallengeException: Cloudflare challenge detected`.
- HTTP 403 Forbidden or HTTP 503 Service Unavailable returned with `cf-mitigated: challenge` header.

### Resolution Steps
1. Tap the three dots (⋮) in the top-right corner of the anime details screen.
2. Select **Open in WebView**.
3. Wait for the page to load, then solve the Cloudflare CAPTCHA or checkbox.
4. Once the site displays normal content, tap the back button to return to Aniyomi.
5. Pull down to refresh the screen. The clearance cookie (`cf_clearance`) is now stored in Aniyomi's cookie jar, allowing automated playback to resume.

---

## 2. Blocked Mirror Domains & Custom Base URL

Regional Internet Service Providers (e.g. Indonesian Kominfo / TrustPositif DNS filtering) frequently block adult domain names.

### Symptoms
- `java.net.UnknownHostException`
- `java.net.SocketTimeoutException`
- Connection reset by peer (`ECONNRESET`)

### Resolution Steps
1. Refer to [docs/SOURCES.md](SOURCES.md) for the active list of working mirror domains.
2. In Aniyomi, open **Settings** -> **Browse** -> **Anime Extensions**.
3. Tap the affected extension (e.g., `Kaziyami: Nekopoi`).
4. Select **Custom Base URL** / **Mirror Domain Override**.
5. Enter an alternative verified mirror (e.g., replace `https://nekopoi.care` with `https://nekopoi.org`).
6. Tap **Save** and retry loading.

---

## 3. Expiring & Tokenized Video URLs

Certain video host engines (such as StreamPoi or Astro SSR CDNs) generate time-limited query tokens (`?token=...&expires=...`) or restrict playback by IP/Referer.

### Symptoms
- Video stream URL resolves, but the internal player displays `HTTP 403 Forbidden` or `Source Error`.

### Resolution Steps
- Video links are valid only for a short duration (typically 30–120 minutes). If you paused playback or left an episode paused for a prolonged period, back out to the episode list and re-open the episode to generate a fresh stream token.
- Ensure your device is not switching network interfaces (e.g. Wi-Fi to cellular data) during token negotiation, as tokenized CDNs validate the client IP.

---

## 4. Submitting a Bug Report: Safe Log Hygiene

When opening an issue on GitHub, **never paste raw headers, cookies, or authorization tokens**.

### What NOT to Include:
- `Cookie:` header contents (especially `cf_clearance`, `PHPSESSID`, `user_token`).
- Full stream query strings containing secret signatures (`?sign=...`, `?key=...`, `?auth=...`).
- Personal IP addresses or device serial numbers.

### What to Include:
1. **Source Name and Extension Version**: e.g., `Kaziyami: NontonHentai v1.4.1`.
2. **Aniyomi Version**: e.g., `Aniyomi Preview r7821`.
3. **Android OS Version**: e.g., `Android 13 (Tiramisu)`.
4. **Network Type**: Residential Wi-Fi / Mobile Carrier (Telkomsel, Indosat, XL, etc.) / VPN.
5. **Sanitized Error Log**:
   ```text
   Failed to extract video list for: /hentai/sample-episode-1/
   Exception: StreamWishExtractor: player source URL not found in DOM
   HTTP Status: 200 OK
   ```
