package eu.kanade.tachiyomi.animeextension.en.rule34video

import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.kvs.KvsTheme

class Rule34Video : KvsTheme(
    name = "Rule34Video",
    lang = "en",
    baseUrl = "https://rule34video.com",
    mirrors = listOf("https://rule34video.party", "https://r34video.com"),
    popularPath = "/most-popular/%d/",
    latestPath = "/latest-updates/%d/",
    searchPath = "/search/%s/?from_videos=%d",
    customSelectors = SelectorsConfig(
        item = "div[data-video-card-id], .item.thumb, div.item.thumb",
        itemTitle = "a.th[title], a[title], img[alt], .title",
        itemThumbnail = "img.thumb, img[data-original], img",
        itemUrl = "a.th, a[href*='/video/']",
        nextPage = ".pagination .item.active + .item a, .pagination .active + a, a[data-parameters*='from']",
        detailsTitle = "h1, .headline h1",
        detailsThumbnail = ".img.wrap_image img, img.thumb, img",
        detailsDescription = ".video-details, .description, .item-description, .info",
        detailsGenre = "a[href*='/tags/'], a[href*='/categories/']"
    ),
    customQuirks = QuirksConfig(
        customReferer = "https://rule34video.com/"
    )
)
