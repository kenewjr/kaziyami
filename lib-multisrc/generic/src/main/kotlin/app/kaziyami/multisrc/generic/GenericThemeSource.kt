package app.kaziyami.multisrc.generic

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import app.kaziyami.core.DateParser
import app.kaziyami.core.EpisodeNumberParser
import app.kaziyami.core.ExtractorRegistry
import app.kaziyami.core.HtmlText
import app.kaziyami.core.LazyImage
import app.kaziyami.core.MirrorManager
import app.kaziyami.core.UrlUtils
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Universal declarative base class for anime extension sources.
 * Automates URL normalization, selector drift detection, relative path storage,
 * episode deduplication, and mirror failover.
 */
open class GenericThemeSource(
    val config: SourceConfig,
    val preferences: eu.kanade.tachiyomi.animesource.SourcePreferences = eu.kanade.tachiyomi.animesource.InMemorySourcePreferences()
) : ParsedAnimeHttpSource(), eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource {

    override val name: String = config.name
    override val lang: String = config.lang
    override val supportsLatest: Boolean = true

    val mirrorManager = MirrorManager(
        defaultBaseUrl = config.baseUrl,
        mirrors = config.mirrors,
        preferences = { getDomainPreference() }
    )

    override val baseUrl: String
        get() = mirrorManager.getBaseUrl()

    open fun getDomainPreference(): String? {
        return preferences.getString("preferred_domain", "").takeIf { it.isNotBlank() }
    }

    override fun setupPreferenceScreen(screen: eu.kanade.tachiyomi.animesource.SourcePreferences) {
        val mirrorsList = (listOf(config.baseUrl) + config.mirrors).distinct().toTypedArray()
        screen.addListPreference(
            key = "preferred_domain",
            title = "Preferred domain",
            summary = "Select domain to use for browsing and streams",
            entries = mirrorsList,
            entryValues = mirrorsList,
            defaultValue = config.baseUrl
        )
        screen.addListPreference(
            key = "preferred_quality",
            title = "Preferred quality",
            summary = "Choose preferred stream resolution",
            entries = arrayOf("1080p", "720p", "480p", "360p"),
            entryValues = arrayOf("1080p", "720p", "480p", "360p"),
            defaultValue = "1080p"
        )
        screen.addSwitchPreference(
            key = "show_raw_titles",
            title = "Show raw titles",
            summary = "Keep original untranslated titles when available",
            defaultValue = false
        )
    }

    // ============================= Popular Anime =============================

    override fun popularAnimeRequest(page: Int): Request {
        val path = String.format(config.popularPath, page)
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url)
    }

    override fun popularAnimeSelector(): String = config.selectors.item

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val elements = document.select(popularAnimeSelector())

        if (elements.isEmpty()) {
            if (isEmptyState(document)) {
                return AnimesPage(emptyList(), false)
            }
            throw IllegalStateException(
                "Selector drift detected on ${response.request.url}: " +
                    "main item selector '${popularAnimeSelector()}' returned 0 elements."
            )
        }

        val animeList = elements.map { popularAnimeFromElement(it) }
        val hasNextPage = popularAnimeNextPageSelector()?.let { selector ->
            document.selectFirst(selector) != null
        } ?: false

        return AnimesPage(animeList, hasNextPage)
    }

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val titleEl = element.selectFirst(config.selectors.itemTitle)
        val title = titleEl?.attr("title")?.takeIf { it.isNotBlank() }
            ?: titleEl?.text()?.takeIf { it.isNotBlank() }
            ?: element.selectFirst("a[title]")?.attr("title")
            ?: element.selectFirst("img")?.attr("alt")
            ?: ""

        anime.title = if (config.quirks.stripTitleBoilerplate) {
            HtmlText.stripBoilerplate(title)
        } else {
            HtmlText.clean(title)
        }

        val linkEl = element.selectFirst(config.selectors.itemUrl) ?: element.selectFirst("a[href]")
        val href = linkEl?.attr("href") ?: ""
        anime.url = UrlUtils.resolve(baseUrl, href)

        val imgEl = element.selectFirst(config.selectors.itemThumbnail)
        anime.thumbnail_url = LazyImage.extractUrl(imgEl, baseUrl)
        return anime
    }

    override fun popularAnimeNextPageSelector(): String? = config.selectors.nextPage

    // ============================= Latest Updates =============================

    override fun latestUpdatesRequest(page: Int): Request {
        val path = String.format(config.latestPath, page)
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url)
    }

    override fun latestUpdatesSelector(): String = config.selectors.item

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val elements = document.select(latestUpdatesSelector())

        if (elements.isEmpty()) {
            if (isEmptyState(document)) {
                return AnimesPage(emptyList(), false)
            }
            throw IllegalStateException(
                "Selector drift detected on ${response.request.url}: " +
                    "latest updates selector '${latestUpdatesSelector()}' returned 0 elements."
            )
        }

        val animeList = elements.map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.selectFirst(selector) != null
        } ?: false

        return AnimesPage(animeList, hasNextPage)
    }

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = config.selectors.nextPage

    // ============================= Search & Filters =============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val path = String.format(config.searchPath, encodedQuery, page)
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url)
    }

    override fun searchAnimeSelector(): String = config.selectors.item

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val elements = document.select(searchAnimeSelector())

        if (elements.isEmpty()) {
            if (isEmptyState(document)) {
                return AnimesPage(emptyList(), false)
            }
            throw IllegalStateException(
                "Selector drift detected on ${response.request.url}: " +
                    "search selector '${searchAnimeSelector()}' returned 0 elements."
            )
        }

        val animeList = elements.map { searchAnimeFromElement(it) }
        val hasNextPage = searchAnimeNextPageSelector()?.let { selector ->
            document.selectFirst(selector) != null
        } ?: false

        return AnimesPage(animeList, hasNextPage)
    }

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String? = config.selectors.nextPage

    // ============================= Anime Details =============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        return GET(UrlUtils.resolve(baseUrl, anime.url))
    }

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        val titleEl = document.selectFirst(config.selectors.detailsTitle)
        val title = titleEl?.text() ?: ""
        anime.title = if (config.quirks.stripTitleBoilerplate) {
            HtmlText.stripBoilerplate(title)
        } else {
            HtmlText.clean(title)
        }

        val thumbEl = document.selectFirst(config.selectors.detailsThumbnail)
        anime.thumbnail_url = LazyImage.extractUrl(thumbEl, baseUrl)

        val descEl = document.select(config.selectors.detailsDescription)
        val descText = descEl.joinToString("\n\n") { it.text() }
        anime.description = HtmlText.clean(descText)

        val genres = document.select(config.selectors.detailsGenre).map { HtmlText.clean(it.text()) }
        if (genres.isNotEmpty()) {
            anime.genre = genres.joinToString(", ")
        }

        val statusText = document.selectFirst(config.selectors.detailsStatus)?.text() ?: ""
        anime.status = when {
            statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
            statusText.contains("Completed", ignoreCase = true) || statusText.contains("Tamat", ignoreCase = true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }

        val authorEl = document.selectFirst(config.selectors.detailsAuthor)
        anime.author = authorEl?.text()?.let { HtmlText.clean(it) }

        return anime
    }

    // ============================= Episode List =============================

    override fun episodeListRequest(anime: SAnime): Request {
        return GET(UrlUtils.resolve(baseUrl, anime.url))
    }

    override fun episodeListSelector(): String = config.selectors.episodeList

    override fun episodeFromElement(element: Element): SEpisode {
        val ep = SEpisode.create()
        val linkEl = element.selectFirst(config.selectors.episodeUrl) ?: element.selectFirst("a[href]")
        val href = linkEl?.attr("href") ?: ""
        ep.url = UrlUtils.resolve(baseUrl, href)

        val nameEl = element.selectFirst(config.selectors.episodeName) ?: linkEl
        val rawName = nameEl?.text()?.takeIf { it.isNotBlank() } ?: "Episode"
        ep.name = HtmlText.clean(rawName)
        ep.episode_number = EpisodeNumberParser.parse(ep.name)

        val dateEl = element.selectFirst(config.selectors.episodeDate)
        if (dateEl != null) {
            ep.date_upload = DateParser.parse(dateEl.text())
        }

        return ep
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val episodes = super.episodeListParse(response)
        // Deduplicate episodes by URL
        val deduped = episodes.distinctBy { it.url }
        val ordered = if (config.quirks.episodesReversed) {
            deduped.reversed()
        } else {
            deduped
        }
        // Stable sort newest first (descending by episode number)
        return ordered.sortedByDescending { it.episode_number }
    }

    // ============================= Video List =============================

    override fun videoListRequest(episode: SEpisode): Request {
        return GET(UrlUtils.resolve(baseUrl, episode.url))
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val iframeUrls = document.select(config.selectors.playerIframes)
            .mapNotNull { element: Element ->
                val src = element.attr("src")
                if (src.isNotBlank()) src else null
            }
            .map { UrlUtils.resolve(baseUrl, it) }

        val preferredQuality = preferences.getString("preferred_quality", "1080p")
        return ExtractorRegistry.resolveAll(iframeUrls, client = client).let {
            ExtractorRegistry.deduplicateAndSort(it, preferredQuality = preferredQuality)
        }
    }

    override fun videoFromElement(element: Element): Video {
        throw UnsupportedOperationException("videoFromElement is not used in GenericThemeSource")
    }

    override fun videoUrlParse(document: Document): String {
        throw UnsupportedOperationException("videoUrlParse is not used in GenericThemeSource")
    }

    override fun videoListSelector(): String {
        throw UnsupportedOperationException("videoListSelector is not used in GenericThemeSource")
    }

    // ============================= Filters =============================

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Press 'Reset' to clear all filters"),
            GenericSortFilter()
        )
    }

    open class GenericSortFilter(
        name: String = "Sort",
        values: Array<String> = arrayOf("Default", "Popular", "Latest", "A-Z", "Rating"),
        state: Int = 0
    ) : AnimeFilter.Select<String>(name, values, state)

    /**
     * Checks if a document contains explicit empty-state indicators rather than selector drift.
     */
    open fun isEmptyState(document: Document): Boolean {
        val body = document.body() ?: return true
        val bodyText = body.text().lowercase().trim()
        if (bodyText.isEmpty()) return true
        return bodyText.contains("no results") ||
            bodyText.contains("no videos") ||
            bodyText.contains("no anime") ||
            bodyText.contains("nothing found") ||
            bodyText.contains("not found") ||
            bodyText.contains("tidak ada") ||
            bodyText.contains("ditemukan") ||
            bodyText.contains("0 results") ||
            document.selectFirst(".empty, .not-found, .notfound, .no-results, .error-404, .search-empty") != null
    }
}
