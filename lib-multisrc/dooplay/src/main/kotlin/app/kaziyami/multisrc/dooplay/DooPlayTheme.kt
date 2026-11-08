package app.kaziyami.multisrc.dooplay

import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.asJsoup
import app.kaziyami.core.ExtractorRegistry
import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.generic.GenericThemeSource
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.generic.SourceConfig
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

open class DooPlayTheme(
    name: String,
    lang: String,
    baseUrl: String,
    mirrors: List<String> = emptyList(),
    popularPath: String = "/trending/page/%d/",
    latestPath: String = "/tvshows/page/%d/",
    searchPath: String = "/?s=%s&page=%d",
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
            item = customSelectors.item.takeIf { it != "article" } ?: "article.item, .items article, div.post",
            itemTitle = customSelectors.itemTitle.takeIf { it != "h2, .title, a[title]" } ?: ".data h3 a, h3 a, .data h3, h2, a[title]",
            itemUrl = customSelectors.itemUrl,
            itemThumbnail = customSelectors.itemThumbnail,
            nextPage = customSelectors.nextPage,
            detailsTitle = customSelectors.detailsTitle.takeIf { it != "h1.entry-title, .data h1, .infox h1, h1" } ?: "h1, .data h1",
            detailsThumbnail = customSelectors.detailsThumbnail.takeIf { it != ".poster img, .thumb img, .entry-content img, .cover img" } ?: ".poster img, .thumb img",
            detailsDescription = customSelectors.detailsDescription.takeIf { it != ".entry-content p, .synopsis, .desc, .entry-content" } ?: ".wp-content p, #info .wp-content, .desc p",
            detailsGenre = customSelectors.detailsGenre.takeIf { it != ".genres a, .genre a, a[rel=category], .tag-links a" } ?: ".sgenres a, .genres a",
            detailsStatus = customSelectors.detailsStatus,
            detailsAuthor = customSelectors.detailsAuthor,
            episodeList = customSelectors.episodeList.takeIf { it != ".episodelist ul li, ul.episodes li, .eplister ul li, .episode-list li" } ?: "#episodes ul.episodios li, .episodios li",
            episodeUrl = customSelectors.episodeUrl,
            episodeName = customSelectors.episodeName.takeIf { it != ".epl-num, .ep-title, a" } ?: ".episodiotitle a, .numerando",
            episodeDate = customSelectors.episodeDate.takeIf { it != ".epl-date, .date, .time" } ?: ".date",
            playerIframes = customSelectors.playerIframes.takeIf { it != "iframe[src], .player-embed iframe, .player iframe" } ?: ".playex iframe, .source-box iframe, iframe.metaframe, iframe[src]"
        ),
        quirks = customQuirks
    ),
    customPreferences = customPreferences
) {
    public override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val candidateUrls = mutableListOf<String>()

        // 1. Direct page iframes
        document.select(config.selectors.playerIframes).forEach { element ->
            val src = element.attr("data-src").ifEmpty {
                element.attr("data-lazy-src").ifEmpty {
                    element.attr("src")
                }
            }
            if (src.isNotBlank()) candidateUrls.add(UrlUtils.resolve(baseUrl, src))
        }

        // 2. DooPlay AJAX player tabs (#playeroptionsul li)
        val playerTabs = document.select("#playeroptionsul li[data-post][data-nume]")
        for (tab in playerTabs) {
            val postId = tab.attr("data-post")
            val nume = tab.attr("data-nume")
            val type = tab.attr("data-type").ifEmpty { "tv" }

            try {
                val ajaxUrl = UrlUtils.resolve(baseUrl, "/wp-admin/admin-ajax.php")
                val requestBody = FormBody.Builder()
                    .add("action", "doo_player_ajax")
                    .add("post", postId)
                    .add("nume", nume)
                    .add("type", type)
                    .build()

                val ajaxRequest = Request.Builder()
                    .url(ajaxUrl)
                    .post(requestBody)
                    .header("Referer", response.request.url.toString())
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()

                client.newCall(ajaxRequest).execute().use { ajaxResponse ->
                    if (ajaxResponse.isSuccessful) {
                        val body = ajaxResponse.body?.string().orEmpty()
                        val ajaxDoc = Jsoup.parse(body)
                        ajaxDoc.select("iframe[src], iframe[data-src]").forEach { element ->
                            val src = element.attr("data-src").ifEmpty { element.attr("src") }
                            if (src.isNotBlank()) candidateUrls.add(UrlUtils.resolve(baseUrl, src))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. Modern DooPlay player tabs (get_player_contents, .dt-mi-tabs a, #post_report)
        val postId = document.selectFirst("#post_report input[name=post_id], input[name=post_id]")?.attr("value")
            ?: Regex("""a:\s*['"](\d+)['"]""").find(document.html())?.groupValues?.get(1)
            ?: Regex("""post-(\d+)""").find(document.html())?.groupValues?.get(1)

        val modernTabs = document.select(".dt-mi-tabs a, [class*='dt-mi'] a, #playeroptions a")
        val optionIndices = if (modernTabs.isNotEmpty()) {
            modernTabs.mapNotNull {
                it.attr("href").replace("#option-", "").toIntOrNull()
            }.distinct()
        } else if (!postId.isNullOrBlank()) {
            listOf(1, 2, 3)
        } else {
            emptyList()
        }

        if (!postId.isNullOrBlank()) {
            val ajaxUrl = UrlUtils.resolve(baseUrl, "/wp-admin/admin-ajax.php")
            for (optIndex in optionIndices) {
                try {
                    val requestBody = FormBody.Builder()
                        .add("action", "get_player_contents")
                        .add("a", postId)
                        .add("i", optIndex.toString())
                        .build()

                    val ajaxRequest = Request.Builder()
                        .url(ajaxUrl)
                        .post(requestBody)
                        .header("Referer", response.request.url.toString())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                    client.newCall(ajaxRequest).execute().use { ajaxResponse ->
                        if (ajaxResponse.isSuccessful) {
                            val rawBody = ajaxResponse.body?.string().orEmpty()
                            val unescapedBody = rawBody
                                .replace("\\\"", "\"")
                                .replace("\\/", "/")
                                .replace("&#038;", "&")
                                .replace("&amp;", "&")
                            val ajaxDoc = Jsoup.parse(unescapedBody)
                            ajaxDoc.select("iframe[src], iframe[data-src]").forEach { element ->
                                val src = element.attr("data-src").ifEmpty { element.attr("src") }
                                    .trim('"', '\'', '\\', ' ')
                                if (src.isNotBlank()) candidateUrls.add(UrlUtils.resolve(baseUrl, src))
                            }
                            val srcRegex = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                            srcRegex.findAll(unescapedBody).forEach { m ->
                                val src = m.groupValues[1].trim('"', '\'', '\\', ' ')
                                if (src.isNotBlank()) candidateUrls.add(UrlUtils.resolve(baseUrl, src))
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // 4. Resolve dt_embed wrappers (hentaimama.io/?dt_embed=...)
        val directStreamUrls = mutableListOf<String>()
        val filteredEmbeds = candidateUrls.filter { isVideoCandidateUrl(it) }.distinct()
        for (candidate in filteredEmbeds) {
            val lower = candidate.lowercase()
            if (lower.contains("dt_embed") || lower.contains("embed=rtmp") || lower.contains("newjav.php") || lower.contains("/embed/")) {
                try {
                    val embedReq = Request.Builder()
                        .url(candidate)
                        .header("Referer", response.request.url.toString())
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                        .build()
                    client.newCall(embedReq).execute().use { embedRes ->
                        if (embedRes.isSuccessful) {
                            val embedBody = embedRes.body?.string().orEmpty().replace("\\/", "/")
                            val fileRegex = Regex("""["']?(?:file|src)["']?\s*:\s*["']([^"']+\.(?:mp4|m3u8)[^"']*)["']""", RegexOption.IGNORE_CASE)
                            fileRegex.findAll(embedBody).forEach { m ->
                                directStreamUrls.add(m.groupValues[1])
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        candidateUrls.addAll(directStreamUrls)

        val preferredQuality = preferences.getString("preferred_quality", "1080p") ?: "1080p"
        val preferredHost = preferences.getString("preferred_host", "")?.takeIf { it.isNotBlank() }
        val referer = response.request.url.toString()
        val filteredUrls = candidateUrls.filter { isVideoCandidateUrl(it) }.distinct()

        val videos = try {
            ExtractorRegistry.resolveSequential(
                urls = filteredUrls,
                preferredHost = preferredHost,
                referer = referer,
                client = client
            ).let {
                ExtractorRegistry.deduplicateAndSort(it, preferredQuality = preferredQuality, preferredHost = preferredHost)
            }
        } catch (_: Exception) {
            emptyList()
        }

        if (videos.isEmpty()) {
            val fallbackUrl = filteredUrls.firstOrNull { isVideoCandidateUrl(it) } ?: response.request.url.toString()
            return listOf(
                Video(
                    url = fallbackUrl,
                    quality = "Stream (Open in WebView if blocked)",
                    videoUrl = fallbackUrl,
                    headers = okhttp3.Headers.Builder().set("Referer", referer).build()
                )
            )
        }

        return videos
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Press 'Reset' to clear all filters"),
            DooPlaySortFilter(),
            DooPlayTypeFilter()
        )
    }

    class DooPlaySortFilter : AnimeFilter.Select<String>(
        "Sort By",
        arrayOf("Default", "Trending", "Rating", "Title", "Date"),
        0
    )

    class DooPlayTypeFilter : AnimeFilter.Select<String>(
        "Type",
        arrayOf("All", "TV Shows", "Movies"),
        0
    )
}
