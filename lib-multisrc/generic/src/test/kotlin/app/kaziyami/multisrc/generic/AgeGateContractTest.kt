package app.kaziyami.multisrc.generic

import app.kaziyami.core.InMemorySharedPreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgeGateContractTest {

    private fun createTestSource(prefs: InMemorySharedPreferences): GenericThemeSource {
        val config = SourceConfig(
            name = "AgeGateTestSource",
            lang = "en",
            baseUrl = "https://example.com",
            popularPath = "/popular?page=%d",
            latestPath = "/latest?page=%d",
            searchPath = "/search?q=%s&p=%d",
            selectors = SelectorsConfig(
                item = ".item",
                itemTitle = ".title",
                itemUrl = "a",
                itemThumbnail = "img"
            )
        )
        return GenericThemeSource(config, prefs)
    }

    @Test
    fun `default state allows all content requests without requiring in-extension age gate`() {
        val prefs = InMemorySharedPreferences()
        val source = createTestSource(prefs)

        assertTrue(source.isAgeConfirmed())

        val anime = SAnime.create().apply { url = "/anime/test" }
        val episode = SEpisode.create().apply { url = "/watch/test" }

        assertNotNull(source.popularAnimeRequest(1))
        assertNotNull(source.latestUpdatesRequest(1))
        assertNotNull(source.searchAnimeRequest(1, "query", AnimeFilterList()))
        assertNotNull(source.animeDetailsRequest(anime))
        assertNotNull(source.episodeListRequest(anime))
        assertNotNull(source.videoListRequest(episode))
    }
}
