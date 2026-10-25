package eu.kanade.tachiyomi.animeextension.id.kuronime

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

class KuronimeTest {

    private val source = Kuronime()

    private fun loadFixture(path: String): String {
        val stream = javaClass.getResourceAsStream("/kuronime/$path")
            ?: throw IllegalArgumentException("Fixture /kuronime/$path not found")
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
        val response = mockResponse("https://kuronime.sbs/anime/?order=popular&page=1", html)
        val page = source.popularAnimeParse(response)

        assertEquals(2, page.animes.size)
        assertTrue(page.hasNextPage)
        assertEquals("Tate no Yuusha", page.animes[0].title)
        assertEquals("https://kuronime.sbs/anime/tate-no-yuusha/", page.animes[0].url)
        assertEquals("https://kuronime.sbs/poster/tate.jpg", page.animes[0].thumbnail_url)
    }

    @Test
    fun parseAnimeDetails() {
        val html = loadFixture("details.html")
        val response = mockResponse("https://kuronime.sbs/anime/tate-no-yuusha/", html)
        val details = source.animeDetailsParse(response)

        assertEquals("Tate no Yuusha", details.title)
        assertEquals("https://kuronime.sbs/poster/tate.jpg", details.thumbnail_url)
        assertTrue(details.description!!.contains("Naofumi Iwatani dipanggil"))
        assertEquals("Action, Isekai", details.genre)
        assertEquals(SAnime.COMPLETED, details.status)
        assertEquals("Studio: Kinema Citrus", details.author)
    }

    @Test
    fun parseEpisodeList() {
        val html = loadFixture("episodes.html")
        val response = mockResponse("https://kuronime.sbs/anime/tate-no-yuusha/", html)
        val episodes = source.episodeListParse(response)

        assertEquals(2, episodes.size)
        assertEquals(12.0f, episodes[0].episode_number)
        assertEquals(11.0f, episodes[1].episode_number)
        assertTrue(episodes[0].date_upload > 0L)
    }

    @Test
    fun parseVideoList() {
        val html = loadFixture("video_player.html")
        val response = mockResponse("https://kuronime.sbs/tate-no-yuusha-episode-12/", html)
        val videos = source.videoListParse(response)

        assertEquals(1, videos.size)
        assertEquals("https://kuronime.sbs/stream/tate-12.mp4", videos[0].videoUrl)
    }

    @Test
    fun parseSearchAnime() {
        val html = loadFixture("search.html")
        val response = mockResponse("https://kuronime.sbs/?s=tate&page=1", html)
        val page = source.searchAnimeParse(response)

        assertEquals(1, page.animes.size)
        assertEquals("Tate no Yuusha", page.animes[0].title)
    }

    @Test
    fun parseSearchEmpty() {
        val html = loadFixture("search_empty.html")
        val response = mockResponse("https://kuronime.sbs/?s=unknown&page=1", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isEmpty())
        assertFalse(page.hasNextPage)
    }
}
