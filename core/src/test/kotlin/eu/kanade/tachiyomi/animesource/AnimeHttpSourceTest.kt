package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AnimeHttpSourceTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private class TestParsedSource(val serverUrl: String) : ParsedAnimeHttpSource() {
        override val name: String = "TestHttpSource"
        override val lang: String = "en"
        override val baseUrl: String = serverUrl

        override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/popular?page=$page")
        override fun popularAnimeSelector(): String = "div.anime-card"
        override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
            title = element.selectFirst(".title")?.text() ?: ""
            url = element.selectFirst("a")?.attr("href") ?: ""
        }
        override fun popularAnimeNextPageSelector(): String = "a.next"

        override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest?page=$page")
        override fun latestUpdatesSelector(): String = "div.anime-card"
        override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
        override fun latestUpdatesNextPageSelector(): String = "a.next"

        override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
            GET("$baseUrl/search?q=$query&page=$page")
        override fun searchAnimeSelector(): String = "div.anime-card"
        override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
        override fun searchAnimeNextPageSelector(): String = "a.next"

        override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""
            description = document.selectFirst(".desc")?.text() ?: ""
        }

        override fun episodeListSelector(): String = "ul.eps li"
        override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
            name = element.selectFirst(".name")?.text() ?: ""
            url = element.selectFirst("a")?.attr("href") ?: ""
        }

        override fun videoListSelector(): String = "div.player"
        override fun videoFromElement(element: Element): Video = Video("https://cdn.example.com/v.mp4", "1080p", "https://cdn.example.com/v.mp4")
        override fun videoUrlParse(document: Document): String = "https://cdn.example.com/v.mp4"
        override fun videoListParse(response: Response): List<Video> = listOf(Video("https://cdn.example.com/v.mp4", "1080p", "https://cdn.example.com/v.mp4"))
    }

    @Test
    fun testAnimeHttpSourceFlow() = runBlocking {
        val testSource = TestParsedSource(server.url("/").toString().removeSuffix("/"))

        // Test GET and POST helpers
        val getReq = testSource.GET("${testSource.baseUrl}/get")
        assertEquals("GET", getReq.method)

        val postReq = testSource.POST("${testSource.baseUrl}/post", body = "test".toRequestBody("text/plain".toMediaType()))
        assertEquals("POST", postReq.method)

        // Popular Anime
        val popularHtml = """
            <html><body>
                <div class="anime-card"><a href="/anime/1"><span class="title">Anime One</span></a></div>
                <a class="next" href="/popular?page=2">Next</a>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(popularHtml))

        val popularPage = testSource.getPopularAnime(1)
        assertEquals(1, popularPage.animes.size)
        assertEquals("Anime One", popularPage.animes[0].title)
        assertTrue(popularPage.hasNextPage)

        // Latest Updates
        server.enqueue(MockResponse().setResponseCode(200).setBody(popularHtml))
        val latestPage = testSource.getLatestUpdates(1)
        assertEquals(1, latestPage.animes.size)

        // Search Anime
        server.enqueue(MockResponse().setResponseCode(200).setBody(popularHtml))
        val searchPage = testSource.getSearchAnime(1, "query", AnimeFilterList())
        assertEquals(1, searchPage.animes.size)

        // Anime Details
        val detailsHtml = "<html><body><h1>Anime One</h1><p class='desc'>Detailed Description</p></body></html>"
        server.enqueue(MockResponse().setResponseCode(200).setBody(detailsHtml))
        val details = testSource.getAnimeDetails(popularPage.animes[0])
        assertEquals("Anime One", details.title)
        assertEquals("Detailed Description", details.description)

        // Episode List
        val epHtml = "<html><body><ul class='eps'><li><a href='/ep/1'><span class='name'>Episode 1</span></a></li></ul></body></html>"
        server.enqueue(MockResponse().setResponseCode(200).setBody(epHtml))
        val episodes = testSource.getEpisodeList(popularPage.animes[0])
        assertEquals(1, episodes.size)
        assertEquals("Episode 1", episodes[0].name)

        // Video List
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body><div class='player'>video</div></body></html>"))
        val videos = testSource.getVideoList(episodes[0])
        assertEquals(1, videos.size)
        assertEquals("1080p", videos[0].quality)

        // ID generation
        assertTrue(testSource.id > 0L)
    }
}
