# Release v1.4.1 — Initial Public Launch

Kaziyami is a privacy-first, resilient adult (NSFW) anime extension repository for Aniyomi and compatible forks.

### Highlights
- **6 Verified Adult Sources**:
  - `Kaziyami: Nekopoi` (id): Indonesian uncensored hentai & JAV with StreamPoi HLS unpacker.
  - `Kaziyami: IndoHentai` (id): Fast card grid hentai catalog with Indonesian subtitles.
  - `Kaziyami: NontonHentai` (id): AnimeStream engine adult releases with multiple HD video options.
  - `Kaziyami: Hentaimama` (en): Extensive English-subbed hentai library on DooPlay engine.
  - `Kaziyami: JAVSubbed` (en): Japanese adult video catalog with English subtitles across fast CDN servers.
  - `Kaziyami: Hanime` (en): High-definition Astro/Vue SSR streaming portal.
- **Defensive Engineering**:
  - Selector drift tolerance with automatic alternate fallbacks.
  - Domain failover and manual user domain overrides.
  - Pure browser network fingerprinting (zero header leakage).
  - Saved bookmarks stored safely as relative URLs.

### Installation URL
Add this repository URL inside Aniyomi (**Settings -> Browse -> Anime extension repos**):
```
https://raw.githubusercontent.com/<username>/kaziyami-extensions/master/repo/repo.json
```
