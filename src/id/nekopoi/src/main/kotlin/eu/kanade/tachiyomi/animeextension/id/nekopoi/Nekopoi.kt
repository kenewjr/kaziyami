package eu.kanade.tachiyomi.animeextension.id.nekopoi

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.InMemorySourcePreferences
import eu.kanade.tachiyomi.animesource.SourcePreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.multisrc.generic.GenericThemeSource
import eu.kanade.tachiyomi.multisrc.generic.QuirksConfig
import eu.kanade.tachiyomi.multisrc.generic.SelectorsConfig
import eu.kanade.tachiyomi.multisrc.generic.SourceConfig

class Nekopoi(
    preferences: SourcePreferences = InMemorySourcePreferences()
) : GenericThemeSource(
    config = SourceConfig(
        name = "Nekopoi",
        lang = "id",
        baseUrl = "https://nekopoi.care",
        mirrors = listOf("https://nekopoi.app", "https://nekopoi.me"),
        popularPath = "/category/hentai/page/%d/",
        latestPath = "/page/%d/",
        searchPath = "/?s=%s&page=%d",
        selectors = SelectorsConfig(
            item = "article.type-post, .result ul li, .post-item, .nk-post-card, .eropost",
            itemTitle = "h2 a, .title a, a[title]",
            itemUrl = "h2 a, .title a, a[href]",
            itemThumbnail = "img",
            nextPage = ".pagination a.next, .nav-links a.next, a.next",
            detailsTitle = "h1.entry-title, .title h1, h1",
            detailsThumbnail = ".imgdesc img, .thumb img, .entry-content img",
            detailsDescription = ".entry-content, .desc",
            detailsGenre = ".genre a, .genres a, .tag-links a",
            detailsStatus = ".status, span:contains(Status)",
            detailsAuthor = ".producers, .studio",
            episodeList = ".episodelist ul li, .listeps ul li, .nk-episode-grid a, .tabs-anime a",
            episodeUrl = "a[href]",
            episodeName = ".epl-num, .title, a",
            episodeDate = ".epl-date, .date",
            playerIframes = ".player-embed iframe, #stream iframe, iframe[src]"
        ),
        quirks = QuirksConfig(
            customReferer = "https://nekopoi.care/"
        )
    ),
    preferences = preferences
) {

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Nekopoi Filters"),
            NekopoiCategoryFilter()
        )
    }

    class NekopoiCategoryFilter : AnimeFilter.Select<String>(
        "Category",
        arrayOf("All", "Hentai", "3D Hentai", "Jav Cosplay"),
        0
    )
}
