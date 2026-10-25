package eu.kanade.tachiyomi.animeextension.en.hanime

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.InMemorySourcePreferences
import eu.kanade.tachiyomi.animesource.SourcePreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.core.DateParser
import eu.kanade.tachiyomi.lib.core.ExtractorRegistry
import eu.kanade.tachiyomi.lib.core.HtmlText
import eu.kanade.tachiyomi.lib.core.UrlUtils
import eu.kanade.tachiyomi.multisrc.generic.GenericThemeSource
import eu.kanade.tachiyomi.multisrc.generic.QuirksConfig
import eu.kanade.tachiyomi.multisrc.generic.SelectorsConfig
import eu.kanade.tachiyomi.multisrc.generic.SourceConfig
import okhttp3.Response
import java.util.regex.Pattern

class Hanime(
    preferences: SourcePreferences = InMemorySourcePreferences()
) : GenericThemeSource(
    config = SourceConfig(
        name = "Hanime",
        lang = "en",
        baseUrl = "https://hanime.tv",
        mirrors = listOf("https://hhanime.tv"),
        popularPath = "/browse/trending?page=%d",
        latestPath = "/browse/recent?page=%d",
        searchPath = "/search?q=%s&page=%d",
        selectors = SelectorsConfig(
            item = "a[href*='/hentai/video/'], div.card, a.card, div.video-card",
            itemTitle = "h3, .title, a[title]",
            itemUrl = "a[href]",
            itemThumbnail = "img",
            nextPage = ".pagination a.next, a.next",
            detailsTitle = "h1, .video-details h1",
            detailsThumbnail = "meta[property='og:image'], .poster img, img",
            detailsDescription = ".description, .video-description, p",
            detailsGenre = "a[href*='/genre/'], a[href*='/tag/'], .tags a",
            detailsAuthor = ".brand, .studio",
            episodeList = ".playlist-item, a[href*='/hentai/video/']",
            playerIframes = "iframe[src], video source"
        ),
        quirks = QuirksConfig(
            customReferer = "https://hanime.tv/"
        )
    ),
    preferences = preferences
) {

    companion object {
        private val M3U8_REGEX = Pattern.compile("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE)
        private val MP4_REGEX = Pattern.compile("""(https?://[^\s"'<>]+\.mp4[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
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

    override fun videoListParse(response: Response): List<Video> {
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
