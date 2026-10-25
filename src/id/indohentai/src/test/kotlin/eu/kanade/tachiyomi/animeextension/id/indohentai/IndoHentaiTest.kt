package eu.kanade.tachiyomi.animeextension.id.indohentai

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

class IndoHentaiTest {

    private val source = IndoHentai()

    private fun loadFixture(path: String): String {
        val stream = javaClass.getResourceAsStream("/indohentai/$path")
            ?: throw IllegalArgumentException("Fixture /indohentai/$path not found")
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
        val response = mockResponse("https://indohentai.net/terbaru/page/1/", html)
        val page = source.popularAnimeParse(response)

        assertEquals(2, page.animes.size)
        assertTrue(page.hasNextPage)
        assertEquals("Overflow Episode 1 Sub Indo", page.animes[0].title)
        assertEquals("https://indohentai.net/hentai/overflow-episode-1-sub-indo/", page.animes[0].url)
        assertEquals("https://indohentai.net/covers/overflow.jpg", page.animes[0].thumbnail_url)
    }

    @Test
    fun parseAnimeDetails() {
        val html = loadFixture("details.html")
        val response = mockResponse("https://indohentai.net/hentai/overflow-sub-indo/", html)
        val details = source.animeDetailsParse(response)

        assertEquals("Overflow Sub Indo", details.title)
        assertEquals("https://indohentai.net/posters/overflow.jpg", details.thumbnail_url)
        assertTrue(details.description!!.contains("Sinopsis cerita sepasang kakak beradik"))
        assertEquals("Harem, Comedy", details.genre)
        assertEquals(SAnime.ONGOING, details.status)
        assertEquals("Studio Houkiboshi", details.author)
    }

    @Test
    fun parseEpisodeList() {
        val html = loadFixture("episodes.html")
        val response = mockResponse("https://indohentai.net/hentai/overflow-sub-indo/", html)
        val episodes = source.episodeListParse(response)

        assertEquals(2, episodes.size)
        assertEquals(2.0f, episodes[0].episode_number)
        assertEquals(1.0f, episodes[1].episode_number)
        assertTrue(episodes[0].date_upload > 0L)
    }

    @Test
    fun parseVideoList() {
        val html = loadFixture("video_player.html")
        val response = mockResponse("https://indohentai.net/hentai/overflow-episode-1-sub-indo/", html)
        val videos = source.videoListParse(response)

        assertEquals(1, videos.size)
        assertEquals("https://indohentai.net/stream/overflow-01.mp4", videos[0].videoUrl)
    }

    @Test
    fun parseSearchAnime() {
        val html = loadFixture("search.html")
        val response = mockResponse("https://indohentai.net/?s=overflow&page=1", html)
        val page = source.searchAnimeParse(response)

        assertEquals(1, page.animes.size)
        assertEquals("Overflow Episode 1 Sub Indo", page.animes[0].title)
    }

    @Test
    fun parseSearchEmpty() {
        val html = loadFixture("search_empty.html")
        val response = mockResponse("https://indohentai.net/?s=unknown&page=1", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isEmpty())
        assertFalse(page.hasNextPage)
    }
}
