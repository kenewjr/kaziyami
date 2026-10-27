package app.kaziyami.adapter.kototoro

import app.kaziyami.engine.KaziyamiEpisode
import app.kaziyami.engine.KaziyamiMedia
import app.kaziyami.engine.KaziyamiPage
import app.kaziyami.engine.KaziyamiSource
import app.kaziyami.engine.KaziyamiTrack
import app.kaziyami.engine.KaziyamiVideo
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.model.*
import org.skepsun.kototoro.parsers.newParser

class KototoroAdapterContractTest {

    private val dummyContext = object : ContentLoaderContext() {
        override val httpClient: OkHttpClient = OkHttpClient()
        override val cookieJar: CookieJar = CookieJar.NO_COOKIES
    }

    private class MockKaziyamiSource(
        override val id: Long = 7073589375897212735L,
        override val name: String = "MockNekopoi",
        override val lang: String = "id",
        override val baseUrl: String = "https://nekopoi.care"
    ) : KaziyamiSource {
        override fun popular(page: Int) = KaziyamiPage(
            items = listOf(
                KaziyamiMedia(id = "/hentai/test-1", title = "Test Anime 1", url = "/hentai/test-1", coverUrl = "https://nekopoi.care/cover1.jpg", status = 1, genres = listOf("Harem", "Romance"), description = "Mock synopsis")
            ),
            hasNextPage = true
        )
        override fun latest(page: Int) = popular(page)
        override fun search(page: Int, query: String, filters: List<Any>) = popular(page)
        override fun details(url: String) = KaziyamiMedia(id = url, title = "Test Anime 1", url = url, coverUrl = "https://nekopoi.care/cover1.jpg", status = 2, genres = listOf("Harem", "Romance"), description = "Full details")
        override fun episodes(url: String) = listOf(
            KaziyamiEpisode(name = "Episode 2", url = "/hentai/test-1-ep-2", episodeNumber = 2.0f, dateUpload = 1700000000000L),
            KaziyamiEpisode(name = "Episode 1", url = "/hentai/test-1-ep-1", episodeNumber = 1.0f, dateUpload = 1690000000000L)
        )
        override fun videos(url: String) = listOf(
            KaziyamiVideo(
                url = "https://streampoi.com/embed/123",
                quality = "1080p - StreamPoi",
                videoUrl = "https://streampoi.com/master.m3u8",
                headers = mapOf("Referer" to "https://nekopoi.care/"),
                subtitles = listOf(KaziyamiTrack("https://nekopoi.care/sub.vtt", "id"))
            )
        )
    }

    @BeforeEach
    fun setup() {
        for (sourceEnum in ContentParserSource.values()) {
            KototoroParserRegistry.register(sourceEnum) {
                MockKaziyamiSource(name = sourceEnum.title, lang = sourceEnum.locale)
            }
        }
    }

    @Test
    fun testAllSourcesConstructViaFactory() {
        for (sourceEnum in ContentParserSource.values()) {
            val parser = newParser(sourceEnum, dummyContext)
            assertNotNull(parser, "Parser must not be null for ${sourceEnum.name}")
            assertEquals(sourceEnum, parser.source)
            assertEquals(ContentType.HENTAI_VIDEO, parser.source.contentType)
            assertTrue(parser.domain.isNotBlank(), "Domain must be set for ${sourceEnum.name}")
            assertTrue(parser.availableSortOrders.contains(SortOrder.POPULARITY))
            assertTrue(parser.availableSortOrders.contains(SortOrder.UPDATED))
        }
    }

    @Test
    fun testKototoroParserAdapterMapping() = runBlocking {
        val mockSource = MockKaziyamiSource()
        val adapter = KototoroParserAdapter(mockSource, ContentParserSource.NEKOPOI, dummyContext)

        // 1. getList popular
        val list = adapter.getList(0, SortOrder.POPULARITY, ContentListFilter())
        assertEquals(1, list.size)
        val firstContent = list[0]
        assertEquals("Test Anime 1", firstContent.title)
        assertEquals("/hentai/test-1", firstContent.url)
        assertEquals("https://nekopoi.care/hentai/test-1", firstContent.publicUrl)
        assertEquals(ContentRating.ADULT, firstContent.contentRating)
        assertEquals(ContentState.ONGOING, firstContent.state)
        assertEquals(2, firstContent.tags.size)

        // 2. getDetails
        val detailedContent = adapter.getDetails(firstContent)
        assertEquals("Full details", detailedContent.description)
        assertNotNull(detailedContent.chapters)
        assertEquals(2, detailedContent.chapters!!.size)

        val ch1 = detailedContent.chapters!![0]
        assertEquals("Episode 2", ch1.title)
        assertEquals(2.0f, ch1.number)
        assertEquals("/hentai/test-1-ep-2", ch1.url)

        // 3. getPages (videos)
        val pages = adapter.getPages(ch1)
        assertEquals(1, pages.size)
        val page = pages[0]
        assertEquals("https://streampoi.com/master.m3u8", page.url)
        assertEquals("1080p - StreamPoi", page.playbackLabel)
        assertEquals(1080, page.playbackQuality)
        assertNotNull(page.headers)
        assertEquals("https://nekopoi.care/", page.headers!!["Referer"])
        assertEquals(1, page.externalSubtitleTracks.size)
        assertEquals("id", page.externalSubtitleTracks[0].lang)

        // 4. getPageUrl
        val streamUrl = adapter.getPageUrl(page)
        assertEquals("https://streampoi.com/master.m3u8", streamUrl)
    }
}

