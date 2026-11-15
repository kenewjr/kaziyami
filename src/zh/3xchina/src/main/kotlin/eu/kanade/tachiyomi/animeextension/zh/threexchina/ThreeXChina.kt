package eu.kanade.tachiyomi.animeextension.zh.threexchina

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import app.kaziyami.core.ExtractorRegistry
import app.kaziyami.core.SourceParseException
import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.retrotube.RetroTubeTheme
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder

class ThreeXChina : RetroTubeTheme(
    name = "3xchina",
    lang = "zh",
    baseUrl = "https://3xchina.page",
    mirrors = listOf("https://3xchina.net"),
    popularPath = "/most-viewed/page/%d/",
    latestPath = "/latest-updates/page/%d/",
    searchPath = "/page/%d/?s=%s",
    customSelectors = SelectorsConfig(
        item = "article.loop-video, article.thumb-block, article.post",
        itemTitle = "header.entry-header a[title], .post-thumbnail a[title], a[title], h2 a",
        itemUrl = "header.entry-header a[href], .post-thumbnail a[href], a[href]",
        itemThumbnail = ".post-thumbnail img, img",
        nextPage = ".pagination a.next, .nav-links a.next, a.next",
        detailsTitle = "h1.entry-title, h1",
        detailsThumbnail = ".video-player img, meta[property='og:image'], .post-thumbnail img",
        detailsDescription = ".entry-content p, .video-description, #tab-overview",
        detailsGenre = ".video-tags a, .post-tags a, a[rel=tag]",
        detailsAuthor = ".video-actors a, .models a",
        playerIframes = ".responsive-player iframe, .responsive-player IFRAME, iframe[src]"
    ),
    customQuirks = QuirksConfig(
        customReferer = "https://3xchina.page/"
    )
) {

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val cleanQuery = query.trim()
        val category = filters.filterIsInstance<ThreeXChinaCategoryFilter>().firstOrNull()?.toSlug().orEmpty()
        val tag = filters.filterIsInstance<ThreeXChinaTagFilter>().firstOrNull()?.toSlug().orEmpty()
        val sort = filters.filterIsInstance<ThreeXChinaSortFilter>().firstOrNull()?.toSlug().orEmpty()

        val path = when {
            cleanQuery.isNotBlank() -> {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                if (page > 1) "/page/$page/?s=$encoded" else "/?s=$encoded"
            }
            tag.isNotEmpty() -> {
                if (page > 1) "/tag/$tag/page/$page/" else "/tag/$tag/"
            }
            category.isNotEmpty() -> {
                if (page > 1) "/category/$category/page/$page/" else "/category/$category/"
            }
            sort.isNotEmpty() -> {
                if (page > 1) "/?filter=$sort&page=$page" else "/?filter=$sort"
            }
            else -> {
                if (page > 1) "/page/$page/" else "/"
            }
        }

        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    public override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val embedUrls = mutableListOf<String>()

        // 1. Sourcetabs (common in RetroTube: <li data-src="url"> or <a data-src="url">)
        document.select(".sourcetabs [data-src], .player-tabs [data-src], .sourcetabs a, .player-tabs a").forEach { tab ->
            val src = tab.attr("data-src").ifEmpty { tab.attr("href") }.trim()
            if (src.isNotEmpty() && !src.startsWith("#")) embedUrls.add(UrlUtils.resolve(baseUrl, src))
        }

        // 2. Direct iframes (handles both iframe and uppercase IFRAME, data-src, lazy-src)
        document.select(".responsive-player iframe, .responsive-player IFRAME, iframe[src], iframe[data-src], iframe[data-lazy-src]").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty {
                iframe.attr("data-lazy-src").ifEmpty {
                    iframe.attr("src").ifEmpty { iframe.attr("SRC") }
                }
            }.trim()
            if (src.isNotEmpty() && !src.startsWith("#")) embedUrls.add(UrlUtils.resolve(baseUrl, src))
        }

        // 3. Direct <video> or <source> elements
        document.select("video source[src], video[src], video source[data-src], video[data-src]").forEach { el ->
            val src = el.attr("data-src").ifEmpty { el.attr("src") }.trim()
            if (src.isNotEmpty()) embedUrls.add(UrlUtils.resolve(baseUrl, src))
        }

        // 4. JavaScript embedded stream links (mp4 / m3u8)
        val html = document.html()
        val streamMatcher = java.util.regex.Pattern.compile("""(?:source|file|videoUrl)\s*[:=]\s*['"](https?://[^'"]+\.(?:mp4|m3u8)[^'"]*)['"]""", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html)
        while (streamMatcher.find()) {
            val stream = streamMatcher.group(1) ?: continue
            embedUrls.add(stream)
        }

        val candidateUrls = embedUrls.filter { isVideoCandidateUrl(it) }.distinct()
        val referer = response.request.url.toString()
        val preferredQuality = preferences.getString("preferred_quality", "1080p") ?: "1080p"
        val preferredHost = preferences.getString("preferred_host", "")?.takeIf { it.isNotBlank() }

        val videos = try {
            ExtractorRegistry.resolveQualities(
                urls = candidateUrls,
                preferredHost = preferredHost,
                referer = referer,
                client = client
            ).let {
                ExtractorRegistry.deduplicateAndSort(it, preferredQuality = preferredQuality, preferredHost = preferredHost)
            }
        } catch (_: Exception) {
            emptyList()
        }

        if (videos.isNotEmpty()) {
            return videos
        }

        // Fallback: only use candidateUrls that end with .mp4 or .m3u8, or known embed host
        val directCandidate = candidateUrls.firstOrNull { it.contains(".mp4") || it.contains(".m3u8") }
        if (directCandidate != null) {
            return listOf(
                Video(
                    url = directCandidate,
                    quality = "3xchina Stream",
                    videoUrl = directCandidate,
                    headers = app.kaziyami.network.BrowserHeaders.mediaHeaders(referer, headers)
                )
            )
        }

        if (candidateUrls.isEmpty()) {
            throw SourceParseException(
                "Kaziyami: $name video stream tidak ditemukan atau dilindungi Cloudflare. Buka di WebView (⋮) untuk memutar."
            )
        }

        val fallback = candidateUrls.first()
        if (fallback.contains(".mp4") || fallback.contains(".m3u8") || fallback.contains("embed") || fallback.contains("play")) {
            return listOf(
                Video(
                    url = fallback,
                    quality = "3xchina Stream",
                    videoUrl = fallback,
                    headers = app.kaziyami.network.BrowserHeaders.mediaHeaders(referer, headers)
                )
            )
        }

        throw SourceParseException(
            "Kaziyami: $name video stream tidak ditemukan atau dilindungi Cloudflare. Buka di WebView (⋮) untuk memutar."
        )
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("排序 (Sorting Order)"),
            ThreeXChinaSortFilter(),
            AnimeFilter.Separator(),
            AnimeFilter.Header("分類 (Category)"),
            ThreeXChinaCategoryFilter(),
            AnimeFilter.Separator(),
            AnimeFilter.Header("標籤 (Tag)"),
            ThreeXChinaTagFilter()
        )
    }

    class ThreeXChinaSortFilter : AnimeFilter.Select<String>(
        "排序 (Sort By)",
        SORTS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = SORTS[state].slug
    }

    class ThreeXChinaCategoryFilter : AnimeFilter.Select<String>(
        "分類 (Category)",
        CATEGORIES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = CATEGORIES[state].slug
    }

    class ThreeXChinaTagFilter : AnimeFilter.Select<String>(
        "標籤 (Tag)",
        TAGS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = TAGS[state].slug
    }

    companion object {
        data class Option(val name: String, val slug: String)

        val SORTS = arrayOf(
            Option("預設 (Default)", ""),
            Option("最新發佈 (Recent)", "latest"),
            Option("觀看最多 (Most Viewed)", "most-viewed"),
            Option("最受歡迎 (Popular)", "popular"),
            Option("片長最長 (Longest)", "longest")
        )

        val CATEGORIES = arrayOf(
            Option("全部分類 (All)", ""),
            Option("Chinese AV (國產AV)", "chinese-av"),
            Option("Chinese Sex (國產自拍)", "chinese-sex")
        )

        val TAGS = arrayOf(
            Option("全部標籤 (All)", ""),
            Option("China AV (國產AV)", "china-av"),
            Option("JVID", "jvid"),
            Option("91Porn (91系列)", "91porn"),
            Option("MD Series (麻豆傳媒)", "md-series"),
            Option("SWAG", "swag"),
            Option("Big Tits (巨乳)", "big-tits"),
            Option("Cuckold (綠帽)", "cuckold"),
            Option("Historical (古裝)", "historical"),
            Option("3P 多人", "3p"),
            Option("4P 多人", "4p")
        )
    }
}

