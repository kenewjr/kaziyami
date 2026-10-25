package eu.kanade.tachiyomi.animeextension.id.indohentai

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.InMemorySourcePreferences
import eu.kanade.tachiyomi.animesource.SourcePreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import app.kaziyami.multisrc.generic.GenericThemeSource
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.generic.SourceConfig

class IndoHentai(
    preferences: SourcePreferences = InMemorySourcePreferences()
) : GenericThemeSource(
    config = SourceConfig(
        name = "IndoHentai",
        lang = "id",
        baseUrl = "https://indohentai.net",
        mirrors = listOf("https://indohentai.site", "https://indohentai.org"),
        popularPath = "/populer/page/%d/",
        latestPath = "/terbaru/page/%d/",
        searchPath = "/?s=%s&page=%d",
        selectors = SelectorsConfig(
            item = "div.pf-card, article.post, div.post, article.type-post",
            itemTitle = ".pf-title, h2 a, h2, a[title]",
            itemUrl = "a[href]",
            itemThumbnail = "img",
            nextPage = ".pagination a.next, a.next",
            detailsTitle = "h1.entry-title, h1",
            detailsThumbnail = ".poster img, .entry-content img, .thumb img",
            detailsDescription = ".entry-content p, .entry-content, .desc",
            detailsGenre = ".tag-links a, .genres a, .genre a",
            detailsStatus = ".status, span:contains(Status)",
            detailsAuthor = ".author, .studio",
            episodeList = ".episode-list li, .episodelist li, ul.episodes li",
            episodeUrl = "a[href]",
            episodeName = ".ep-title, .title, a",
            episodeDate = ".ep-date, .date",
            playerIframes = ".responsive-embed iframe, .video-player iframe, iframe[src]"
        ),
        quirks = QuirksConfig(
            customReferer = "https://indohentai.net/"
        )
    ),
    preferences = preferences
) {

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("IndoHentai Filters"),
            IndoHentaiSortFilter()
        )
    }

    class IndoHentaiSortFilter : AnimeFilter.Select<String>(
        "Urutkan",
        arrayOf("Terbaru", "Terpopuler", "A-Z", "Rating"),
        0
    )
}
