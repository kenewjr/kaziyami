# Candidate Sources Research & Verification (Phase 0 Corrections)

## 1. Verified & Evaluated Source Matrix

| Site | Mirrors | Engine / Theme | Lang | Cloudflare Level | Video Hosts | Pagination | Status | Selection & Rationale |
|---|---|---|---|---|---|---|---|---|
| **Nekopoi** | `https://nekopoi.care`, `https://nekopoi.app`, `https://nekopoi.link` | WordPress (ThemeNekopoi) | `id` | Low (Pass-through) | StreamPoi (Dean Edwards p.a.c.k.e.r), PlayMogo, Mp4Upload | Query / Next link | **Works** | **Selected (1/6)**: Verified adult hentai/JAV with Indonesian subs. HLS master.m3u8 stream verified answering Range 206 with `video/MP2T` chunks. Evidence in `docs/evidence/nekopoi/`. |
| **NontonHentai** | `https://nontonhentai.net` | WordPress (AnimeStream / `animestream`) | `id` | Low (Pass-through) | LocalStream / HepiDrive (GDPlayer base) in 720p, 480p, 360p | Path `/page/N/` | **Partial** | **Selected (2/6)**: Verified adult hentai with Indonesian subs. Replaces non-adult Kuronime. Uses standard AnimeStream Base64 option servers. Evidence in `docs/evidence/nontonhentai/`. |
| **IndoHentai** | `https://indohentai.net` | Custom SSR (Card grid layout) | `id` | High (`cf-mitigated: challenge`, HTTP 403) | Loadvid, XXContent (CFD) | Query `/?page=N` | **Unverified** | **Selected (3/6)**: Adult Indonesian subbed hentai. Blocked by Cloudflare Managed Challenge in headless CLI environment; requires Android WebView clearance. |
| **Hentaimama** | `https://hentaimama.io` | WordPress (DooPlay) | `en` | Low-Medium (Turnstile `0x4AAAAAAEgPA0XMACJJhK8U`) | Internal CDN / Direct MP4 | Path `/page/N/` | **Partial** | **Selected (4/6)**: Adult English subbed hentai. DooPlay theme verified. Player container `#playex` is dynamically hydrated; downloads gated by Turnstile. Evidence in `docs/evidence/hentaimama/`. |
| **JAVSubbed** | `https://javsubbed.net` | WordPress (RetroTube) | `en` | Direct IP block / Down | Streamtape, VOE, `player.mp4` | Path `/page/N/` | **Unverified** | **Selected (5/6)**: Adult JAV with English subs on RetroTube. Connection actively refused (`103.10.223.15:443 Connection refused`) from current environment; requires testing from user device. |
| **hhanime.tv** | None (`http://ww80.hhanime.tv`) | Nginx / Parking Squatter | `en` | None | None | None | **Blocked** | **Evaluated (6/6)**: Domain is parked/dead (`302 Found` to ad parking service, SSL untrusted). Cannot stream video. |
| **Hanime.tv** | `https://hanime.tv` | Astro SSR + Vue Client Islands | `en` | Medium (Turnstile `0x4AAAAAABsjKGKilqEiCHWo`) | Hanime CDN HLS | Client paginator | **Partial** | **Candidate**: Adult English hentai. `window.AppConfig` confirms `https://guest.freeanimehentai.net` API backend. Video player requires Turnstile and CSRF token. Evidence in `docs/evidence/hanime/`. |
| **Hanime1.me** | `https://hanime1.me` | Custom PHP | `zh` | High (`cf-mitigated: challenge`, HTTP 403) | Internal HLS | Query `?page=N` | **Unverified** | **Excluded**: Traditional Chinese site with Cloudflare Managed Challenge. |
| **JavSubIndo** | `https://javsubindo.com` | WordPress (RetroTube) | `id` | Low (Pass-through) | Internal Stream / Embed | Path `/page/N/` | **Partial** | **Indonesian Backup**: Authentic adult JAV with Indonesian subtitles running RetroTube theme. |
| **Ohentai** | `https://ohentai.org` | Custom PHP/jQuery | `en` | Low (Pass-through, 200 OK) | Internal player | Query `?page=N` | **Works** | **English Backup**: Fast adult English hentai streams with clean HTML. |

---

## 2. Summary of Phase 0 Corrections

1. **Strict NSFW Requirement**: Replaced non-adult general anime sites (**Kuronime** and **Otakudesu**) with verified Indonesian adult hentai and JAV platforms (**NontonHentai** on the AnimeStream engine and **JavSubIndo** on RetroTube).
2. **Hanime Separation & Investigation**:
   - `hhanime.tv` is confirmed dead/parked (redirects to domain squatter `ww80.hhanime.tv`).
   - `hanime.tv` uses Astro SSR; `window.AppConfig` proves the `guest.freeanimehentai.net` backend exists, but anonymous video player playback is gated by Cloudflare Turnstile token `0x4AAAAAABsjKGKilqEiCHWo`.
   - `hanime1.me` is a distinct Traditional Chinese site blocked by Cloudflare Managed Challenge (HTTP 403).
3. **Rigorous Evidence Baseline**:
   - **Nekopoi** is the only site with 100% headless end-to-end stream playback verified from this CLI environment: Dean Edwards packer unpacked from StreamPoi embed, yielding a multi-bitrate HLS playlist (`master.m3u8`) whose TS segments respond with `HTTP 206 Partial Content` (`video/MP2T`). Saved in `docs/evidence/nekopoi/`.
   - **IndoHentai** (`cf-mitigated: challenge` 403) and **JAVSubbed** (`103.10.223.15:443 Connection refused`) downgraded to **Unverified** because they cannot be reached headlessly from this network environment and must be verified from the user's local machine or Android WebView.
