package eu.kanade.tachiyomi.animeextension.en.javsubbed

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.InMemorySourcePreferences
import eu.kanade.tachiyomi.animesource.SourcePreferences
import eu.kanade.tachiyomi.multisrc.generic.QuirksConfig
import eu.kanade.tachiyomi.multisrc.generic.SelectorsConfig
import eu.kanade.tachiyomi.multisrc.retrotube.RetroTubeTheme

class JAVSubbed(
    preferences: SourcePreferences = InMemorySourcePreferences()
) : RetroTubeTheme(
    name = "JAVSubbed",
    lang = "en",
    baseUrl = "https://javsubbed.net",
    mirrors = listOf("https://javsubbed.com"),
    popularPath = "/most-viewed/page/%d/",
    latestPath = "/page/%d/",
    searchPath = "/?s=%s&page=%d",
    customSelectors = SelectorsConfig(
        item = "article.loop-video, .video-item, article.post",
        itemTitle = "header.entry-header h2 a, .post-title a, a[title]",
        itemThumbnail = ".post-thumbnail img, img",
        nextPage = ".pagination a.next, .nav-links a.next, a.next",
        detailsTitle = "h1.entry-title, .video-details h1, h1",
        detailsThumbnail = ".video-player img, meta[property=og:image], .post-thumbnail img",
        detailsDescription = ".entry-content, .video-description, #tab-overview",
        detailsGenre = ".video-tags a, .post-tags a, a[rel=tag]",
        detailsAuthor = ".video-actors a, .models a, .video-info a[rel=tag]",
        playerIframes = ".responsive-player iframe, .sourcetabs iframe, iframe[src]"
    ),
    customQuirks = QuirksConfig(
        customReferer = "https://javsubbed.net/"
    ),
    preferences = preferences
)
