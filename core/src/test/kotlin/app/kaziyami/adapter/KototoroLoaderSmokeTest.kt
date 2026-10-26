package app.kaziyami.adapter

import app.kaziyami.adapter.kototoro.KototoroPlugin
import app.kaziyami.adapter.kototoro.KototoroSourceWrapper
import app.kaziyami.engine.DefaultHttpClientProvider
import app.kaziyami.engine.KaziyamiEpisode
import app.kaziyami.engine.KaziyamiMedia
import app.kaziyami.engine.KaziyamiPage
import app.kaziyami.engine.KaziyamiSource
import app.kaziyami.engine.KaziyamiVideo
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KototoroLoaderSmokeTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        KototoroPlugin.clear()
    }

    @Test
    fun testReflectionLoadingAndMockServerListing() {
        // 1. Simulate Kototoro classloader reflection loading
        val pluginClass = Class.forName("app.kaziyami.adapter.kototoro.KototoroPlugin")
        assertNotNull(pluginClass, "Plugin class must be loadable via reflection")

        // 2. Mock web response from source
        val baseUrl = server.url("/").toString().removeSuffix("/")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    <div class="anime-card">
                        <a href="/hentai/test-1">
                            <img src="/cover.jpg" />
                            <h3>Test Popular Anime</h3>
                        </a>
                    </div>
                """.trimIndent())
        )

        val liveSource = object : KaziyamiSource {
            override val id: Long = 987654321L
            override val name: String = "MockKototoroSource"
            override val lang: String = "id"
            override val baseUrl: String = baseUrl

            override fun popular(page: Int): KaziyamiPage<KaziyamiMedia> {
                val req = Request.Builder().url("$baseUrl/popular").build()
                val resp = OkHttpClient().newCall(req).execute()
                val html = resp.body?.string().orEmpty()
                val hasMatch = html.contains("Test Popular Anime")
                val items = if (hasMatch) {
                    listOf(KaziyamiMedia("1", "Test Popular Anime", "/hentai/test-1", "$baseUrl/cover.jpg"))
                } else emptyList()
                return KaziyamiPage(items, false)
            }

            override fun latest(page: Int) = popular(page)
            override fun search(page: Int, query: String, filters: List<Any>) = popular(page)
            override fun details(url: String) = KaziyamiMedia("1", "Test Popular Anime", url)
            override fun episodes(url: String) = emptyList<KaziyamiEpisode>()
            override fun videos(url: String) = emptyList<KaziyamiVideo>()
        }

        KototoroPlugin.registerSource(liveSource)
        assertEquals(1, KototoroPlugin.getSources().size)

        // 3. Exercise wrapper methods as Kototoro AniyomiSourceRuntime would
        val wrapper = KototoroSourceWrapper(liveSource)
        val dummyResp = Response.Builder()
            .request(Request.Builder().url("$baseUrl/popular").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()

        val parsedPage = wrapper.popularAnimeParse(dummyResp)
        assertEquals(1, parsedPage.animes.size)
        assertEquals("Test Popular Anime", parsedPage.animes[0].title)
        assertEquals("/hentai/test-1", parsedPage.animes[0].url)
        assertEquals("$baseUrl/cover.jpg", parsedPage.animes[0].thumbnail_url)
    }
}
