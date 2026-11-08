package eu.kanade.tachiyomi.animeextension.id.nekopoi

import eu.kanade.tachiyomi.animesource.model.SAnime
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class NekopoiTest {

    private val source = Nekopoi().apply {
        preferences.edit().putBoolean("age_gate_confirmed", true).apply()
    }

    private fun loadFixture(path: String): String {
        val stream = javaClass.getResourceAsStream("/nekopoi/$path")
            ?: throw IllegalArgumentException("Fixture /nekopoi/$path not found")
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
        val response = mockResponse("https://nekopoi.care/category/hentai/page/1/", html)
        val page = source.popularAnimeParse(response)

        assertEquals(2, page.animes.size)
        assertTrue(page.hasNextPage)

        val first = page.animes[0]
        assertEquals("Kanojo x Kanojo Episode 1", first.title)
        assertEquals("/hentai/kanojo-x-kanojo-episode-1/", first.url)
        assertEquals("https://nekopoi.care/thumbs/kanojo1.jpg", first.thumbnail_url)
    }

    @Test
    fun parseAnimeDetails() {
        val html = loadFixture("details.html")
        val response = mockResponse("https://nekopoi.care/hentai/kanojo-x-kanojo/", html)
        val details = source.animeDetailsParse(response)

        assertEquals("Kanojo x Kanojo", details.title)
        assertEquals("https://nekopoi.care/posters/kanojo.jpg", details.thumbnail_url)
        assertTrue(details.description!!.contains("Sinopsis anime dewasa ini"))
        assertEquals("Romance, Harem", details.genre)
        assertEquals(SAnime.COMPLETED, details.status)
        assertEquals("Studio: Poppuri", details.author)
    }

    @Test
    fun parseEpisodeList() {
        val html = loadFixture("episodes.html")
        val response = mockResponse("https://nekopoi.care/hentai/kanojo-x-kanojo/", html)
        val episodes = source.episodeListParse(response)

        assertEquals(2, episodes.size)
        assertEquals(2.0f, episodes[0].episode_number)
        assertEquals(1.0f, episodes[1].episode_number)
        assertTrue(episodes[0].date_upload > 0L)
    }

    @Test
    fun parseVideoList() {
        val html = loadFixture("video_player.html")
        val response = mockResponse("https://nekopoi.care/hentai/kanojo-ep-1/", html)
        val videos = source.videoListParse(response)

        assertNotNull(videos)
    }

    @Test
    fun parseSearchAnime() {
        val html = loadFixture("search.html")
        val response = mockResponse("https://nekopoi.care/?s=kanojo&page=1", html)
        val page = source.searchAnimeParse(response)

        assertEquals(1, page.animes.size)
        assertEquals("Kanojo x Kanojo Episode 1", page.animes[0].title)
    }

    @Test
    fun parseSearchEmpty() {
        val html = loadFixture("search_empty.html")
        val response = mockResponse("https://nekopoi.care/?s=unknown&page=1", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test
    fun mirrorFailover() {
        assertEquals("https://nekopoi.care", source.baseUrl)
        val next = source.mirrorManager.failover("https://nekopoi.care")
        assertEquals("https://nekopoi.app", next)
        assertEquals("https://nekopoi.app", source.baseUrl)
    }

    @Test
    fun testFiltersAndSearchRequest() {
        val filterList = source.getFilterList()
        val catFilter = filterList.filterIsInstance<Nekopoi.NekopoiCategoryFilter>().first()
        val genreFilter = filterList.filterIsInstance<Nekopoi.NekopoiGenreFilter>().first()

        assertEquals(4, catFilter.values.size)
        assertTrue(genreFilter.values.size >= 70)
        assertTrue(genreFilter.values.contains("Romance"))
        assertTrue(genreFilter.values.contains("Milf") || genreFilter.values.contains("MILF"))

        // 1. Default request
        val reqDefault = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://nekopoi.care/category/hentai/page/1/", reqDefault.url.toString())

        // 2. Category = 3D Hentai
        catFilter.state = 2
        val req3D = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://nekopoi.care/category/3d-hentai/page/1/", req3D.url.toString())

        // 3. Genre = Romance page 1
        val romanceIdx = genreFilter.values.indexOf("Romance")
        genreFilter.state = romanceIdx
        val reqRomance1 = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://nekopoi.care/genres/romance/", reqRomance1.url.toString())

        // 4. Genre = Romance page 2
        val reqRomance2 = source.searchAnimeRequest(2, "", filterList)
        assertEquals("https://nekopoi.care/genres/romance/page/2/", reqRomance2.url.toString())

        // 5. Query overrides filters
        val reqQuery = source.searchAnimeRequest(1, "kanojo", filterList)
        assertEquals("https://nekopoi.care/search/kanojo/page/1/", reqQuery.url.toString())
    }
}
