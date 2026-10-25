package eu.kanade.tachiyomi.animeextension.id.kuronime

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.InMemorySourcePreferences
import eu.kanade.tachiyomi.animesource.SourcePreferences
import app.kaziyami.multisrc.animestream.AnimeStreamTheme
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig

class Kuronime(
    preferences: SourcePreferences = InMemorySourcePreferences()
) : AnimeStreamTheme(
    name = "Kuronime",
    lang = "id",
    baseUrl = "https://kuronime.sbs",
    mirrors = listOf("https://kuronime.org", "https://kuronime.moe"),
    popularPath = "/anime/?status=&type=&order=popular&page=%d",
    latestPath = "/anime/?status=&type=&order=update&page=%d",
    searchPath = "/?s=%s&page=%d",
    customSelectors = SelectorsConfig(
        item = ".animposx, .listupd article, div.bsx",
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
        customReferer = "https://kuronime.sbs/"
    ),
    preferences = preferences
)
