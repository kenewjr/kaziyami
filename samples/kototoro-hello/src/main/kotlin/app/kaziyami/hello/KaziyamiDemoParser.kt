package app.kaziyami.hello

import okhttp3.Headers
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.ContentParser
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.config.ContentSourceConfig
import org.skepsun.kototoro.parsers.model.*

/**
 * Minimal sample parser demonstrating the verified Kototoro ContentParser contract.
 * Serves static 2 titles and 2 episodes without external network reliance.
 */
class KaziyamiDemoParser(
    override val source: ContentSource,
    val context: ContentLoaderContext,
) : ContentParser {

    override val availableSortOrders: Set<SortOrder> = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)
    override val filterCapabilities: ContentListFilterCapabilities =
        ContentListFilterCapabilities(isMultipleTagsSupported = false, isSearchSupported = true)
    override val config: ContentSourceConfig by lazy { context.getConfig(source) }
    override val domain: String = "demo.kaziyami.local"
    override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain(domain)

    override suspend fun getList(offset: Int, order: SortOrder, filter: ContentListFilter): List<Content> {
        if (offset >= 2) return emptyList()
        val titles = listOf(
            Content(
                id = 1001L,
                title = "Kaziyami Demo Series 1",
                altTitles = setOf("Demo 1"),
                url = "/anime/demo-1",
                publicUrl = "https://$domain/anime/demo-1",
                rating = 0.95f,
                contentRating = ContentRating.ADULT,
                coverUrl = "https://$domain/covers/1.jpg",
                tags = setOf(ContentTag("Hentai", "hentai", source), ContentTag("HD", "hd", source)),
                state = ContentState.FINISHED,
                authors = setOf("Kaziyami Studios"),
                description = "Demonstration title 1 for Kototoro JAR format verification.",
                source = source
            ),
            Content(
                id = 1002L,
                title = "Kaziyami Demo Series 2",
                altTitles = setOf("Demo 2"),
                url = "/anime/demo-2",
                publicUrl = "https://$domain/anime/demo-2",
                rating = 0.90f,
                contentRating = ContentRating.ADULT,
                coverUrl = "https://$domain/covers/2.jpg",
                tags = setOf(ContentTag("Hentai", "hentai", source)),
                state = ContentState.ONGOING,
                authors = setOf("Kaziyami Studios"),
                description = "Demonstration title 2 for Kototoro JAR format verification.",
                source = source
            )
        )
        val q = filter.query
        return if (!q.isNullOrBlank()) {
            titles.filter { it.title.contains(q, ignoreCase = true) }
        } else {
            titles
        }
    }

    override suspend fun getDetails(manga: Content): Content {
        val baseId = manga.id * 10
        val episodes = listOf(
            ContentChapter(
                id = baseId + 1,
                title = "Episode 1: Awakening",
                number = 1.0f,
                volume = 0,
                url = "https://$domain/stream/${manga.id}/ep1.mp4",
                scanlator = "Kaziyami",
                uploadDate = 1700000000000L,
                source = source
            ),
            ContentChapter(
                id = baseId + 2,
                title = "Episode 2: Climax",
                number = 2.0f,
                volume = 0,
                url = "https://$domain/stream/${manga.id}/ep2.mp4",
                scanlator = "Kaziyami",
                uploadDate = 1700086400000L,
                source = source
            )
        )
        return manga.copy(chapters = episodes)
    }

    override suspend fun getPages(chapter: ContentChapter): List<ContentPage> {
        return listOf(
            ContentPage(
                id = chapter.id * 100 + 1,
                url = chapter.url,
                preview = null,
                headers = mapOf("Referer" to "https://$domain/"),
                playbackLabel = "1080p - Direct",
                playbackQuality = 1080,
                source = source
            )
        )
    }

    override suspend fun getPageUrl(page: ContentPage): String = page.url

    override suspend fun getFilterOptions(): ContentListFilterOptions = ContentListFilterOptions(
        tags = listOf(ContentTag("Hentai", "hentai", source), ContentTag("HD", "hd", source))
    )

    override suspend fun getFavicons(): Favicons = Favicons(listOf("https://$domain/favicon.ico"))

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        keys.add(configKeyDomain)
    }

    override fun getRequestHeaders(): Headers = Headers.Builder()
        .add("User-Agent", "Kaziyami-Demo/1.0")
        .build()
}
