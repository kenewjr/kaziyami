# Future Expansion Candidate Sources

These candidate sources have been preliminarily researched and satisfy all policy criteria (18+ adult content, zero illegal content, no login/paywall/DRM, verified video hosts). They are documented for future expansion and are **NOT enabled in the active build**.

---

## Indonesian (`id`) Candidates

### 1. JavSubIndo
- **Primary Domain**: `https://javsubindo.com`
- **Engine**: RetroTube (`lib-multisrc:retrotube`)
- **Category**: JAV (Japanese Adult Video) with Indonesian subtitles.
- **Video Hosts**: Streamtape, VOE, DoodStream, Filemoon.
- **Cloudflare Level**: Low (direct browser user-agent passes without challenge).
- **Pros**: Clean SSR layout, rapid updates, fast stream embeds.
- **Cons**: Domain churn every 6-9 months due to regional ISP blocks.

### 2. Ourostream
- **Primary Domain**: `https://ourostream.org`
- **Engine**: AnimeStream (`lib-multisrc:animestream`)
- **Category**: Hentai with Indonesian subtitles.
- **Video Hosts**: LocalStream, HepiDrive, Google Drive mirrors.
- **Cloudflare Level**: Medium (occasional Turnstile check).
- **Pros**: High-resolution 1080p stream options.
- **Cons**: WordPress database occasional high response latency.

---

## English / Multi-language (`en`) Candidates

### 1. HentaiTV
- **Primary Domain**: `https://hentaitv.am`
- **Engine**: DooPlay (`lib-multisrc:dooplay`)
- **Category**: English-subtitled and English-dubbed hentai.
- **Video Hosts**: Mp4Upload, StreamWish, DoodStream.
- **Cloudflare Level**: Low.
- **Pros**: Dual-audio options (Sub/Dub filter available).
- **Cons**: Ad-heavy DOM requires script sanitization.

### 2. JAVSub.co
- **Primary Domain**: `https://javsub.co`
- **Engine**: RetroTube (`lib-multisrc:retrotube`)
- **Category**: JAV with English hardcoded subtitles.
- **Video Hosts**: Streamtape, Filemoon.
- **Cloudflare Level**: Medium.
- **Pros**: High-speed CDN video players.
- **Cons**: Requires custom referer headers for MP4 chunk downloads.
