package app.kaziyami.adapter.kototoro

import app.kaziyami.engine.*
import okhttp3.Headers
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.ContentParser
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.config.ContentSourceConfig
import org.skepsun.kototoro.parsers.model.*

/**
 * Adapter bridging any platform-neutral [KaziyamiSource] into Kototoro's [ContentParser] contract.
 */
class KototoroParserAdapter(
    val kaziyamiSource: KaziyamiSource,
    override val source: ContentSource,
    val context: ContentLoaderContext
) : ContentParser {

    override val availableSortOrders: Set<SortOrder> = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)
    override val filterCapabilities: ContentListFilterCapabilities =
        ContentListFilterCapabilities(isMultipleTagsSupported = false, isSearchSupported = true)
    override val config: ContentSourceConfig by lazy { context.getConfig(source) }

    override val domain: String
        get() = kaziyamiSource.baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')

    override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain(domain)

    override suspend fun getList(offset: Int, order: SortOrder, filter: ContentListFilter): List<Content> {
        val pageNumber = offset + 1
        val result = when {
            !filter.query.isNullOrBlank() -> kaziyamiSource.search(pageNumber, filter.query)
            order == SortOrder.UPDATED -> kaziyamiSource.latest(pageNumber)
            else -> kaziyamiSource.popular(pageNumber)
        }
        return result.items.map { it.toKototoroContent(source, domain) }
    }

    override suspend fun getDetails(manga: Content): Content {
        val media = kaziyamiSource.details(manga.url)
        val episodes = kaziyamiSource.episodes(manga.url)
        val chapters = episodes.mapIndexed { index, ep ->
            ContentChapter(
                id = (manga.id * 1000) + index + 1,
                title = ep.name,
                number = if (ep.episodeNumber > 0f) ep.episodeNumber else (index + 1).toFloat(),
                volume = 0,
                url = ep.url,
                scanlator = null,
                uploadDate = ep.dateUpload,
                branch = null,
                source = source
            )
        }
        return manga.copy(
            title = if (media.title.isNotBlank()) media.title else manga.title,
            description = media.description.takeIf { it.isNotBlank() } ?: manga.description,
            coverUrl = media.coverUrl.takeIf { it.isNotBlank() } ?: manga.coverUrl,
            tags = media.genres.map { ContentTag(it, it.lowercase(), source) }.toSet(),
            chapters = chapters
        )
    }

    override suspend fun getPages(chapter: ContentChapter): List<ContentPage> {
        val videos = kaziyamiSource.videos(chapter.url)
        return videos.mapIndexed { index, video ->
            ContentPage(
                id = (chapter.id * 100) + index + 1,
                url = if (video.videoUrl.isNotBlank()) video.videoUrl else video.url,
                preview = null,
                headers = video.headers.takeIf { it.isNotEmpty() },
                externalSubtitleTracks = video.subtitles.map { ContentExternalTrack(it.url, it.lang) },
                playbackLabel = video.quality.takeIf { it.isNotBlank() },
                playbackQuality = extractQualityNumber(video.quality),
                source = source
            )
        }
    }

    override suspend fun getPageUrl(page: ContentPage): String = page.url

    override suspend fun getFilterOptions(): ContentListFilterOptions = ContentListFilterOptions()

    override suspend fun getFavicons(): Favicons = Favicons(listOf("https://$domain/favicon.ico"))

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        keys.add(configKeyDomain)
    }

    override fun getRequestHeaders(): Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        .build()

    companion object {
        fun KaziyamiMedia.toKototoroContent(source: ContentSource, domain: String): Content {
            val numericId = (id.hashCode().toLong() and 0x7FFFFFFFL)
            return Content(
                id = if (numericId != 0L) numericId else 1000L,
                title = title,
                altTitles = emptySet(),
                url = url,
                publicUrl = if (url.startsWith("http")) url else "https://$domain${if (url.startsWith("/")) "" else "/"}$url",
                rating = -1f,
                contentRating = ContentRating.ADULT,
                coverUrl = coverUrl.takeIf { it.isNotBlank() },
                tags = genres.map { ContentTag(it, it.lowercase(), source) }.toSet(),
                state = when (status) {
                    1 -> ContentState.ONGOING
                    2 -> ContentState.FINISHED
                    else -> null
                },
                description = description.takeIf { it.isNotBlank() },
                source = source
            )
        }

        private fun extractQualityNumber(quality: String): Int? {
            val match = Regex("(\\d{3,4})p?").find(quality)
            return match?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}

