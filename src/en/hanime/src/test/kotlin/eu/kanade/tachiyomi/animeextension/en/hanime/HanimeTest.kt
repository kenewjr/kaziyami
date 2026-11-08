package eu.kanade.tachiyomi.animeextension.en.hanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class HanimeTest {

    private val source = Hanime().apply {
        preferences.edit().putBoolean("age_gate_confirmed", true).apply()
    }

    private fun loadFixture(path: String): String {
        val stream = javaClass.getResourceAsStream("/hanime/$path")
            ?: throw IllegalArgumentException("Fixture /hanime/$path not found")
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
        val response = mockResponse("https://hanime.tv/browse/trending?page=1", html)
        val page = source.popularAnimeParse(response)

        assertEquals(2, page.animes.size)
        assertTrue(page.hasNextPage)
        assertEquals("Shoujo Ramune Episode 1", page.animes[0].title)
        assertEquals("/hentai/video/shoujo-ramune-1", page.animes[0].url)
        assertEquals("https://hanime.tv/thumbs/shoujo-1.jpg", page.animes[0].thumbnail_url)
    }

    @Test
    fun parseAnimeDetails() {
        val html = loadFixture("details.html")
        val response = mockResponse("https://hanime.tv/hentai/video/shoujo-ramune-1", html)
        val details = source.animeDetailsParse(response)

        assertEquals("Shoujo Ramune", details.title)
        assertEquals("https://hanime.tv/posters/shoujo.jpg", details.thumbnail_url)
        assertTrue(details.description!!.contains("Summer vacation adventures"))
        assertEquals("Summer, Vanilla", details.genre)
        assertEquals("Milk Factory", details.author)
    }

    @Test
    fun parseEpisodeList() {
        val html = loadFixture("details.html")
        val response = mockResponse("https://hanime.tv/hentai/video/shoujo-ramune-1", html)
        val episodes = source.episodeListParse(response)

        assertEquals(1, episodes.size)
        assertEquals("Full Episode", episodes[0].name)
        assertEquals(1.0f, episodes[0].episode_number)
        assertTrue(episodes[0].date_upload > 0L)
    }

    @Test
    fun parseVideoList() {
        val html = loadFixture("video_player.html")
        val response = mockResponse("https://hanime.tv/hentai/video/shoujo-ramune-1", html)
        val videos = source.videoListParse(response)

        assertEquals(2, videos.size)
        assertTrue(videos.any { it.videoUrl?.contains(".m3u8") == true })
        assertTrue(videos.any { it.videoUrl?.contains(".mp4") == true })
    }

    @Test
    fun parseSearchAnime() {
        val html = loadFixture("search.html")
        val response = mockResponse("https://hanime.tv/search?q=ramune&page=1", html)
        val page = source.searchAnimeParse(response)

        assertEquals(1, page.animes.size)
        assertEquals("Shoujo Ramune Episode 1", page.animes[0].title)
    }

    @Test
    fun parseSearchEmpty() {
        val html = loadFixture("search_empty.html")
        val response = mockResponse("https://hanime.tv/search?q=unknown&page=1", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test
    fun testFiltersAndSearchRequest() {
        val filterList = source.getFilterList()
        val sortFilter = filterList.filterIsInstance<Hanime.HanimeSortFilter>().first()
        val genreFilter = filterList.filterIsInstance<Hanime.HanimeGenreFilter>().first()

        assertTrue(genreFilter.values.size > 50)
        assertTrue(genreFilter.values.contains("Yuri"))
        assertTrue(genreFilter.values.contains("Big Boobs"))

        // Default request: Trending
        val reqDefault = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://hanime.tv/browse/trending?page=1", reqDefault.url.toString())

        // Sort = Latest
        sortFilter.state = 1
        val reqLatest = source.searchAnimeRequest(2, "", filterList)
        assertEquals("https://hanime.tv/?page=2", reqLatest.url.toString())

        // Genre = Yuri
        val yuriIndex = genreFilter.values.indexOf("Yuri")
        assertTrue(yuriIndex >= 0)
        genreFilter.state = yuriIndex
        val reqGenre = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://hanime.tv/browse/tags/yuri?page=1", reqGenre.url.toString())

        // Genre = Big Boobs (URL-encoded space)
        val boobsIndex = genreFilter.values.indexOf("Big Boobs")
        assertTrue(boobsIndex >= 0)
        genreFilter.state = boobsIndex
        val reqBoobs = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://hanime.tv/browse/tags/big%20boobs?page=1", reqBoobs.url.toString())

        // Genre + Search Query
        val reqWithQuery = source.searchAnimeRequest(1, "ramune", filterList)
        assertEquals("https://hanime.tv/browse/tags/big%20boobs?page=1&q=ramune", reqWithQuery.url.toString())
    }
}
