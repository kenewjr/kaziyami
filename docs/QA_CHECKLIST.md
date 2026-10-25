# Device QA Checklist

Use this checklist when validating Kaziyami extensions on a physical Android device or emulator.

---

## 1. Test Environment Matrix

| App | Version Tested | Android OS | Architecture | Status |
| :--- | :---: | :---: | :---: | :---: |
| **Aniyomi Stable** | 0.15.3 | Android 13 / 14 | arm64-v8a | [ ] Pass / [ ] Fail |
| **Aniyomi Preview** | r7000+ | Android 14 / 15 | arm64-v8a | [ ] Pass / [ ] Fail |
| **Kuukiyomi** | Latest | Android 12 / 13 | arm64-v8a | [ ] Pass / [ ] Fail |
| **Dantotsu** | Latest | Android 13 / 14 | arm64-v8a | [ ] Pass / [ ] Fail |

---

## 2. Per-Source Verification Checklist

Repeat these steps for each of the 6 sources:
- `Kaziyami: Nekopoi` (`id`)
- `Kaziyami: IndoHentai` (`id`)
- `Kaziyami: NontonHentai` (`id`)
- `Kaziyami: Hentaimama` (`en`)
- `Kaziyami: JAVSubbed` (`en`)
- `Kaziyami: Hanime` (`en`)

### Step-by-Step Verification
1. [ ] **Repository Setup**: Add repository URL in **Settings -> Browse -> Anime extension repos**. Confirm repository metadata loads.
2. [ ] **Branding & Label**: Confirm extension appears in the list with label `Kaziyami: <Site>` and original Kaziyami icon.
3. [ ] **Installation**: Tap **Install**. Confirm Android package installer installs extension without trust warnings or signature conflicts.
4. [ ] **Browse Popular**: Open source -> browse **Popular** tab. Confirm titles, thumbnails, and pagination load smoothly.
5. [ ] **Browse Latest**: Open **Latest** tab. Verify newest updates display in chronological order.
6. [ ] **Search**: Enter search query (e.g. `overflow`, `itadaki`). Confirm results return correct matching titles.
7. [ ] **Filters**: Open filter sheet -> select a genre and sort order -> tap filter. Confirm filtered catalog matches criteria.
8. [ ] **Details View**: Tap an entry -> verify title, cover image, status, synopsis, and studio metadata populate accurately.
9. [ ] **Episode List**: Verify episode list order is descending (Episode 12, 11, ... 1) with correct numbering.
10. [ ] **Video Playback**: Tap an episode -> verify video quality / server selection sheet appears -> play on at least two qualities or hosts (e.g. 1080p, 720p).
11. [ ] **Cloudflare / WebView**: If a protection challenge appears, tap **Open in WebView** -> complete challenge -> return to app -> reload page.
12. [ ] **Domain Override**: Open extension settings -> enter custom domain or choose alternate mirror -> verify catalog fetches through new domain.
13. [ ] **Bookmark Migration**: Add entry to library -> change extension domain override -> reopen entry -> verify episodes still play without recreating bookmark.
14. [ ] **Version Update**: After pushing a version bump (`extVersionCode` + 1), refresh repository -> verify **Update** button appears -> update installs cleanly.

---

## 3. Safe Log Pasting & Redaction Guide

When reporting an issue, sanitize all logcat output before submitting:
- **Redact Cookies**: Remove all `Cookie:` and `Set-Cookie:` header values.
- **Redact Tokens**: Remove authorization headers, bearer tokens, and stream signature query parameters (`?token=...`, `&sig=...`).
- **Redact Personal Identifiers**: Remove IP addresses, device identifiers (IMEI/MAC), and user file paths.
- **Keep Technical Details**: Keep HTTP status codes, error stack traces, requested URL paths (e.g. `/anime/slug/`), and extractor names.
