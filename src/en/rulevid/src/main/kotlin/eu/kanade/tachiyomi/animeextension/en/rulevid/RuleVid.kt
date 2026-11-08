package eu.kanade.tachiyomi.animeextension.en.rulevid

import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.kvs.KvsTheme

class RuleVid : KvsTheme(
    name = "RuleVid",
    lang = "en",
    baseUrl = "https://rulevid.com",
    mirrors = listOf("https://rulevid.com"),
    popularPath = "/most-popular/%d/",
    latestPath = "/latest-updates/%d/",
    searchPath = "/search/%s/",
    customSelectors = SelectorsConfig(
        item = ".video-card, .item",
        itemTitle = "a.video-card-thumb-wrap img[alt], a[title], img[alt], .title",
        itemThumbnail = "img.video-card-thumb, img",
        itemUrl = "a.video-card-thumb-wrap, a[href*='/video/']",
        nextPage = ".pagination .active + a, a[data-parameters*='from']",
        detailsTitle = "h1, .headline h1",
        detailsThumbnail = ".video-card-thumb, img",
        detailsDescription = ".video-details, .description, .item-description",
        detailsGenre = "a[href*='/tags/'], a[href*='/categories/']"
    ),
    customQuirks = QuirksConfig(
        customReferer = "https://rulevid.com/"
    )
)

