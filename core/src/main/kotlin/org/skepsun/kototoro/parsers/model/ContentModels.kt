package org.skepsun.kototoro.parsers.model

enum class ContentType {
    MANGA,
    MANHWA,
    MANHUA,
    HENTAI_MANGA,
    HENTAI_NOVEL,
    HENTAI_VIDEO,
    COMICS,
    VIDEO,
    NOVEL,
    ONE_SHOT,
    DOUJINSHI,
    IMAGE_SET,
    ARTIST_CG,
    GAME_CG,
    OTHER
}

interface ContentSource {
    val name: String
    val locale: String
    val contentType: ContentType
}

enum class ContentRating {
    SAFE,
    SUGGESTIVE,
    ADULT
}

data class ContentTag(
    val title: String,
    val key: String,
    val source: ContentSource
)

enum class ContentState {
    ONGOING,
    FINISHED,
    ABANDONED,
    PAUSED
}

data class ContentExternalTrack(
    @JvmField val url: String,
    @JvmField val lang: String,
    @JvmField val headers: Map<String, String>? = null
)

data class ContentPage(
    @JvmField val id: Long,
    @JvmField val url: String,
    @JvmField val preview: String? = null,
    @JvmField val headers: Map<String, String>? = null,
    @JvmField val externalSubtitleTracks: List<ContentExternalTrack> = emptyList(),
    @JvmField val playbackLabel: String? = null,
    @JvmField val playbackQuality: Int? = null,
    @JvmField val source: ContentSource
)

data class ContentChapter(
    @JvmField val id: Long,
    @JvmField val title: String?,
    @JvmField val number: Float,
    @JvmField val volume: Int = 0,
    @JvmField val url: String,
    @JvmField val scanlator: String? = null,
    @JvmField val uploadDate: Long = 0L,
    @JvmField val branch: String? = null,
    @JvmField val source: ContentSource
)

data class Content(
    @JvmField val id: Long,
    @JvmField val title: String,
    @JvmField val altTitles: Set<String> = emptySet(),
    @JvmField val url: String,
    @JvmField val publicUrl: String,
    @JvmField val rating: Float = -1f,
    @JvmField val contentRating: ContentRating? = ContentRating.ADULT,
    @JvmField val coverUrl: String? = null,
    @JvmField val tags: Set<ContentTag> = emptySet(),
    @JvmField val state: ContentState? = null,
    @JvmField val authors: Set<String> = emptySet(),
    @JvmField val largeCoverUrl: String? = null,
    @JvmField val description: String? = null,
    @JvmField val chapters: List<ContentChapter>? = null,
    @JvmField val source: ContentSource
)

enum class SortOrder {
    POPULARITY,
    UPDATED,
    RATING,
    NEWEST,
    ALPHABETICAL
}

data class ContentListFilter(
    val query: String? = null,
    val tags: Set<ContentTag> = emptySet()
)

data class ContentListFilterCapabilities(
    val isMultipleTagsSupported: Boolean = false,
    val isSearchSupported: Boolean = true
)

data class ContentListFilterOptions(
    val tags: List<ContentTag> = emptyList()
)

class Favicons(val urls: List<String> = emptyList())

