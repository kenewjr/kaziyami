package app.kaziyami.multisrc.kvs

import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import app.kaziyami.core.ContentPolicy
import app.kaziyami.core.DateParser
import app.kaziyami.core.ExtractorRegistry
import app.kaziyami.core.HtmlText
import app.kaziyami.core.LazyImage
import app.kaziyami.core.SourceParseException
import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.generic.GenericThemeSource
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.generic.SourceConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * Multisrc base theme for Kernel Video Sharing (KVS) powered platforms.
 * Handles single video entry modeling, direct MP4 flashvars stream resolution,
 * and standard KVS pagination.
 */
open class KvsTheme(
    name: String,
    lang: String,
    baseUrl: String,
    mirrors: List<String> = emptyList(),
    popularPath: String = "/most-popular/%d/",
    latestPath: String = "/latest-updates/%d/",
    searchPath: String = "/search/%s/?from_videos=%d",
    customSelectors: SelectorsConfig = SelectorsConfig(),
    customQuirks: QuirksConfig = QuirksConfig(),
    customPreferences: SharedPreferences? = null
) : GenericThemeSource(
    config = SourceConfig(
        name = name,
        lang = lang,
        baseUrl = baseUrl,
        mirrors = mirrors,
        popularPath = popularPath,
        latestPath = latestPath,
        searchPath = searchPath,
        selectors = SelectorsConfig(
            item = customSelectors.item.takeIf { it != "article" } ?: ".video-card, .item.thumb, .item, .thumb, div[class*='item']",
            itemAlternates = customSelectors.itemAlternates.ifEmpty {
                listOf(".video-card", ".item.thumb", ".item", ".thumb", "div[class*='item']")
            },
            itemTitle = customSelectors.itemTitle.takeIf { it != "h2, .title, a[title]" } ?: "a.video-card-thumb-wrap img[alt], a.th[title], a[title], img[alt], .title",
            itemTitleAlternates = customSelectors.itemTitleAlternates.ifEmpty {
                listOf("a.video-card-thumb-wrap img[alt]", "a.th[title]", "a[title]", "img[alt]", ".title")
            },
            itemUrl = customSelectors.itemUrl.takeIf { it != "a[href]" } ?: "a.video-card-thumb-wrap, a.th, a[href*='/video/']",
            itemThumbnail = customSelectors.itemThumbnail.takeIf { it != "img" } ?: "img.video-card-thumb, img.thumb, img[data-original], img",
            nextPage = customSelectors.nextPage.takeIf { it.isNotBlank() } ?: ".pagination .item.active + .item a, .pagination .active + a, .pagination a:contains(Next), a[data-parameters*='from']",
            detailsTitle = customSelectors.detailsTitle.takeIf { it != "h1" } ?: "h1, .headline h1, h1.title",
            detailsTitleAlternates = customSelectors.detailsTitleAlternates.ifEmpty {
                listOf("h1", ".headline h1", "h1.title")
            },
            detailsDescription = customSelectors.detailsDescription.takeIf { it != ".description, p.desc" } ?: ".video-details, .description, .item-description, .info",
            detailsGenre = customSelectors.detailsGenre.takeIf { it != ".genre a, .genres a" } ?: "a[href*='/tags/'], a[href*='/categories/']",
            episodeList = customSelectors.episodeList.takeIf { it != "ul.episodes li" } ?: "h1",
            episodeListAlternates = customSelectors.episodeListAlternates.ifEmpty {
                listOf("h1", ".headline h1", ".video-details")
            }
        ),
        quirks = customQuirks
    ),
    customPreferences = customPreferences
) {

    override fun popularAnimeRequest(page: Int): Request {
        val path = if (page == 1) "/most-popular/" else config.popularPath.replace("%d", page.toString())
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page == 1) "/latest-updates/" else config.latestPath.replace("%d", page.toString())
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val path = if (query.isBlank()) {
            if (page == 1) "/most-popular/" else config.popularPath.replace("%d", page.toString())
        } else {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            config.searchPath.replace("%s", encodedQuery).replace("%d", page.toString())
        }
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override fun popularAnimeNextPageSelector(): String =
        ".pagination .item.active + .item a, .pagination .active + a, .pagination a[href*='/2/'], .pagination a[href*='from'], a[data-parameters*='from']"

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()

        // 1. Primary metadata source: JSON-LD VideoObject
        var foundJsonLd = false
        val scriptElements = document.select("script[type='application/ld+json']")
        for (scriptEl in scriptElements) {
            val content = scriptEl.data().ifBlank { scriptEl.html() }
            if (content.contains("VideoObject")) {
                try {
                    val jsonEl = Json { ignoreUnknownKeys = true }.parseToJsonElement(content)
                    val obj = jsonEl as? JsonObject
                    if (obj != null) {
                        val name = (obj["name"] as? JsonPrimitive)?.content
                        val desc = (obj["description"] as? JsonPrimitive)?.content
                        val thumb = when (val t = obj["thumbnailUrl"]) {
                            is JsonArray -> (t.firstOrNull() as? JsonPrimitive)?.content
                            is JsonPrimitive -> t.content
                            else -> null
                        }

                        if (!name.isNullOrBlank()) {
                            anime.title = if (config.quirks.stripTitleBoilerplate) HtmlText.stripBoilerplate(name) else HtmlText.clean(name)
                            anime.description = HtmlText.clean(desc.orEmpty())
                            anime.thumbnail_url = thumb?.let { UrlUtils.resolve(baseUrl, it) }
                            foundJsonLd = true
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Fallback to CSS selectors if JSON-LD is missing or lacks title
        if (!foundJsonLd || anime.title.isBlank()) {
            val titleEl = config.selectors.detailsTitleCandidates.asSequence()
                .mapNotNull { document.selectFirst(it) }
                .firstOrNull()
            val rawTitle = titleEl?.text().orEmpty()
            anime.title = if (config.quirks.stripTitleBoilerplate) HtmlText.stripBoilerplate(rawTitle) else HtmlText.clean(rawTitle)
        }

        if (anime.thumbnail_url.isNullOrBlank()) {
            val thumbEl = document.selectFirst(config.selectors.detailsThumbnail)
            anime.thumbnail_url = LazyImage.extractUrl(thumbEl, baseUrl)
        }

        if (anime.description.isNullOrBlank()) {
            val descEl = document.select(config.selectors.detailsDescription)
            val descText = descEl.toList().filter { it.tagName() != "script" && it.tagName() != "style" }.map { el ->
                if (el.tagName() == "meta") el.attr("content") else el.text()
            }.map { HtmlText.clean(it) }.filter { it.isNotBlank() }.distinct().joinToString("\n\n")
            anime.description = descText
        }

        // Extract genuine genres from tags / categories, excluding navigation elements
        val rawGenres = document.select(config.selectors.detailsGenre)
            .map { HtmlText.clean(it.text()) }
            .filter { it.isNotBlank() && !it.equals("Tags", ignoreCase = true) && it.length < 50 }
            .distinct()
        if (rawGenres.isNotEmpty()) {
            anime.genre = rawGenres.joinToString(", ")
        }

        anime.status = SAnime.COMPLETED

        if (anime.title.isBlank()) {
            throw SourceParseException(
                source = name,
                pageType = "anime details",
                selectorName = config.selectors.detailsTitle,
                urlPath = "Missing title"
            )
        }

        if (!ContentPolicy.isSafeAnime(anime)) {
            throw SourceParseException(
                source = name,
                pageType = "anime details",
                selectorName = "ContentPolicy",
                urlPath = "Prohibited content indicator detected"
            )
        }

        return anime
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val ep = SEpisode.create().apply {
            url = UrlUtils.getPath(response.request.url.toString())
            name = "Full Video"
            episode_number = 1.0f
            val dateEl = document.selectFirst(".released-date, time, .upload-date, .date, .post-date, .time")
            if (dateEl != null) {
                date_upload = DateParser.parse(dateEl.text())
            }
        }
        return listOf(ep)
    }

    override fun videoListParse(response: Response): List<Video> {
        val html = response.body?.string().orEmpty()
        val videos = mutableListOf<Video>()
        val videoHeaders = app.kaziyami.network.BrowserHeaders.mediaHeaders(baseUrl, headers)

        // 1. Primary video_url
        val mainMatch = Regex("""video_url:\s*['"]([^'"]+)['"]""").find(html)
        if (mainMatch != null) {
            val url = UrlUtils.resolve(baseUrl, mainMatch.groupValues[1])
            videos.add(Video(url, "360p (Default)", url, videoHeaders))
        }

        // 2. Alternate resolution streams
        val altSpecs = listOf(
            Pair("480p", Regex("""video_alt_url:\s*['"]([^'"]+)['"]""")),
            Pair("720p", Regex("""video_alt_url2:\s*['"]([^'"]+)['"]""")),
            Pair("1080p", Regex("""video_alt_url3:\s*['"]([^'"]+)['"]""")),
            Pair("4K", Regex("""video_alt_url4:\s*['"]([^'"]+)['"]"""))
        )
        for ((label, regex) in altSpecs) {
            val match = regex.find(html)
            if (match != null) {
                val url = UrlUtils.resolve(baseUrl, match.groupValues[1])
                videos.add(Video(url, label, url, videoHeaders))
            }
        }

        // 3. Fallback: video tags or iframes if flashvars was empty
        if (videos.isEmpty()) {
            val doc = org.jsoup.Jsoup.parse(html, response.request.url.toString())
            doc.select("video source[src], video[src]").forEach { el ->
                val src = el.attr("src").ifEmpty { el.attr("data-src") }
                if (src.isNotBlank() && isVideoCandidateUrl(src)) {
                    val resolved = UrlUtils.resolve(baseUrl, src)
                    videos.add(Video(resolved, "Stream", resolved, videoHeaders))
                }
            }
        }

        if (videos.isEmpty()) {
            val fallback = response.request.url.toString()
            videos.add(Video(fallback, "Stream (Open in WebView if blocked)", fallback, videoHeaders))
        }

        val preferredQuality = preferences.getString("preferred_quality", "1080p") ?: "1080p"
        val preferredHost = preferences.getString("preferred_host", "")?.takeIf { it.isNotBlank() }

        return ExtractorRegistry.deduplicateAndSort(videos, preferredQuality, preferredHost)
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Press 'Reset' to clear all filters"),
            KvsSortFilter()
        )
    }

    open class KvsSortFilter(
        name: String = "Sort by",
        values: Array<String> = arrayOf("Most Popular", "Latest Updates", "Top Rated", "Longest"),
        state: Int = 0
    ) : AnimeFilter.Select<String>(name, values, state)
}
