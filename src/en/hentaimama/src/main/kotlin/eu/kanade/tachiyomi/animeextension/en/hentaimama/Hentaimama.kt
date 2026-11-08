package eu.kanade.tachiyomi.animeextension.en.hentaimama

import app.kaziyami.multisrc.dooplay.DooPlayTheme
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig

class Hentaimama : DooPlayTheme(
    name = "Hentaimama",
    lang = "en",
    baseUrl = "https://hentaimama.io",
    mirrors = listOf("https://hentaimama.tv"),
    popularPath = "/tvshows/page/%d/",
    latestPath = "/episodes/page/%d/",
    searchPath = "/?s=%s&page=%d",
    customSelectors = SelectorsConfig(
        item = "article.item.tvshows, article.item, .items article, article.series-card, .series-card",
        itemTitle = "h3.sc-title a, .sc-title a, .data h3 a, h3 a, h2 a, a[title]",
        itemUrl = "a.sc-poster, .data h3 a, a[href]",
        itemThumbnail = "img",
        nextPage = ".pagination a.next, a.next",
        detailsTitle = "h1, .data h1",
        detailsThumbnail = ".poster img, .thumb img",
        detailsDescription = ".wp-content p, #info .wp-content",
        episodeList = "#episodes a.dt-se-item, .dt-se-list a.dt-se-item, a.dt-se-item, #episodes ul.episodios li, .episodios li",
        episodeName = ".dt-se-title, b.dt-se-title, .episodiotitle a, .numerando, .dt-se-num",
        episodeDate = ".dt-se-date, span.dt-se-date, .date",
        episodeUrl = "a[href], a",
        playerIframes = ".playex iframe, .source-box iframe, iframe.metaframe, iframe[src]"
    ),
    customQuirks = QuirksConfig(
        usesAjaxPlayer = true,
        customReferer = "https://hentaimama.io/"
    )
)
