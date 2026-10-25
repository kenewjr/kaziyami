package eu.kanade.tachiyomi.animeextension.en.javsubbed

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class JAVSubbedTest {

    private val source = JAVSubbed()

    private fun loadFixture(path: String): String {
        val stream = javaClass.getResourceAsStream("/javsubbed/$path")
            ?: throw IllegalArgumentException("Fixture /javsubbed/$path not found")
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
        val response = mockResponse("https://javsubbed.net/most-viewed/page/1/", html)
        val page = source.popularAnimeParse(response)

        assertEquals(2, page.animes.size)
        assertTrue(page.hasNextPage)
        assertEquals("STARS-123 Beautiful Actress English Sub", page.animes[0].title)
        assertEquals("/video/stars-123-subbed/", page.animes[0].url)
        assertEquals("https://javsubbed.net/thumbs/stars-123.jpg", page.animes[0].thumbnail_url)
    }

    @Test
    fun parseAnimeDetails() {
        val html = loadFixture("details.html")
        val response = mockResponse("https://javsubbed.net/video/stars-123-subbed/", html)
        val details = source.animeDetailsParse(response)

        assertEquals("STARS-123 Beautiful Actress English Sub", details.title)
        assertEquals("https://javsubbed.net/posters/stars-123.jpg", details.thumbnail_url)
        assertTrue(details.description!!.contains("Exciting JAV story"))
        assertEquals("English Subbed, Cosplay", details.genre)
        assertEquals("Yua Mikami", details.author)
    }

    @Test
    fun parseEpisodeListSynthesizesSingleVideo() {
        val html = loadFixture("details.html")
        val response = mockResponse("https://javsubbed.net/video/stars-123-subbed/", html)
        val episodes = source.episodeListParse(response)

        assertEquals(1, episodes.size)
        assertEquals("Full Video", episodes[0].name)
        assertEquals(1.0f, episodes[0].episode_number)
        assertTrue(episodes[0].date_upload > 0L)
    }

    @Test
    fun parseVideoList() {
        val html = loadFixture("video_player.html")
        val response = mockResponse("https://javsubbed.net/video/stars-123-subbed/", html)
        val videos = source.videoListParse(response)

        assertEquals(1, videos.size)
        assertEquals("https://javsubbed.net/stream/stars-123.mp4", videos[0].videoUrl)
    }

    @Test
    fun parseSearchAnime() {
        val html = loadFixture("search.html")
        val response = mockResponse("https://javsubbed.net/?s=stars&page=1", html)
        val page = source.searchAnimeParse(response)

        assertEquals(1, page.animes.size)
        assertEquals("STARS-123 Beautiful Actress English Sub", page.animes[0].title)
    }

    @Test
    fun parseSearchEmpty() {
        val html = loadFixture("search_empty.html")
        val response = mockResponse("https://javsubbed.net/?s=unknown&page=1", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isEmpty())
        assertFalse(page.hasNextPage)
    }
}
