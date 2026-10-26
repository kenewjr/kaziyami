# Device QA Checklist

Use this checklist when validating Kaziyami extensions on a physical Android device or emulator.

---

## 0. Recommended Test Order

1. **Local Preflight First**:
   - Run `python scripts/preflight.py` (or `.\scripts\preflight.ps1`) on your computer.
   - If ISP/DNS blocking is reported, enable Private DNS on your Android device (**Settings -> Network & internet -> Private DNS -> 'one.one.one.one'**) before testing.
2. **Install ONE Debug Extension First**:
   - Install the easiest baseline extension: `Kaziyami: Hentaimama` (DooPlay base):
     ```bash
     python scripts/install_debug.py --source hentaimama
     ```
   - Tap "Trust" on Aniyomi's untrusted extension prompt.
   - Verify browse popular, latest, search, filters, details, and video playback on at least two servers.
3. **Install Remaining 5 Sources One by One**:
   - Install `NontonHentai` (AnimeStream), `JAVSubbed` (RetroTube), `IndoHentai` (Card Grid), `Nekopoi` (WordPress/StreamPoi), and `Hanime` (Astro SSR).
   - Test each individually.
4. **Final Repository & Signing Path**:
   - Only after all 6 debug extensions pass locally: generate production keystore, configure GitHub secrets, push repo, and test installation through the remote `repo.json` URL.

---

## 1. First-Run Failure Triage Table

| Symptom | Likely Cause | Next Action |
| :--- | :--- | :--- |
| **Nothing loads / Connection refused** | DNS or ISP regional block, or site down. | Verify site reachable in browser. Check Private DNS (DoH) setting on phone. Try alternate mirror in extension settings. |
| **"Cloudflare / Site protection active"** | Cloudflare Turnstile or Managed Challenge. | Tap **Open in WebView** on the error screen, solve the checkmark once, return to app, and refresh. |
| **Empty list with no error message** | Selector drift or site layout update. | Report as a bug. Run `python scripts/preflight.py` to confirm whether site changed layout. |
| **Episodes load, but video playback fails** | Video embed host changed token format or expired token. | Test another server/quality in the player sheet. If all fail, capture redacted log and report extractor bug. |
| **Video plays but stutters or stops** | CDN rate-limiting, buffering, or missing Referer header. | Select lower resolution (e.g. 720p or 480p). Check network connection speed. |
| **Wrong episode numbering or inverted order** | Source ordering quirk or date parser ambiguity. | Toggle "Reverse episode list" in source quirks or report to flip sort order. |

---

## 2. Test Environment Matrix

| App | Version Tested | Android OS | Architecture | Status |
| :--- | :---: | :---: | :---: | :---: |
| **Aniyomi Stable** | 0.15.3 | Android 13 / 14 | arm64-v8a | [ ] Pass / [ ] Fail |
| **Aniyomi Preview** | r7000+ | Android 14 / 15 | arm64-v8a | [ ] Pass / [ ] Fail |
| **Kuukiyomi** | Latest | Android 12 / 13 | arm64-v8a | [ ] Pass / [ ] Fail |
| **Dantotsu** | Latest | Android 13 / 14 | arm64-v8a | [ ] Pass / [ ] Fail |

---

## 3. Per-Source Verification Checklist

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

## 4. Safe Log Pasting & Redaction Guide

When reporting an issue, sanitize all logcat output before submitting:
- **Redact Cookies**: Remove all `Cookie:` and `Set-Cookie:` header values.
- **Redact Tokens**: Remove authorization headers, bearer tokens, and stream signature query parameters (`?token=...`, `&sig=...`).
- **Redact Personal Identifiers**: Remove IP addresses, device identifiers (IMEI/MAC), and user file paths.
- **Keep Technical Details**: Keep HTTP status codes, error stack traces, requested URL paths (e.g. `/anime/slug/`), and extractor names.

---

## 5. Kototoro (v2.3+) Verification Checklist

1. [ ] **Add Repository**: In Kototoro, open **Settings** -> **Extension management** -> **Repositories** -> **Add repository** (+). Select **ANIYOMI** type, enter `https://raw.githubusercontent.com/kenewjr/kaziyami/repo/index.min.json`, and confirm.
2. [ ] **Trust Prompt**: Confirm trust for the repository when the signing key prompt appears.
3. [ ] **Repository Listing**: Verify all 6 extensions display with package-qualified icons and labels formatted as `Kaziyami: <Site>`.
4. [ ] **Installation**: Tap **Install** for each extension. Confirm successful installation via package manager without error.
5. [ ] **Browse Catalog**: Open each source from Explore / Sources list. Confirm popular and latest anime items display with cover images.
6. [ ] **Search & Filters**: Perform search query with filters (genres / sorting). Verify results match and return without exceptions.
7. [ ] **Anime Details**: Open an entry. Verify title, synopsis, status, genres, and chapter/episode list populate accurately.
8. [ ] **Video Playback**: Tap an episode. Verify video stream plays smoothly in Kototoro's player. Test at least two stream hosts / qualities where available (e.g. 1080p, 720p).
9. [ ] **Cloudflare / Turnstile Clearance**: If a challenge prompt occurs, trigger the in-app WebView clearance flow, complete challenge, and verify cookies persist.
10. [ ] **Domain Override**: In source settings, enter a mirror domain from `docs/SOURCES.md`. Confirm catalog updates to fetch via the new domain.
