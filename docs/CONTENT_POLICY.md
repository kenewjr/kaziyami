# Kaziyami Content Safety & Policy Specification

This document details the content safety constraints, automated exclusion layers, and age gate implementation enforced across the Kaziyami extension suite.

---

## 1. Content Boundary & Scope

Kaziyami extensions strictly target adult animation:
- **Permitted**:
  - 2D Adult Animation (Hentai anime, OVAs).
  - 3D/CG Adult Animation (Blender, Source Filmmaker [SFM], MikuMikuDance [MMD], Unreal Engine 3D animation).
- **Prohibited & Filtered**:
  - Minors / Child Sexual Abuse Material (CSAM): Absolute zero tolerance.
  - Live-Action adult content (JAV, pornographic live video, non-animated media).
  - Non-consensual material (rape, guro, snuff, extreme violence).
  - Voyeurism, hidden camera, non-consensual filming.
  - Any material depicting or simulating real persons without consent.

---

## 2. Age Gate Architecture & Host NSFW Management

Adult content gating is handled natively by the host application (Aniyomi and Kototoro):
1. **Manifest NSFW Flag**: Every extension declares `tachiyomi.animeextension.nsfw=1` in its Android manifest.
2. **Host-Level NSFW Toggles**: Both Aniyomi and Kototoro provide global 18+/NSFW toggles in their respective Settings and Sources screens to hide or display adult sources.
3. **Zero Friction**: In-extension manual age switches are omitted to prevent redundant configuration; enabling the host app's NSFW filter grants immediate access to the catalog.

---

## 3. Automated Exclusion Layer (`ContentPolicy.kt`)

A shared filtering engine in `app.kaziyami.core.ContentPolicy` validates all media metadata (titles, descriptions, category tags, author tags) retrieved from upstream providers before presenting items to the host application UI.

### 3.1 Multilingual Blocklist

The filter maintains comprehensive regex pattern matchers across English (`en`), Indonesian (`id`), and Japanese romanization (`ja-Latn`):

1. **Minor / Underage Indicators (`MINOR_PATTERNS`)**:
   - `\bloli\b`, `\blolicon\b`, `\bshota\b`, `\bshotacon\b`
   - `\bunderage\b`, `\bchild\b`, `\bchildren\b`, `\bminor\b`, `\bminors\b`
   - `\bkawaii\s+child\b`, `\bkodomo\b`, `\byoujo\b`, `\bbaby\b`
   - `\banak\s+kecil\b`, `\bbocah\b`, `\bdibawah\s+umur\b`
   - `\bsmp\b`, `\bsd\b` (Indonesian school age classifications)
   - `\bmiddle\s+school\b`, `\belementary\s+school\b`, `\bprimary\s+school\b`
   - `\bpedophil\w*\b`

2. **Non-Consensual & Live-Action Indicators (`NON_CONSENSUAL_AND_LIVE_ACTION_PATTERNS`)**:
   - `\brape\b`, `\bforced\b`, `\bnon-consensual\b`, `\bchikan\b`, `\bmolest\w*\b`
   - `\bguro\b`, `\bsnuff\b`, `\bmutilat\w*\b`, `\btorture\b`, `\bblood\b`
   - `\bperkosa\b`, `\bpaksa\b`, `\bpelecehan\b`
   - `\blive[\s-]?action\b`, `\breal\s+person\b`, `\bjav\b`
   - `\bspycam\b`, `\bhidden\s+cam\b`, `\bvoyeur\b`, `\bkamera\s+tersembunyi\b`, `\bintip\b`

### 3.2 Pipeline Interception

All parsing pipelines intercept and drop matching items:
- **Listing Views** (`popularAnimeParse`, `latestUpdatesParse`, `searchAnimeParse`):
  Evaluated through `ContentPolicy.filterAnimes(animes)`. Dropped items are scrubbed from page results.
- **Details View** (`animeDetailsParse`):
  Evaluated through `ContentPolicy.isSafeAnime(anime)`. Flagged entries throw `SourceParseException("Content omitted: item violates safety policy.")`.
- **Media Representation** (`filterMedia(mediaList)`):
  Unified bridge for Kototoro and Aniyomi items ensuring consistent filtering across all host frontends.

