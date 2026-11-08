# Kototoro Source Test Harness Architecture & Analysis

Analysis of Kototoro's "Test All" source test harness merged via PR #578 (*source groups, per-source lock, Test All, extension page fixes*).

---

## 1. Upstream Source Locations & Components

Upstream Kototoro code references:
- **`SourceTester`**: `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/test/SourceTester.kt`
- **`SourceTestModels`**: `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/test/SourceTestModels.kt`
- **`SourceTestViewModel`**: `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/test/SourceTestViewModel.kt`
- **`AniyomiSourceRuntime`**: `source-host/src/main/kotlin/org/skepsun/kototoro/source/host/AniyomiSourceRuntime.kt`

---

## 2. Step-by-Step Test Execution Flow

Kototoro executes up to 7 sequential steps per source (`SourceTestStep` enum):
Timeouts: 20 seconds per step (`SOURCE_TEST_STEP_TIMEOUT_MS = 20_000L`), 90 seconds per source (`SOURCE_TEST_SOURCE_TIMEOUT_MS = 90_000L`). Max concurrency is 10 (`SOURCE_TEST_MAX_PARALLELISM = 10`).

### Step 1: `LIST`
- **Call**: `repo.getList(0, repo.defaultSortOrder, ContentListFilter.EMPTY)`
- **Aniyomi Bridge**: `AniyomiSourceRuntime.getList(sourceName, offset=0, order="POPULARITY", filter=null)` invokes extension method `getPopularAnime(1)`.
- **Verdict**:
  - `PASS`: Resulting `items` list is not empty. Returns count.
  - `FAIL`: Empty list (`source_test_msg_empty`) or thrown exception.
  - If `LIST` fails, subsequent dependent steps (`COVERS`, `DETAILS`, `CHAPTERS`, `PAGES`) are marked `SKIP` (`source_test_msg_prev_failed`).

### Step 2: `COVERS`
- **Sample**: Up to first 3 items (`SOURCE_TEST_COVER_SAMPLE = 3`) from `LIST`.
- **Call**: Calls `client.newCall(repo.createCoverRequest(url)).await()` for each item's `coverUrl`.
- **Verification**: `verifyImage(response)` peeks 64KB (`VERIFY_PEEK_BYTES = 64L * 1024`). Checks `Content-Type: image/*` or decodes bitmap bounds via `BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)`.
- **Verdict**:
  - `PASS`: All sampled covers return HTTP 200 with valid image header/dimensions.
  - `FAIL`: Any sampled cover fails to load or is not a valid image.

### Step 3: `DETAILS`
- **Sample**: Up to first 3 items (`SOURCE_TEST_DETAILS_SAMPLE = 3`) from `LIST`.
- **Call**: `repo.getDetails(item)`.
- **Aniyomi Bridge**: `AniyomiSourceRuntime.getDetails` calls:
  1. `invoke(lease, session, "getAnimeDetails", adapter.copy(original))`
  2. `invoke(lease, session, "getEpisodeList", adapter.copy(original))`
  The tester iterates over the sample until `loaded.chapters.orEmpty().isNotEmpty()`.
- **Verdict**:
  - `PASS`: At least one sampled item returns details with non-empty chapters/episodes.
  - `FAIL`: All sampled items throw exceptions or have empty chapters.

### Step 4: `CHAPTERS`
- **Call**: Evaluates `details.chapters.orEmpty().size`.
- **Verdict**:
  - `PASS`: `count > 0`.
  - `FAIL`: `count == 0` (`source_test_msg_no_chapters`).

### Step 5: `PAGES`
- **Target**: First chapter/episode: `chapter = details.chapters.first()`.
- **Call**: `repo.getPages(target)`.
- **Aniyomi Bridge**: `AniyomiSourceRuntime.getPages` invokes `compatibleVideos(lease, session, episode)`:
  1. Calls `getVideoList(episode)`. If non-empty, calls `resolve(videos)`.
  2. If empty, calls `getHosterList(episode)`. If non-empty, resolves hoster video list.
  3. If both empty, returns `emptyList()`.
- **Stream Verification**:
  - If `pages.isEmpty()`: fails immediately with `source_test_msg_no_pages` (**"Page list is empty"**).
  - If `pages.isNotEmpty()`: For video sources (`repo.source.contentType.isVideo()`, covering `VIDEO` and `HENTAI_VIDEO`), fetches stream response and calls `verifyStream(response)`.
  - `verifyStream(response)` peeks 4KB (`VERIFY_STREAM_PEEK_BYTES = 4L * 1024`) and runs `looksLikeVideoStream(contentType, head)`:
    - `contentType.startsWith("video/") || contentType.startsWith("audio/")`
    - `contentType.contains("mpegurl") || contentType.contains("dash+xml")`
    - head starts with `#EXTM3U` (HLS manifest)
    - head bytes 4..7 == `ftyp` (ISO MP4)
    - head bytes 0..3 == `0x1A 0x45 0xDF 0xA3` (Matroska / WebM)
    - `contentType == "application/octet-stream" || contentType == "binary/octet-stream"`
