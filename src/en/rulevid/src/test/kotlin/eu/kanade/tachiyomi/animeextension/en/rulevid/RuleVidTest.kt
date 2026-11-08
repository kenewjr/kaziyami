package eu.kanade.tachiyomi.animeextension.en.rulevid

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class RuleVidTest {

    private val source = RuleVid()

    private fun loadFixture(path: String): String {
        val stream = javaClass.getResourceAsStream("/rulevid/$path")
            ?: throw IllegalArgumentException("Fixture /rulevid/$path not found")
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
        val response = mockResponse("https://rulevid.com/most-popular/", html)
        val page = source.popularAnimeParse(response)

        assertTrue(page.animes.isNotEmpty(), "Popular anime list should not be empty")
        assertTrue(page.hasNextPage, "Should have next page")
        val first = page.animes[0]
        assertTrue(first.title.isNotBlank())
        assertTrue(first.url.startsWith("/video/"))
    }

    @Test
    fun parseAnimeDetails() {
        val html = loadFixture("detail.html")
        val response = mockResponse("https://rulevid.com/video/11/gwen-summer-heat-all-wip/", html)
        val details = source.animeDetailsParse(response)

        assertTrue(details.title.contains("Gwen Summer Heat"))
        assertTrue(details.genre!!.isNotEmpty())
    }

    @Test
    fun parseEpisodeListSynthesizesSingleVideo() {
        val html = loadFixture("detail.html")
        val response = mockResponse("https://rulevid.com/video/11/gwen-summer-heat-all-wip/", html)
        val episodes = source.episodeListParse(response)

        assertEquals(1, episodes.size)
        assertEquals("Full Video", episodes[0].name)
        assertEquals(1.0f, episodes[0].episode_number)
    }

    @Test
    fun parseVideoList() {
        val html = loadFixture("episode.html")
        val response = mockResponse("https://rulevid.com/video/11/gwen-summer-heat-all-wip/", html)
        val videos = source.videoListParse(response)

        assertTrue(videos.isNotEmpty(), "Extracted videos should not be empty")
        assertTrue(videos.any { it.quality.contains("360p") || it.quality.contains("Default") })
        assertTrue(videos.all { it.videoUrl!!.endsWith(".mp4") || it.videoUrl!!.contains(".mp4") })
    }

    @Test
    fun parseSearchAnime() {
        val html = loadFixture("search.html")
        val response = mockResponse("https://rulevid.com/search/belle/?from_videos=1", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isNotEmpty(), "Search results should not be empty")
    }
}

