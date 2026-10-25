package eu.kanade.tachiyomi.animeextension.id.nontonhentai

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.InMemorySourcePreferences
import eu.kanade.tachiyomi.animesource.SourcePreferences
import app.kaziyami.multisrc.animestream.AnimeStreamTheme
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig

class NontonHentai(
    preferences: SourcePreferences = InMemorySourcePreferences()
) : AnimeStreamTheme(
    name = "NontonHentai",
    lang = "id",
    baseUrl = "https://nontonhentai.net",
    mirrors = listOf("https://nontonhentai.net"),
    popularPath = "/page/%d/",
    latestPath = "/page/%d/",
    searchPath = "/?s=%s",
    customSelectors = SelectorsConfig(
        item = "div.listupd article.bs, .animposx, .listupd article, div.bsx",
        itemTitle = ".title, h4, h2, a[title]",
        itemThumbnail = "img",
        nextPage = ".pagination .next, a.next, .hpage .r",
        detailsTitle = "h1.entry-title, .infox h1, h1",
        detailsThumbnail = ".thumb img, .poster img",
        detailsDescription = ".entry-content p, .synopsis, .desc, .sinopsis",
        episodeList = ".eplister ul li, .episodelist ul li",
        playerIframes = ".player-embed iframe, iframe[src]"
    ),
    customQuirks = QuirksConfig(
        customReferer = "https://nontonhentai.net/"
    ),
    preferences = preferences
)
