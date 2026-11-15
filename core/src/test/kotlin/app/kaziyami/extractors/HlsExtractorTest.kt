package app.kaziyami.extractors

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HlsExtractorTest {

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
    fun extractsMultipleQualityVariantStreamsFromMasterPlaylist() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080
            1080p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            720p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=854x480
            480p/index.m3u8
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(masterPlaylist))

        val masterUrl = server.url("/hls/master.m3u8").toString()
        val headers = Headers.Builder().build()
        val videos = HlsExtractor.extract(masterUrl, headers, client, prefix = "StreamPoi")

        assertEquals(3, videos.size)
        assertEquals("StreamPoi 1080p", videos[0].quality)
        assertTrue(videos[0].videoUrl.orEmpty().endsWith("/hls/1080p/index.m3u8"))
        assertEquals("StreamPoi 720p", videos[1].quality)
        assertTrue(videos[1].videoUrl.orEmpty().endsWith("/hls/720p/index.m3u8"))
        assertEquals("StreamPoi 480p", videos[2].quality)
        assertTrue(videos[2].videoUrl.orEmpty().endsWith("/hls/480p/index.m3u8"))
    }

    @Test
    fun returnsEmptyOnNonMasterPlaylist() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            segment0.ts
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(mediaPlaylist))

        val url = server.url("/hls/media.m3u8").toString()
        val videos = HlsExtractor.extract(url, Headers.Builder().build(), client)

        assertTrue(videos.isEmpty())
    }
}
