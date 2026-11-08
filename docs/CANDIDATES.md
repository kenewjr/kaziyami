# Candidate Sources & Research Evaluation (Phase 14)

This document records the discovery, network probes, CMS classification, content policy audit, and architectural decisions for candidate video sources targeting 2D and 3D adult animation (global `en`/multi or Indonesian `id`).

---

## 1. Selected Production Sources

### 1.1 RuleVid (`en`)
- **Domain**: `https://rulevid.com`
- **CMS / Engine**: Kernel Video Sharing (KVS) -> `lib-multisrc:kvs`
- **Category**: 100% 3D & 2D Adult Animation (SFM, Blender, MMD, 3D hentai, Overwatch/Apex/Genshin animations). Zero live-action.
- **Probe Results**:
  - Listing & Search: Clean SSR markup with standard pagination.
  - Video Delivery: Direct CDN MP4 stream URLs extracted from inline flashvars (`video_url: '...'`, `video_alt_url: '...'`).
  - Stream Verification: HTTP 200 OK (Content-Length: ~67 MB, `video/mp4`).
  - Anti-Bot: Cloudflare Low; browser User-Agent passes cleanly without challenges.
- **Status**: **PRODUCTION ACTIVE** (Aniyomi ID: `3619818370138619659L`, Kototoro ID: `6315209008681740874L`).

### 1.2 Rule34Video (`en`)
- **Domain**: `https://rule34video.com`
- **CMS / Engine**: Kernel Video Sharing (KVS) -> `lib-multisrc:kvs`
- **Category**: Dedicated 3D/2D Rule34 adult animation catalog (SFM, Blender, 3D CGI, western/anime animations). Zero live-action.
- **Probe Results**:
  - Listing & Search: High-density video cards with responsive pagination.
  - Video Delivery: Direct MP4 extraction with multi-resolution quality profiles (`360p`, `480p`, `720p`, `1080p`, `4K`).
  - Stream Verification: HTTP 200 OK (Content-Length: ~5 MB chunk, `video/mp4`).
  - Anti-Bot: Standard Cloudflare CDN; normal User-Agent passes cleanly.
- **Status**: **PRODUCTION ACTIVE** (Aniyomi ID: `9212790518101043860L`, Kototoro ID: `6540785455483770072L`).

---

## 2. Backup Sources (Documented for Future Iteration)

### 2.1 HentaiHaven
- **Domain**: `https://hentaihaven.xxx` / `https://hentaihaven.red`
- **Category**: 2D hentai anime.
- **Evaluation**: Main catalogue pages load cleanly, but player iframe (`streamani` / `autoembed`) serves Cloudflare Turnstile challenge. Feasible with WebView challenge resolution, held as backup.

### 2.2 SFMCompile
- **Domain**: `https://sfmcompile.club`
- **Category**: 3D SFM & Blender animations.
- **Evaluation**: Origin protected by Sucuri Cloudproxy (`HTTP 307` temporary redirect cookie gate). Requires session cookie persistence, held as backup.

---

## 3. Rejected Candidates

| Candidate | Domain | Rejection Reason |
| :--- | :--- | :--- |
| **HentaiMoon** | `hentai-moon.com` | Origin server down (`WinError 10061: Connection refused`). |
| **Hentaisea** | `hentaisea.com` | Content safety violation: site tags include underage indicators violating `docs/CONTENT_POLICY.md`. |
| **HentaiIndo** | `hentaiindo.com` | `DNS_PROBE_FINISHED_NXDOMAIN` (domain expired / dead). |
| **HentaiPoi** | `hentaipoi.com` | SSL certificate handshake failure (`ERR_SSL_PROTOCOL_ERROR`). |
| **Anitoki** | `anitoki.com` | Inactive WordPress installation; zero streaming embeds available. |
| **hanime1.me** | `hanime1.me` | Out of scope: Chinese-language platform, Cloudflare Managed Challenge. |
| **3xchina** | `3xchina.net` | Out of scope: Chinese-language focus, live-action content. |
| **JavSubIndo** | `javsubindo.com` | Out of scope: Live-action JAV content (Phase 14 mandate is 2D/3D animation only). |
| **JAVSub.co** | `javsub.co` | Out of scope: Live-action JAV content. |
