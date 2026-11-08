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

        val preferredQuality = preferences.getString("preferred_quality", "1080p") ?: "1080p"
        val preferredHost = preferences.getString("preferred_host", "")?.takeIf { it.isNotBlank() }
        val referer = response.request.url.toString()
        val filteredUrls = candidateUrls.filter { isVideoCandidateUrl(it) }.distinct()

        val videos = ExtractorRegistry.resolveSequential(
            urls = filteredUrls,
            preferredHost = preferredHost,
            referer = referer,
            client = client
        ).let {
            ExtractorRegistry.deduplicateAndSort(it, preferredQuality = preferredQuality, preferredHost = preferredHost)
        }

        if (videos.isEmpty() && filteredUrls.isNotEmpty()) {
            throw app.kaziyami.core.SourceParseException(
                source = name,
                pageType = "video streams",
                selectorName = config.selectors.playerIframes,
                urlPath = UrlUtils.getPath(response.request.url.toString())
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
