package eu.kanade.tachiyomi.animeextension.en.rule34video

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class Rule34VideoTest {

    private val source = Rule34Video().apply {
        preferences.edit().putBoolean("age_gate_confirmed", true).apply()
    }

    private fun loadFixture(path: String): String {
        val stream = javaClass.getResourceAsStream("/rule34video/$path")
            ?: throw IllegalArgumentException("Fixture /rule34video/$path not found")
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
        val response = mockResponse("https://rule34video.com/most-popular/", html)
        val page = source.popularAnimeParse(response)

        assertTrue(page.animes.isNotEmpty(), "Popular anime list should not be empty")
        val first = page.animes[0]
        assertTrue(first.title.isNotBlank())
        assertTrue(first.url.startsWith("/video/"))
    }

    @Test
    fun parseAnimeDetails() {
        val html = loadFixture("detail.html")
        val response = mockResponse("https://rule34video.com/video/3063279/jackerman-mother-s-warmth-ep-2-60fps/", html)
        val details = source.animeDetailsParse(response)

        assertTrue(details.title.contains("Mother's Warmth") || details.title.contains("Jackerman"))
        assertTrue(details.genre!!.isNotEmpty())
    }

    @Test
    fun parseEpisodeListSynthesizesSingleVideo() {
        val html = loadFixture("detail.html")
        val response = mockResponse("https://rule34video.com/video/3063279/jackerman-mother-s-warmth-ep-2-60fps/", html)
        val episodes = source.episodeListParse(response)

        assertEquals(1, episodes.size)
        assertEquals("Full Video", episodes[0].name)
        assertEquals(1.0f, episodes[0].episode_number)
    }

    @Test
    fun parseVideoList() {
        val html = loadFixture("episode.html")
        val response = mockResponse("https://rule34video.com/video/3063279/jackerman-mother-s-warmth-ep-2-60fps/", html)
        val videos = source.videoListParse(response)

        assertTrue(videos.isNotEmpty(), "Extracted videos should not be empty")
        assertTrue(videos.any { it.quality.contains("360p") || it.quality.contains("Default") })
        assertTrue(videos.any { it.quality.contains("720p") || it.quality.contains("1080p") })
        assertTrue(videos.all { it.videoUrl!!.endsWith(".mp4") || it.videoUrl!!.contains(".mp4") })
    }

    @Test
    fun parseSearchAnime() {
        val html = loadFixture("search.html")
        val response = mockResponse("https://rule34video.com/search/peach/?from_videos=1", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isNotEmpty(), "Search results should not be empty")
    }

    @Test
    fun testFiltersAndSearchRequest() {
        val filterList = source.getFilterList()
        val sortFilter = filterList.filterIsInstance<Rule34Video.Rule34VideoSortFilter>().first()
        val genreFilter = filterList.filterIsInstance<Rule34Video.Rule34VideoGenreFilter>().first()

        assertEquals(4, sortFilter.values.size)
        assertTrue(genreFilter.values.size >= 50)
        assertTrue(genreFilter.values.contains("3D"))
        assertTrue(genreFilter.values.contains("Blowjob"))

        // 1. Default request
        val reqDefault = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://rule34video.com/most-popular/", reqDefault.url.toString())

        // 2. Default page 2
        val reqPage2 = source.searchAnimeRequest(2, "", filterList)
        assertEquals("https://rule34video.com/most-popular/2/", reqPage2.url.toString())

        // 3. Sort = Latest Updates
        sortFilter.state = 1
        val reqLatest = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://rule34video.com/latest-updates/", reqLatest.url.toString())

        // 4. Genre = Blowjob page 1
        val blowjobIdx = genreFilter.values.indexOf("Blowjob")
        genreFilter.state = blowjobIdx
        val reqGenre1 = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://rule34video.com/tags/blowjob/", reqGenre1.url.toString())

        // 5. Genre = Blowjob page 2
        val reqGenre2 = source.searchAnimeRequest(2, "", filterList)
        assertEquals("https://rule34video.com/tags/blowjob/2/", reqGenre2.url.toString())

        // 6. Keyword search page 1
        val reqSearch1 = source.searchAnimeRequest(1, "peach", filterList)
        assertEquals("https://rule34video.com/search/peach/", reqSearch1.url.toString())

        // 7. Keyword search page 2
        val reqSearch2 = source.searchAnimeRequest(2, "peach", filterList)
        assertEquals("https://rule34video.com/search/peach/?from_videos=2", reqSearch2.url.toString())
    }
}
