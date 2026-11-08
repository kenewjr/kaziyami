package app.kaziyami.multisrc.generic

import app.kaziyami.core.InMemorySharedPreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `default state blocks all content requests with descriptive exception`() {
        val prefs = InMemorySharedPreferences()
        val source = createTestSource(prefs)

        assertFalse(source.isAgeConfirmed())

        val anime = SAnime.create().apply { url = "/anime/test" }
        val episode = SEpisode.create().apply { url = "/watch/test" }

        val exPop = assertThrows(IllegalStateException::class.java) { source.popularAnimeRequest(1) }
        assertTrue(exPop.message!!.contains("Age verification required"))

        val exLatest = assertThrows(IllegalStateException::class.java) { source.latestUpdatesRequest(1) }
        assertTrue(exLatest.message!!.contains("Age verification required"))

        val exSearch = assertThrows(IllegalStateException::class.java) {
            source.searchAnimeRequest(1, "query", AnimeFilterList())
        }
        assertTrue(exSearch.message!!.contains("Age verification required"))

        val exDetails = assertThrows(IllegalStateException::class.java) { source.animeDetailsRequest(anime) }
        assertTrue(exDetails.message!!.contains("Age verification required"))

        val exEp = assertThrows(IllegalStateException::class.java) { source.episodeListRequest(anime) }
        assertTrue(exEp.message!!.contains("Age verification required"))

        val exVid = assertThrows(IllegalStateException::class.java) { source.videoListRequest(episode) }
        assertTrue(exVid.message!!.contains("Age verification required"))
    }

    @Test
    fun `enabling 18 plus preference allows requests without error`() {
        val prefs = InMemorySharedPreferences()
        val source = createTestSource(prefs)

        prefs.edit().putBoolean("age_gate_confirmed", true).apply()
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
