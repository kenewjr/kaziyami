package app.kaziyami.extractors

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SignedVideoResolverTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun returnsOriginalUrlIfValid() {
        server.enqueue(MockResponse().setResponseCode(200))

        val videoUrl = server.url("/stream.mp4?sig=valid1").toString()
        val resolveCalls = AtomicInteger(0)

        val resolved = VideoResolverHelper.resolveWithRefresh(
            pageUrl = "https://site.com/video/1",
            initialVideoUrl = videoUrl,
            client = client,
            resolver = {
                resolveCalls.incrementAndGet()
                "https://site.com/fresh.mp4"
            }
        )

        assertEquals(videoUrl, resolved)
        assertEquals(0, resolveCalls.get())
    }

    @Test
    fun reResolvesOnceOn403ExpiredToken() {
        // First check returns 403 Forbidden
        server.enqueue(MockResponse().setResponseCode(403))
        // Fresh check returns 200 OK
        server.enqueue(MockResponse().setResponseCode(200))

        val expiredUrl = server.url("/stream.mp4?sig=expired").toString()
        val freshUrl = server.url("/stream.mp4?sig=fresh99").toString()
        val resolveCalls = AtomicInteger(0)

        val resolved = VideoResolverHelper.resolveWithRefresh(
            pageUrl = "https://site.com/video/1",
            initialVideoUrl = expiredUrl,
            client = client,
            resolver = {
                resolveCalls.incrementAndGet()
                freshUrl
            }
        )

        assertEquals(freshUrl, resolved)
        assertEquals(1, resolveCalls.get())
    }
}
