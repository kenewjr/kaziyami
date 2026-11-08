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

    private val source = Hentaimama().apply {
        preferences.edit().putBoolean("age_gate_confirmed", true).apply()
    }

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
    fun parseAnimeDetailsMetaDescription() {
        val html = """
            <html>
            <head>
                <meta property="og:description" content="Watch Hitori Tsuma hentai online free in HD." />
            </head>
            <body>
                <div class="sheader"><div class="data"><h1>Hitori Tsuma</h1></div></div>
                <div class="poster"><img src="https://hentaimama.io/thumb.jpg" /></div>
            </body>
            </html>
        """.trimIndent()
        val response = mockResponse("https://hentaimama.io/tvshows/hitori-tsuma/", html)
        val details = source.animeDetailsParse(response)

        assertEquals("Hitori Tsuma", details.title)
        assertEquals("Watch Hitori Tsuma hentai online free in HD.", details.description)
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

    @Test
    fun testFiltersAndSearchRequest() {
        val filterList = source.getFilterList()
        val categoryFilter = filterList.filterIsInstance<Hentaimama.HentaimamaCategoryFilter>().first()
        val genreFilter = filterList.filterIsInstance<Hentaimama.HentaimamaGenreFilter>().first()

        assertEquals(2, categoryFilter.values.size)
        assertTrue(genreFilter.values.size >= 28)
        assertTrue(genreFilter.values.contains("MILF"))
        assertTrue(genreFilter.values.contains("Vanilla"))

        // 1. Default request: TV Shows page 1
        val reqDefault = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://hentaimama.io/tvshows/", reqDefault.url.toString())

        // 2. Default TV Shows page 2
        val reqPage2 = source.searchAnimeRequest(2, "", filterList)
        assertEquals("https://hentaimama.io/tvshows/page/2/", reqPage2.url.toString())

        // 3. Category = Episodes page 1
        categoryFilter.state = 1
        val reqEpisodes1 = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://hentaimama.io/episodes/", reqEpisodes1.url.toString())

        // 4. Category = Episodes page 2
        val reqEpisodes2 = source.searchAnimeRequest(2, "", filterList)
        assertEquals("https://hentaimama.io/episodes/page/2/", reqEpisodes2.url.toString())

        // Reset category
        categoryFilter.state = 0

        // 5. Genre = MILF page 1
        val milfIndex = genreFilter.values.indexOf("MILF")
        assertTrue(milfIndex >= 0)
        genreFilter.state = milfIndex
        val reqMilf1 = source.searchAnimeRequest(1, "", filterList)
        assertEquals("https://hentaimama.io/genre/milf/", reqMilf1.url.toString())

        // 6. Genre = MILF page 2
        val reqMilf2 = source.searchAnimeRequest(2, "", filterList)
        assertEquals("https://hentaimama.io/genre/milf/page/2/", reqMilf2.url.toString())

        // 7. Search Query overrides genre & category
        val reqQuery1 = source.searchAnimeRequest(1, "itadaki", filterList)
        assertEquals("https://hentaimama.io/?s=itadaki", reqQuery1.url.toString())

        val reqQuery2 = source.searchAnimeRequest(2, "itadaki", filterList)
        assertEquals("https://hentaimama.io/?s=itadaki&page=2", reqQuery2.url.toString())
    }
}
