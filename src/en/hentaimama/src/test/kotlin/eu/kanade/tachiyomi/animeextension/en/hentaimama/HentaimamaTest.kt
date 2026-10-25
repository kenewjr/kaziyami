package eu.kanade.tachiyomi.animeextension.en.hentaimama

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

class HentaimamaTest {

    private val source = Hentaimama()

    private fun loadFixture(path: String): String {
        val stream = javaClass.getResourceAsStream("/hentaimama/$path")
            ?: throw IllegalArgumentException("Fixture /hentaimama/$path not found")
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
        val response = mockResponse("https://hentaimama.io/tvshows/page/1/", html)
        val page = source.popularAnimeParse(response)

        assertEquals(2, page.animes.size)
        assertTrue(page.hasNextPage)
        assertEquals("Itadaki Seieki Episode 1", page.animes[0].title)
        assertEquals("/episodes/itadaki-seieki-episode-1/", page.animes[0].url)
        assertEquals("https://hentaimama.io/thumbs/itadaki.jpg", page.animes[0].thumbnail_url)
    }

    @Test
    fun parseAnimeDetails() {
        val html = loadFixture("details.html")
        val response = mockResponse("https://hentaimama.io/tvshows/itadaki-seieki/", html)
        val details = source.animeDetailsParse(response)

        assertEquals("Itadaki Seieki", details.title)
        assertEquals("https://hentaimama.io/posters/itadaki.jpg", details.thumbnail_url)
        assertTrue(details.description!!.contains("A vampire succubus hybrid"))
        assertEquals("Comedy, Supernatural", details.genre)
        assertEquals(SAnime.COMPLETED, details.status)
    }

    @Test
    fun parseEpisodeList() {
        val html = loadFixture("episodes.html")
        val response = mockResponse("https://hentaimama.io/tvshows/itadaki-seieki/", html)
        val episodes = source.episodeListParse(response)

        assertEquals(2, episodes.size)
        assertEquals(1.0f, episodes[0].episode_number)
        assertEquals(1.0f, episodes[1].episode_number)
        assertTrue(episodes[0].date_upload > 0L)
    }

    @Test
    fun parseVideoList() {
        val html = loadFixture("video_player.html")
        val response = mockResponse("https://hentaimama.io/episodes/itadaki-seieki-episode-1/", html)
        val videos = source.videoListParse(response)

        assertEquals(1, videos.size)
        assertEquals("https://hentaimama.io/stream/itadaki-01.mp4", videos[0].videoUrl)
    }

    @Test
    fun parseSearchAnime() {
        val html = loadFixture("search.html")
        val response = mockResponse("https://hentaimama.io/?s=itadaki&page=1", html)
        val page = source.searchAnimeParse(response)

        assertEquals(1, page.animes.size)
        assertEquals("Itadaki Seieki Episode 1", page.animes[0].title)
    }

    @Test
    fun parseSearchEmpty() {
        val html = loadFixture("search_empty.html")
        val response = mockResponse("https://hentaimama.io/?s=unknown&page=1", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isEmpty())
        assertFalse(page.hasNextPage)
    }
}
