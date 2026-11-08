package eu.kanade.tachiyomi.animeextension.zh.hanime1

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
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
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import java.util.regex.Pattern

class Hanime1 : GenericThemeSource(
    config = SourceConfig(
        name = "Hanime1",
        lang = "zh",
        baseUrl = "https://hanime1.me",
        mirrors = listOf("https://hanime1.me"),
        popularPath = "/search?sort=本日排行&page=%d",
        latestPath = "/search?sort=最新上市&page=%d",
        searchPath = "/search?query=%s&page=%d",
        selectors = SelectorsConfig(
            item = ".search-result-items > div, div[class^=video-item-container], div.home-rows-videos-div, .card",
            itemTitle = ".home-rows-videos-title, .video-item-title, h4, h5, .caption, a[title]",
            itemUrl = "a[href*='watch?v=']",
            itemThumbnail = "img[src], img[data-src]",
            nextPage = ".pagination a.next, a.next, a[rel=next]",
            detailsTitle = "#shareBtn-title, h1, .video-details-wrapper h4",
            detailsThumbnail = "meta[property='og:image'], img.video-cover",
            detailsDescription = "div[class=video-details-wrapper] .video-caption-text, meta[name=description], .description",
            detailsGenre = "div.video-tags-wrapper a, a[href*='search?genre='], a[href*='search?tag=']",
            detailsAuthor = "div.video-details-wrapper a[href*='search?brand='], #video-artist-name",
            episodeList = ".related-watch-wrap a[href*='watch?v='], .video-playlist-wrapper a[href*='watch?v=']",
            playerIframes = "video#player source[src], video#player, iframe[src]"
        ),
        quirks = QuirksConfig(
            customReferer = "https://hanime1.me/"
        )
    )
) {

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        checkAgeConfirmed()
        val cleanQuery = query.trim()
        val genre = filters.filterIsInstance<Hanime1GenreFilter>().firstOrNull()?.toSlug().orEmpty()
        val sort = filters.filterIsInstance<Hanime1SortFilter>().firstOrNull()?.toSlug().orEmpty()
        val tag = filters.filterIsInstance<Hanime1TagFilter>().firstOrNull()?.toSlug().orEmpty()

        val params = mutableListOf<String>()
        if (cleanQuery.isNotEmpty()) {
            params.add("query=" + URLEncoder.encode(cleanQuery, "UTF-8"))
        }
        if (genre.isNotEmpty()) {
            params.add("genre=" + URLEncoder.encode(genre, "UTF-8"))
        }
        if (sort.isNotEmpty()) {
            params.add("sort=" + URLEncoder.encode(sort, "UTF-8"))
        }
        if (tag.isNotEmpty()) {
            params.add("tag=" + URLEncoder.encode(tag, "UTF-8"))
        }
        if (page > 1) {
            params.add("page=$page")
        }

        val path = if (params.isNotEmpty()) {
            "/search?" + params.joinToString("&")
        } else {
            if (page > 1) "/search?sort=最新上市&page=$page" else "/search?sort=最新上市"
        }

        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    public override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val relatedLinks = document.select(".related-watch-wrap a[href*='watch?v='], .video-playlist-wrapper a[href*='watch?v=']")
        if (relatedLinks.isNotEmpty()) {
            return relatedLinks.mapIndexed { index, el ->
                val ep = SEpisode.create()
                val href = el.attr("href")
                ep.url = UrlUtils.resolve(baseUrl, href)
                ep.name = HtmlText.clean(el.text()).ifEmpty { "Episode ${index + 1}" }
                ep.episode_number = (index + 1).toFloat()
                ep
            }
        }

        // Single video fallback
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
        val unescapedBody = body.replace("\\/", "/")
        val videoList = mutableListOf<Video>()

        // 1. Direct <video id="player"> or <source> elements
        val doc = org.jsoup.Jsoup.parse(unescapedBody, baseUrl)
        val sources = doc.select("video#player source[src], video[src] source[src], video#player, video[src]")
        for (srcEl in sources) {
            val src = srcEl.attr("src").trim()
            if (src.isNotBlank()) {
                val fullUrl = UrlUtils.resolve(baseUrl, src)
                val quality = srcEl.attr("size").takeIf { it.isNotBlank() }?.let { "${it}p" } ?: "Hanime1 720p/1080p"
                videoList.add(Video(fullUrl, quality, fullUrl, headers))
            }
        }

        // 2. Embedded M3U8 regex
        val m3u8Matcher = Pattern.compile("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE).matcher(unescapedBody)
        while (m3u8Matcher.find()) {
            val streamUrl = m3u8Matcher.group(1) ?: continue
            videoList.add(Video(streamUrl, "Hanime1 HLS (1080p)", streamUrl, headers))
        }

        // 3. Embedded MP4 regex
        val mp4Matcher = Pattern.compile("""(https?://[^\s"'<>]+\.mp4[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE).matcher(unescapedBody)
        while (mp4Matcher.find()) {
            val streamUrl = mp4Matcher.group(1) ?: continue
            videoList.add(Video(streamUrl, "Hanime1 Direct MP4", streamUrl, headers))
        }

        // 4. Extractor registry iframes
        val iframes = doc.select("iframe[src]").mapNotNull {
            val src = it.attr("src")
            if (src.isNotBlank()) UrlUtils.resolve(baseUrl, src) else null
        }
        videoList.addAll(ExtractorRegistry.extractAll(iframes, client))

        val deduped = videoList.distinctBy { it.videoUrl }
        if (deduped.isEmpty()) {
            val epUrl = response.request.url.toString()
            return listOf(
                Video(
                    url = epUrl,
                    quality = "Open in WebView (Cloudflare Turnstile)",
                    videoUrl = epUrl,
                    headers = headers
                )
            )
        }
        return deduped
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Hanime1 繁體中文過濾器"),
            Hanime1SortFilter(),
            Hanime1GenreFilter(),
            Hanime1TagFilter()
        )
    }

    class Hanime1SortFilter : AnimeFilter.Select<String>(
        "排序 (Sort By)",
        SORTS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = SORTS[state].slug
    }

    class Hanime1GenreFilter : AnimeFilter.Select<String>(
        "分類 (Genre)",
        GENRES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = GENRES[state].slug
    }

    class Hanime1TagFilter : AnimeFilter.Select<String>(
        "標籤 (Tag)",
        TAGS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = TAGS[state].slug
    }

    companion object {
        data class Option(val name: String, val slug: String)

        val SORTS = arrayOf(
            Option("最新上市 (New Arrival)", "最新上市"),
            Option("最新上傳 (New Upload)", "最新上傳"),
            Option("本日排行 (Daily Ranking)", "本日排行"),
            Option("本週排行 (Weekly Ranking)", "本週排行"),
            Option("本月排行 (Monthly Ranking)", "本月排行"),
            Option("觀看次數 (View Count)", "觀看次數"),
            Option("讚好比例 (Like Ratio)", "讚好比例"),
            Option("他們在看 (Trending)", "他們在看"),
            Option("時長最長 (Longest)", "時長最長")
        )

        val GENRES = arrayOf(
            Option("全部 (All)", ""),
            Option("裏番 (Hentai)", "裏番"),
            Option("泡麵番 (Short Anime)", "泡麵番"),
            Option("Motion Anime", "Motion Anime"),
            Option("3DCG", "3DCG"),
            Option("2.5D", "2.5D"),
            Option("2D動畫", "2D動畫"),
            Option("AI生成", "AI生成"),
            Option("MMD", "MMD"),
            Option("Cosplay", "Cosplay")
        )

        val TAGS = arrayOf(
            Option("全部標籤 (All)", ""),
            Option("無碼 (Uncensored)", "無碼"),
            Option("AI解碼 (AI Decoded)", "AI解碼"),
            Option("中文字幕 (Chinese Sub)", "中文字幕"),
            Option("中文配音 (Chinese Dub)", "中文配音"),
            Option("同人作品 (Fan Video)", "同人作品"),
            Option("近親 (Incest)", "近親"),
            Option("姐 (Elder Sister)", "姐"),
            Option("妹 (Younger Sister)", "妹"),
            Option("母 (Mother)", "母"),
            Option("女兒 (Daughter)", "女兒"),
            Option("處女 (Virgin)", "處女"),
            Option("御姐 (Older Sister)", "御姐"),
            Option("熟女 (MILF)", "熟女"),
            Option("人妻 (Married Woman)", "人妻"),
            Option("巨乳 (Big Breasts)", "巨乳"),
            Option("貧乳 (Flat Chest)", "貧乳"),
            Option("黑絲 (Black Stockings)", "黑絲"),
            Option("催眠 (Hypnosis)", "催眠"),
            Option("異世界 (Isekai)", "異世界"),
            Option("純愛 (Pure Love)", "純愛"),
            Option("NTR (Netorare)", "NTR"),
            Option("內射 (Creampie)", "內射"),
            Option("口交 (Blowjob)", "口交"),
            Option("後背位 (Doggystyle)", "後背位")
        )
    }
}
