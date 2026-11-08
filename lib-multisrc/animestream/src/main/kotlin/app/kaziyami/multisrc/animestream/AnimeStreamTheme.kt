package app.kaziyami.multisrc.animestream

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
    customPreferences = customPreferences
) {
    public override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoUrls = mutableListOf<String>()

        // Direct iframes (check data-src, data-lazy-src, src)
        document.select(config.selectors.playerIframes).forEach {
            val src = it.attr("data-src").ifEmpty {
                it.attr("data-lazy-src").ifEmpty {
                    it.attr("src")
                }
            }
            if (src.isNotBlank()) videoUrls.add(UrlUtils.resolve(baseUrl, src))
        }

        // Direct video/source elements
        document.select("video source[src], video[src], video source[data-src], video[data-src]").forEach { el ->
            val src = el.attr("data-src").ifEmpty { el.attr("src") }
            if (src.isNotBlank()) videoUrls.add(UrlUtils.resolve(baseUrl, src))
        }

        // Server option mirrors (Base64 or HTML iframe inside select/option value)
        document.select("select.mirror option, .mobius select option, select[name=server] option, .player_servers option, .player-options option").forEach { option ->
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

        val preferredQuality = preferences.getString("preferred_quality", "1080p") ?: "1080p"
        val preferredHost = preferences.getString("preferred_host", "")?.takeIf { it.isNotBlank() }
        val referer = response.request.url.toString()
        val filteredUrls = videoUrls.filter { isVideoCandidateUrl(it) }.distinct()
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
        val iframe = doc.selectFirst("iframe") ?: return null
        return iframe.attr("data-src").ifEmpty { iframe.attr("src") }.takeIf { it.isNotBlank() }
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
