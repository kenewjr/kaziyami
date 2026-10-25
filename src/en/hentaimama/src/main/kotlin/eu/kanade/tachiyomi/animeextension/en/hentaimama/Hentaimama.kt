package eu.kanade.tachiyomi.animeextension.en.hentaimama

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.InMemorySourcePreferences
import eu.kanade.tachiyomi.animesource.SourcePreferences
import app.kaziyami.multisrc.dooplay.DooPlayTheme
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig

class Hentaimama(
    preferences: SourcePreferences = InMemorySourcePreferences()
) : DooPlayTheme(
    name = "Hentaimama",
    lang = "en",
    baseUrl = "https://hentaimama.io",
    mirrors = listOf("https://hentaimama.tv"),
    popularPath = "/tvshows/page/%d/",
    latestPath = "/episodes/page/%d/",
    searchPath = "/?s=%s&page=%d",
    customSelectors = SelectorsConfig(
        item = "article.item.tvshows, article.item, .items article",
        itemTitle = ".data h3 a, h3 a",
        itemThumbnail = "img",
        nextPage = ".pagination a.next, a.next",
        detailsTitle = "h1, .data h1",
        detailsThumbnail = ".poster img, .thumb img",
        detailsDescription = ".wp-content p, #info .wp-content",
        episodeList = "#episodes ul.episodios li, .episodios li",
        playerIframes = ".playex iframe, .source-box iframe, iframe.metaframe, iframe[src]"
    ),
    customQuirks = QuirksConfig(
        usesAjaxPlayer = true,
        customReferer = "https://hentaimama.io/"
    ),
    preferences = preferences
)
