# Kototoro JAR Plugin Format: Video Feasibility Investigation

**Status**: Verified Negative (Not Possible for Video Sources)  
**Date**: November 2026  
**Reference Codebase**: [Kototoro-app/Kototoro](https://github.com/Kototoro-app/Kototoro) (branch `devel`, Apache-2.0)  

---

## 1. Executive Summary

An investigation was conducted into Kototoro's codebase (`parser-api`, `source-host`, `desktop-player`, and `app`) to evaluate whether the Kototoro JAR Parser Plugin architecture (type `JAR`, entrypoint `ContentParserFactoryKt`) can support video/anime streaming sources.

**Conclusion**: **Kototoro JAR parser repositories CANNOT support video streaming sources.**
Kototoro's `parser-api` is architected exclusively for manga images and web novel text. Video playback in Kototoro is exclusively routed through **Variant A** (Aniyomi Extension APKs) and CloudStream `.cs3` plugins.

Attempting to distribute video sources via Kototoro JAR plugins causes Kototoro to display **"manga is not supported"** or **"This video source is not supported"** (`unsupported_video_source`).

---

## 2. Code Evidence & Citations

### 2.1 The `ContentParser` Interface Lacks Video Streams
In `parser-api/src/main/kotlin/org/skepsun/kototoro/parsers/ContentParser.kt`:
```kotlin
public interface ContentParser {
    public val source: ContentSource
    public suspend fun getList(page: Int, filter: ContentFilter): List<Content>
    public suspend fun getDetails(content: Content): Content
    public suspend fun getChapters(content: Content): List<ContentChapter>
    public suspend fun getPages(chapter: ContentChapter): List<ContentPage>
    public suspend fun getChapterContent(chapter: ContentChapter): NovelChapterContent?
}
```
`ContentParser` has **zero** methods or models for video streams, stream qualities, HLS manifests, MP4 links, HTTP stream headers, or subtitle tracks. It only provides:
- `getPages`: returns `List<ContentPage>` (images for manga reader)
- `getChapterContent`: returns `NovelChapterContent` (HTML/plain text for novel reader)

Although `org.skepsun.kototoro.parsers.model.ContentType` defines enum constants `VIDEO` and `HENTAI_VIDEO`, these values exist only as catalog classification tags; the parser interface itself contains no video playback contract.

### 2.2 Video Playback Architecture in Kototoro
In `app/src/main/kotlin/org/skepsun/kototoro/ui/player/VideoPlayerActivity.kt` and `desktop-player`:
Video playback is bound exclusively to:
1. `org.skepsun.kototoro.aniyomi.AniyomiAnimeRepository` (Aniyomi Extension APKs implementing `eu.kanade.tachiyomi.animesource.AnimeHttpSource`).
2. `com.lagradost.cloudstream3` plugins (`CloudstreamContentRepository`).

When a video source is provided by a `ContentParser` (wrapped in `ParserContentRepository`), Kototoro falls back to calling `repo.getPages(chapter)`, treating page image URLs as video streams, which immediately causes playback failure.

### 2.3 Origin of "This video source is not supported"
In `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/SourceSettingsRoute.kt:332-344`:
```kotlin
if (repository is EmptyContentRepository) {
    sections += SourceSettingsSectionUiState(
        id = "unsupported",
        title = context.getString(R.string.settings),
        rows = listOf(
            SourceSettingsInfoRowUiState(
                id = "unsupported_info",
                title = viewModel.source.getTitle(context),
                summary = context.getString(contentType.getUnsupportedSourceTitleResId()),
            ),
        ),
    )
}
```
When `contentType == ContentType.VIDEO` or `ContentType.HENTAI_VIDEO`, `getUnsupportedSourceTitleResId()` returns string resource `R.string.unsupported_video_source`:
> *"This video source is not supported"*

Because `ContentParser` cannot back a video repository, Kototoro instantiates an `EmptyContentRepository` for the source, rendering the settings screen completely disabled and unplayable.

### 2.4 Origin of "manga is not supported"
In Kototoro's unified repository manager (`UnifiedSourcesViewModel.kt` and `ExtensionRepoService.kt`):
Kototoro categorizes external repositories by capability:
- `ANIYOMI`: anime video streaming (APK)
- `MIHON`: manga reader (APK)
- `JAR`: Kototoro `parser-api` (manga & light novels)
- `TVBOX`: MacCMS / IPTV video aggregation
- `CLOUDSTREAM`: CloudStream video plugins

When a user on the Video tab subscribes to a `JAR` parser repository, Kototoro detects that all packages in the repository declare parsers backed by `ContentParser` (manga/novel), and displays the message:
> *"manga is not supported"*

indicating that the current tab (Video) cannot consume manga parser plugins from a JAR repository.

---

## 3. Decision & Architectural Direction

| Distribution Variant | Feasibility for Video | Action |
|---|---|---|
| **Variant A: Aniyomi Extension (APK)** | **100% Fully Supported** | Retain as the primary and supported format for both Aniyomi and Kototoro. |
| **Variant B: Kototoro JAR Plugin (JAR)** | **Not Possible for Video** | Discontinued. Removed JAR build tasks and `repo/jar/` distribution from repository. |

Users running Kototoro should install Kaziyami extensions via **Variant A** (Aniyomi Extension Repository) by entering `https://raw.githubusercontent.com/kenewjr/kaziyami/repo/index.min.json` into Kototoro under **Explore -> Manage -> Repositories -> ANIYOMI**. Kototoro's built-in `AniyomiExtensionLoader` loads and runs the extensions natively.
