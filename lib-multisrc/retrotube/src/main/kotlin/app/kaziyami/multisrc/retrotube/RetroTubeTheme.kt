package app.kaziyami.multisrc.retrotube

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import app.kaziyami.core.DateParser
import app.kaziyami.core.ExtractorRegistry
import app.kaziyami.core.HtmlText
import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.generic.GenericThemeSource
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.generic.SourceConfig
import okhttp3.Response
import org.jsoup.nodes.Document

open class RetroTubeTheme(
    name: String,
    lang: String,
    baseUrl: String,
    mirrors: List<String> = emptyList(),
    popularPath: String = "/most-viewed/page/%d/",
    latestPath: String = "/latest-updates/page/%d/",
    searchPath: String = "/?s=%s&page=%d",
    customSelectors: SelectorsConfig = SelectorsConfig(),
    customQuirks: QuirksConfig = QuirksConfig(),
    preferences: eu.kanade.tachiyomi.animesource.SourcePreferences = eu.kanade.tachiyomi.animesource.InMemorySourcePreferences()
) : GenericThemeSource(
    SourceConfig(
        name = name,
        lang = lang,
        baseUrl = baseUrl,
        mirrors = mirrors,
        popularPath = popularPath,
        latestPath = latestPath,
        searchPath = searchPath,
        selectors = SelectorsConfig(
            item = customSelectors.item.takeIf { it != "article" } ?: "article.loop-video, .video-item, article.post",
            itemTitle = customSelectors.itemTitle.takeIf { it != "h2, .title, a[title]" } ?: "header.entry-header h2 a, .post-title a, a[title]",
            itemUrl = customSelectors.itemUrl.takeIf { it != "a[href]" } ?: "header.entry-header a[href], .post-thumbnail a[href], a[href]",
            itemThumbnail = customSelectors.itemThumbnail.takeIf { it != "img" } ?: ".post-thumbnail img, img",
            nextPage = customSelectors.nextPage.takeIf { it != ".pagination .next, a.next, .nav-links .next" } ?: ".pagination a.next, .nav-links a.next, a.next",
            detailsTitle = customSelectors.detailsTitle.takeIf { it != "h1.entry-title, .data h1, .infox h1, h1" } ?: "h1.entry-title, .video-details h1, h1",
            detailsThumbnail = customSelectors.detailsThumbnail.takeIf { it != ".poster img, .thumb img, .entry-content img, .cover img" } ?: ".video-player img, meta[property=og:image], .post-thumbnail img",
            detailsDescription = customSelectors.detailsDescription.takeIf { it != ".entry-content p, .synopsis, .desc, .entry-content" } ?: ".entry-content, .video-description, #tab-overview",
            detailsGenre = customSelectors.detailsGenre.takeIf { it != ".genres a, .genre a, a[rel=category], .tag-links a" } ?: ".video-tags a, .post-tags a, a[rel=tag]",
            detailsStatus = customSelectors.detailsStatus,
            detailsAuthor = customSelectors.detailsAuthor.takeIf { it != ".author, .studio" } ?: ".video-actors a, .models a, .video-info a[rel=tag]",
            episodeList = customSelectors.episodeList,
            episodeUrl = customSelectors.episodeUrl,
            episodeName = customSelectors.episodeName,
            episodeDate = customSelectors.episodeDate,
            playerIframes = customSelectors.playerIframes.takeIf { it != "iframe[src], .player-embed iframe, .player iframe" } ?: ".responsive-player iframe, .sourcetabs iframe, iframe[src]"
        ),
        quirks = customQuirks
    ),
    preferences = preferences
) {
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val parts = document.select(".video-parts a, .parts-list a")
        if (parts.isNotEmpty()) {
            return parts.mapIndexed { index, part ->
                val ep = SEpisode.create()
                val href = part.attr("href")
                ep.url = UrlUtils.resolve(baseUrl, href)
                ep.name = HtmlText.clean(part.text()).ifEmpty { "Part ${index + 1}" }
                ep.episode_number = (index + 1).toFloat()
                ep
            }
        }

        // Single video post fallback
        val ep = SEpisode.create()
        ep.url = response.request.url.toString()
        ep.name = "Full Video"
        ep.episode_number = 1.0f

        val dateEl = document.selectFirst(".entry-date, .post-date, time")
        if (dateEl != null) {
            ep.date_upload = DateParser.parse(dateEl.text())
        }

        return listOf(ep)
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val embedUrls = mutableListOf<String>()

        // 1. Sourcetabs (common in RetroTube: <li data-src="url"> or <a data-src="url">)
        document.select(".sourcetabs [data-src], .player-tabs [data-src]").forEach { tab ->
            val src = tab.attr("data-src").trim()
            if (src.isNotEmpty()) embedUrls.add(UrlUtils.resolve(baseUrl, src))
        }

        // 2. Direct iframes
        document.select(config.selectors.playerIframes).forEach { iframe ->
            val src = iframe.attr("src").trim()
            if (src.isNotEmpty()) embedUrls.add(UrlUtils.resolve(baseUrl, src))
        }

        return ExtractorRegistry.extractAll(embedUrls.distinct(), client)
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Press 'Reset' to clear all filters"),
            RetroTubeSortFilter()
        )
    }

    class RetroTubeSortFilter : AnimeFilter.Select<String>(
        "Sort By",
        arrayOf("Recent", "Most Viewed", "Top Rated", "Longest"),
        0
    )
}