- **Verdict**:
  - `PASS`: At least 1 video URL returned and stream header passes `looksLikeVideoStream`.
  - `FAIL`: `pages.isEmpty()` ("Page list is empty") or invalid stream header.

### Step 6: `RANDOM`
- **Call**: Never executed. `SourceTester.kt` line 166 records:
  `record(SourceTestStep.RANDOM, skipped(R.string.source_test_msg_unsupported))`
- **Reason**: The parser API has no per-source random capability (only app-level discovery). Permanently `SKIP`.

### Step 7: `SEARCH`
- **Queries**: `SOURCE_TEST_SEARCH_QUERIES = listOf("a", "one")`.
- **Call**: Calls `repo.getList(0, repo.defaultSortOrder, ContentListFilter(query = query))` for each query.
- **Aniyomi Bridge**: Invokes `getSearchAnime(1, query, nativeList)`.
- **Verdict**:
  - `PASS`: Either query `"a"` or `"one"` returns `count > 0`.
  - `FAIL`: Both queries return 0 items (`source_test_msg_search_empty`) or throw.

---

## 3. Cloudflare & Error Classification

In `SourceTester.kt` lines 193-199:
```kotlin
runCatchingCancellable { block() }.getOrElse { e ->
    if (isCloudflareBlocked(e)) {
        StepResult(StepStatus.CLOUDFLARE, text(R.string.source_test_msg_cloudflare))
    } else {
        StepResult(StepStatus.FAIL, describe(e))
    }
}
```
- Cloudflare blocks are identified by inspecting up to 5 levels of causes for `CloudFlareException`.
- All other uncaught exceptions are formatted as `"${e.javaClass.simpleName}: ${e.message}"` (capped at 160 chars).

---

## 4. Export JSON Report Schema

`SourceTestViewModel.exportJson()` emits:
```json
{
  "generated": "2026-11-08T06:10:56.123456Z",
  "sources": [
    {
      "name": "ANIYOMI_<source.id>",
      "status": "pass|fail|cloudflare",
      "steps": {
        "LIST": "pass|fail|skip|cloudflare",
        "COVERS": "pass|fail|skip|cloudflare",
        "DETAILS": "pass|fail|skip|cloudflare",
        "CHAPTERS": "pass|fail|skip|cloudflare",
        "PAGES": "pass|fail|skip|cloudflare",
        "RANDOM": "skip",
        "SEARCH": "pass|fail|skip|cloudflare"
      },
      "messages": {
        "<STEP>": "<Error message if FAIL or CLOUDFLARE>"
      }
    }
  ]
}
```
Overall status:
- If ANY step is `FAIL` -> source status is `fail`.
- If ANY step is `CLOUDFLARE` (and no `FAIL`) -> source status is `cloudflare`.
- If all steps are `PASS` or `SKIP` -> source status is `pass`.

---

## 5. Root Cause Mapping of Reported Device Failures

| Source Name | Source ID | Failing Step | Reported Error | Root Cause |
|---|---|---|---|---|
| **JAVSubbed** | `9192959167101688940` | `LIST`, `SEARCH` | `ConnectException: Failed to connect to javsubbed.net/103.10.223.15:443` | Indonesian ISP DNS sinkhole (`103.10.223.15`). ISP blocks connection on port 443. Requires Android Private DNS on device. |
| **IndoHentai** | `7369497329911250289` | `LIST`, `SEARCH` | `ConnectException: Failed to connect to indohentai.net/103.10.223.15:443` | Same Indonesian ISP DNS sinkhole (`103.10.223.15`). |
| **Hanime** | `2150907185602526076` | `PAGES` | `Page list is empty` | Astro/Nuxt JSON escapes slashes (`https:\/\/`). Plain regex `https?://` failed to match; video list returned empty. Fixed by unescaping slashes and querying video API fallback. |
| **Hanime** | `2150907185602526076` | `SEARCH` | `ConnectException: Failed to connect to guest.freeanimehentai.net/103.10.223.15:443` | Same Indonesian ISP DNS sinkhole on search endpoint. |
| **Nekopoi** | `7073589375897212735` | `DETAILS` | `SourceParseException: could not read the episode list; the site layout may have changed` | Popular list returns single-episode video releases. Page has player embed but no `.episodelist` series container. `GenericThemeSource.episodeListParse` threw exception instead of synthesizing single video episode. |
| **Hentaimama** | `8058423919997530503` | `DETAILS` | `SourceParseException: could not read the episode list; the site layout may have changed` | Same as Nekopoi. Popular list items are direct episode posts (`/episodes/...`). Page has video player but no `#episodes ul.episodios li`. |
| **NontonHentai** | `7400791378172011716` | `PAGES` | `Page list is empty` | Embed iframes had lazy-loaded `data-src` or internal embed player wrapper unhandled by specific extractors, returning empty list. Fixed with `data-src` resolution and generic embed recursive resolver. |

