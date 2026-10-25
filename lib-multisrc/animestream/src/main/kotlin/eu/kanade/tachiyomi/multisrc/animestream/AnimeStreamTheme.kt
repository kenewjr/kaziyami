package eu.kanade.tachiyomi.multisrc.animestream

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.core.ExtractorRegistry
import eu.kanade.tachiyomi.lib.core.UrlUtils
import eu.kanade.tachiyomi.multisrc.generic.GenericThemeSource
import eu.kanade.tachiyomi.multisrc.generic.QuirksConfig
import eu.kanade.tachiyomi.multisrc.generic.SelectorsConfig
import eu.kanade.tachiyomi.multisrc.generic.SourceConfig
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.Base64

open class AnimeStreamTheme(
    name: String,
    lang: String,
    baseUrl: String,
    mirrors: List<String> = emptyList(),
    popularPath: String = "/anime/?status=&type=&order=popular&page=%d",
    latestPath: String = "/anime/?status=&type=&order=update&page=%d",
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
            item = customSelectors.item.takeIf { it != "article" } ?: ".animposx, .listupd article, .animepost, div.bsx",
            itemTitle = customSelectors.itemTitle.takeIf { it != "h2, .title, a[title]" } ?: ".title, h4, h2, a[title]",
            itemUrl = customSelectors.itemUrl,
            itemThumbnail = customSelectors.itemThumbnail,
            nextPage = customSelectors.nextPage.takeIf { it != ".pagination .next, a.next, .nav-links .next" } ?: ".pagination .next, a.next, .hpage .r",
            detailsTitle = customSelectors.detailsTitle.takeIf { it != "h1.entry-title, .data h1, .infox h1, h1" } ?: "h1.entry-title, .infox h1, h1",
            detailsThumbnail = customSelectors.detailsThumbnail.takeIf { it != ".poster img, .thumb img, .entry-content img, .cover img" } ?: ".thumb img, .poster img, .entry-content img",
            detailsDescription = customSelectors.detailsDescription.takeIf { it != ".entry-content p, .synopsis, .desc, .entry-content" } ?: ".entry-content p, .synopsis, .desc, .sinopsis",
            detailsGenre = customSelectors.detailsGenre.takeIf { it != ".genres a, .genre a, a[rel=category], .tag-links a" } ?: ".genxed a, .genre-info a, .genres a",
            detailsStatus = customSelectors.detailsStatus.takeIf { it != ".status, span:contains(Status)" } ?: ".spe span:contains(Status), .status",
            detailsAuthor = customSelectors.detailsAuthor.takeIf { it != ".author, .studio" } ?: ".spe span:contains(Studio), .studio",
            episodeList = customSelectors.episodeList.takeIf { it != ".episodelist ul li, ul.episodes li, .eplister ul li, .episode-list li" } ?: ".eplister ul li, .episodelist ul li, ul.episodes li",
            episodeUrl = customSelectors.episodeUrl,
            episodeName = customSelectors.episodeName.takeIf { it != ".epl-num, .ep-title, a" } ?: ".epl-num, .epl-title, a",
            episodeDate = customSelectors.episodeDate.takeIf { it != ".epl-date, .date, .time" } ?: ".epl-date, .date",
            playerIframes = customSelectors.playerIframes.takeIf { it != "iframe[src], .player-embed iframe, .player iframe" } ?: ".player-embed iframe, iframe[src], .video-content iframe"
        ),
        quirks = customQuirks
    ),
    preferences = preferences
) {
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoUrls = mutableListOf<String>()

        // Direct iframes
        document.select(config.selectors.playerIframes).forEach {
            val src = it.attr("src")
            if (src.isNotBlank()) videoUrls.add(UrlUtils.resolve(baseUrl, src))
        }

        // Server option mirrors (Base64 or HTML iframe inside select/option value)
        document.select("select.mirror option, .mobius select option").forEach { option ->
            val value = option.attr("value").trim()
            if (value.isNotEmpty()) {
                val decoded = tryDecodeBase64(value) ?: value
                val iframeSrc = extractIframeSrc(decoded)
                if (iframeSrc != null) {
                    videoUrls.add(UrlUtils.resolve(baseUrl, iframeSrc))
                } else if (value.startsWith("http://") || value.startsWith("https://")) {
                    videoUrls.add(value)
                }
            }
        }

        return ExtractorRegistry.extractAll(videoUrls.distinct(), client)
    }

    private fun tryDecodeBase64(text: String): String? {
        return try {
            val clean = text.replace("\\s".toRegex(), "")
            val bytes = Base64.getDecoder().decode(clean)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractIframeSrc(html: String): String? {
        if (!html.contains("<iframe", ignoreCase = true)) return null
        val doc = Jsoup.parse(html)
        return doc.selectFirst("iframe[src]")?.attr("src")
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Press 'Reset' to clear all filters"),
            AnimeStreamSortFilter(),
            AnimeStreamStatusFilter()
        )
    }

    class AnimeStreamSortFilter : AnimeFilter.Select<String>(
        "Sort By",
        arrayOf("Default", "Popular", "Latest Update", "A-Z", "Rating"),
        0
    )

    class AnimeStreamStatusFilter : AnimeFilter.Select<String>(
        "Status",
        arrayOf("All", "Ongoing", "Completed"),
        0
    )
}
