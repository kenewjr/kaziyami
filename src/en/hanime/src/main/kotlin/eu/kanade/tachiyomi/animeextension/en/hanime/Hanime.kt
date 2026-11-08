package eu.kanade.tachiyomi.animeextension.en.hanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import app.kaziyami.core.DateParser
import app.kaziyami.core.ExtractorRegistry
import app.kaziyami.core.HtmlText
import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.generic.GenericThemeSource
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.generic.SourceConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import java.util.regex.Pattern

class Hanime : GenericThemeSource(
    config = SourceConfig(
        name = "Hanime",
        lang = "en",
        baseUrl = "https://hanime.tv",
        mirrors = listOf("https://hhanime.tv"),
        popularPath = "/browse/trending?page=%d",
        latestPath = "/browse/recent?page=%d",
        searchPath = "/search?q=%s&page=%d",
        selectors = SelectorsConfig(
            item = "a[href*='/videos/hentai/'], a[href*='/hentai/video/'], div.card, a.card, div.video-card",
            itemTitle = "h3, .title, a[title]",
            itemUrl = "a[href]",
            itemThumbnail = "img",
            nextPage = ".pagination a.next, a.next",
            detailsTitle = "h1, .video-details h1, h1.entry-title",
            detailsThumbnail = "meta[property='og:image'], .poster img, img",
            detailsDescription = ".description, .video-description, p",
            detailsGenre = "a[href*='/genre/'], a[href*='/tag/'], .tags a",
            detailsAuthor = ".brand, .studio",
            episodeList = ".playlist-item, a[href*='/videos/hentai/'], a[href*='/hentai/video/']",
            playerIframes = "iframe[src], video source"
        ),
        quirks = QuirksConfig(
            customReferer = "https://hanime.tv/"
        )
    )
) {

    companion object {
        private val M3U8_REGEX = Pattern.compile("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE)
        private val MP4_REGEX = Pattern.compile("""(https?://[^\s"'<>]+\.mp4[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://guest.freeanimehentai.net/api/v11/search_hvs?q=$encoded&page=${(page - 1).coerceAtLeast(0)}"
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val body = response.body?.string().orEmpty()
        if (body.trimStart().startsWith("{")) {
            return try {
                val json = Json.parseToJsonElement(body).jsonObject
                val data = json["data"]?.jsonArray ?: return AnimesPage(emptyList(), false)
                val animes = data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val slug = obj["slug"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: ""
                    val cover = obj["cover_url"]?.jsonPrimitive?.content
                        ?: obj["poster_url"]?.jsonPrimitive?.content.orEmpty()
                    SAnime.create().apply {
                        this.title = HtmlText.clean(name)
                        this.url = "/videos/hentai/$slug"
                        this.thumbnail_url = cover
                    }
                }
                val query = response.request.url.queryParameter("q").orEmpty().lowercase()
                val filtered = if (query.isNotBlank()) {
                    animes.filter { it.title.lowercase().contains(query) }
                } else {
                    animes
                }
                val pageSize = 24
                val pageIndex = (response.request.url.queryParameter("page")?.toIntOrNull() ?: 0)
                val paged = filtered.drop(pageIndex * pageSize).take(pageSize)
                val hasNext = (pageIndex + 1) * pageSize < filtered.size
                AnimesPage(paged, hasNext)
            } catch (_: Exception) {
                AnimesPage(emptyList(), false)
            }
        }

        val doc = org.jsoup.Jsoup.parse(body, baseUrl)
        val (_, elements) = selectFirstMatching(doc, "search_item", config.selectors.itemCandidates)
        if (elements.isEmpty()) {
            if (isEmptyState(doc)) return AnimesPage(emptyList(), false)
            throw app.kaziyami.core.SourceParseException(
                source = name,
                pageType = "search results",
                selectorName = config.selectors.item,
                urlPath = UrlUtils.getPath(response.request.url.toString())
            )
        }
        val animeList = elements.map { searchAnimeFromElement(it) }
        val hasNextPage = searchAnimeNextPageSelector()?.let { selector ->
            doc.selectFirst(selector) != null
        } ?: false
        return AnimesPage(animeList, hasNextPage)
    }

    public override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val playlistItems = document.select(".playlist a, .series-episodes a")
        if (playlistItems.isNotEmpty()) {
            return playlistItems.mapIndexed { index, el ->
                val ep = SEpisode.create()
                val href = el.attr("href")
                ep.url = UrlUtils.resolve(baseUrl, href)
                ep.name = HtmlText.clean(el.text()).ifEmpty { "Episode ${index + 1}" }
                ep.episode_number = (index + 1).toFloat()
                ep
            }
        }

        // Single video release
        val ep = SEpisode.create()
        ep.url = response.request.url.toString()
        ep.name = "Full Episode"
        ep.episode_number = 1.0f

        val dateEl = document.selectFirst(".released-date, time, .upload-date")
        if (dateEl != null) {
            ep.date_upload = DateParser.parse(dateEl.text())
        }

        return listOf(ep)
    }

    public override fun videoListParse(response: Response): List<Video> {
        val body = response.body?.string().orEmpty()
        val videoList = mutableListOf<Video>()

        // Check for embedded HLS m3u8 streams
        val m3u8Matcher = M3U8_REGEX.matcher(body)
        while (m3u8Matcher.find()) {
            val streamUrl = m3u8Matcher.group(1)
            videoList.add(
                Video(
                    url = streamUrl,
                    quality = "HLS 720p/1080p",
                    videoUrl = streamUrl,
                    headers = headers
                )
            )
        }

        // Check for embedded MP4 streams
        val mp4Matcher = MP4_REGEX.matcher(body)
        while (mp4Matcher.find()) {
            val streamUrl = mp4Matcher.group(1)
            videoList.add(
                Video(
                    url = streamUrl,
                    quality = "Direct MP4",
                    videoUrl = streamUrl,
                    headers = headers
                )
            )
        }

        // Also check iframes via ExtractorRegistry
        val doc = org.jsoup.Jsoup.parse(body, baseUrl)
        val iframes = doc.select("iframe[src]").mapNotNull {
            val src = it.attr("src")
            if (src.isNotBlank()) UrlUtils.resolve(baseUrl, src) else null
        }
        videoList.addAll(ExtractorRegistry.extractAll(iframes, client))

        return videoList.distinctBy { it.videoUrl }
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Hanime Filters"),
            HanimeSortFilter()
        )
    }

    class HanimeSortFilter : AnimeFilter.Select<String>(
        "Sort By",
        arrayOf("Trending", "Recent", "Most Views", "Top Rated"),
        0
    )
}
