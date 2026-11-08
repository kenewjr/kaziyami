package eu.kanade.tachiyomi.animeextension.id.nontonhentai

import eu.kanade.tachiyomi.animesource.model.SAnime
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class NontonHentaiTest {

    private val source = NontonHentai()

    private fun loadFixture(path: String): String {
        val stream = javaClass.getResourceAsStream("/nontonhentai/$path")
            ?: throw IllegalArgumentException("Fixture /nontonhentai/$path not found")
        return InputStreamReader(stream).use { it.readText() }
    }

    private fun mockResponse(url: String, html: String): Response {
        val request = Request.Builder().url(url).build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(html.toResponseBody())
            .build()
    }

    @Test
    fun parsePopularAnime() {
        val html = loadFixture("popular.html")
        val response = mockResponse("https://nontonhentai.net/page/1/", html)
        val page = source.popularAnimeParse(response)

        assertEquals(2, page.animes.size)
        assertTrue(page.hasNextPage)
        assertEquals("Tate no Yuusha", page.animes[0].title)
        assertEquals("/anime/tate-no-yuusha/", page.animes[0].url)
    }

    @Test
    fun parseAnimeDetails() {
        val html = loadFixture("details.html")
        val response = mockResponse("https://nontonhentai.net/anime/tate-no-yuusha/", html)
        val details = source.animeDetailsParse(response)

        assertEquals("Tate no Yuusha", details.title)
        assertTrue(details.description!!.contains("Naofumi Iwatani dipanggil"))
        assertEquals("Action, Isekai", details.genre)
        assertEquals(SAnime.COMPLETED, details.status)
    }

    @Test
    fun parseEpisodeList() {
        val html = loadFixture("episodes.html")
        val response = mockResponse("https://nontonhentai.net/anime/tate-no-yuusha/", html)
        val episodes = source.episodeListParse(response)

        assertEquals(2, episodes.size)
        assertEquals(12.0f, episodes[0].episode_number)
        assertEquals(11.0f, episodes[1].episode_number)
        assertTrue(episodes[0].date_upload > 0L)
    }

    @Test
    fun parseVideoList() {
        val html = loadFixture("video_player.html")
        val response = mockResponse("https://nontonhentai.net/tate-no-yuusha-episode-12/", html)
        val videos = source.videoListParse(response)

        assertEquals(1, videos.size)
        assertEquals("https://nontonhentai.net/stream/tate-12.mp4", videos[0].videoUrl)
    }

    @Test
    fun parseSearchAnime() {
        val html = loadFixture("search.html")
        val response = mockResponse("https://nontonhentai.net/?s=tate", html)
        val page = source.searchAnimeParse(response)

        assertEquals(1, page.animes.size)
        assertEquals("Tate no Yuusha", page.animes[0].title)
    }

    @Test
    fun parseSearchEmpty() {
        val html = loadFixture("search_empty.html")
        val response = mockResponse("https://nontonhentai.net/?s=unknown", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test
    fun searchAnimeRequestFormatsQueryAndPagination() {
        source.preferences.edit().putBoolean("age_gate_confirmed", true).apply()
        val filters = source.getFilterList()

        val req1 = source.searchAnimeRequest(1, "tate", filters)
        assertEquals("https://nontonhentai.net/?s=tate", req1.url.toString())

        val req2 = source.searchAnimeRequest(2, "tate", filters)
        assertEquals("https://nontonhentai.net/page/2/?s=tate", req2.url.toString())
    }

    @Test
    fun searchAnimeRequestFormatsFiltersWhenQueryBlank() {
        source.preferences.edit().putBoolean("age_gate_confirmed", true).apply()
        val filters = source.getFilterList()
        val sortFilter = filters.filterIsInstance<NontonHentai.NontonHentaiSortFilter>().first()
        sortFilter.state = 1 // Popular
        val genreFilter = filters.filterIsInstance<NontonHentai.NontonHentaiGenreFilter>().first()
        // Find milf index
        val milfIdx = NontonHentai.GENRES.indexOfFirst { it.slug == "milf" }
        genreFilter.state = milfIdx

        val req = source.searchAnimeRequest(1, "", filters)
        assertTrue(req.url.toString().contains("/anime/?"))
        assertTrue(req.url.toString().contains("order=popular"))
        assertTrue(req.url.toString().contains("genre%5B%5D=milf"))
        assertTrue(req.url.toString().contains("page=1"))
    }

    @Test
    fun filterListContainsCuratedFilters() {
        val filters = source.getFilterList()
        assertTrue(filters.any { it is NontonHentai.NontonHentaiSortFilter })
        assertTrue(filters.any { it is NontonHentai.NontonHentaiStatusFilter })
        val genreFilter = filters.filterIsInstance<NontonHentai.NontonHentaiGenreFilter>().firstOrNull()
        assertTrue(genreFilter != null)
        assertTrue(NontonHentai.GENRES.size > 50)
        assertTrue(NontonHentai.GENRES.any { it.slug == "milf" })
        assertTrue(NontonHentai.GENRES.any { it.slug == "ahegao" })
    }
}
