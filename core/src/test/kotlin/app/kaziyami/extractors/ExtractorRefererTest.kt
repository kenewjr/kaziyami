package app.kaziyami.extractors

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExtractorRefererTest {

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
    fun testDirectVideoExtractorAttachesReferer() {
        val extractor = DirectVideoExtractor()
        val streamUrl = server.url("/video.mp4").toString()
        val referer = "https://example.com/watch/episode-1"

        val videos = extractor.extract(streamUrl, referer, client)
        assertEquals(1, videos.size)
        assertEquals(referer, videos[0].headers?.get("Referer"))
    }

    @Test
    fun testStreamTapeExtractorSendsRefererAndAttachesEmbedReferer() {
        val extractor = StreamTapeExtractor()
        val html = """<div id="videolink">'//streamtape.com/get_video?id=123&token=abc'</div>"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val embedUrl = server.url("/e/test123").toString()
        val customReferer = "https://example.com/anime-detail"

        val videos = extractor.extract(embedUrl, customReferer, client)
        assertEquals(1, videos.size)

        val recorded = server.takeRequest()
        assertEquals(customReferer, recorded.getHeader("Referer"))
        assertEquals(embedUrl, videos[0].headers?.get("Referer"))
    }

    @Test
    fun testVoeExtractorSendsReferer() {
        val extractor = VoeExtractor()
        val html = """var sources = {'hls': 'https://voe.sx/engine/hls.m3u8'};"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val embedUrl = server.url("/e/voe123").toString()
        val customReferer = "https://parent.org/hentai/ep1"

        val videos = extractor.extract(embedUrl, customReferer, client)
        assertEquals(1, videos.size)

        val recorded = server.takeRequest()
        assertEquals(customReferer, recorded.getHeader("Referer"))
        assertEquals(embedUrl, videos[0].headers?.get("Referer"))
    }

    @Test
    fun testMp4UploadExtractorSendsReferer() {
        val extractor = Mp4UploadExtractor()
        val html = """player.src("https://mp4upload.com/stream.mp4");"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val embedUrl = server.url("/embed-mp4123.html").toString()
        val customReferer = "https://example.com/watch"

        val videos = extractor.extract(embedUrl, customReferer, client)
        assertEquals(1, videos.size)

        val recorded = server.takeRequest()
        assertEquals(customReferer, recorded.getHeader("Referer"))
        assertEquals(embedUrl, videos[0].headers?.get("Referer"))
    }

    @Test
    fun testStreamWishExtractorSendsReferer() {
        val extractor = StreamWishExtractor()
        val html = """sources: [{'file': 'https://streamwish.to/hls.m3u8'}]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val embedUrl = server.url("/e/sw123").toString()
        val customReferer = "https://example.com/watch"

        val videos = extractor.extract(embedUrl, customReferer, client)
        assertEquals(1, videos.size)

        val recorded = server.takeRequest()
        assertEquals(customReferer, recorded.getHeader("Referer"))
        assertEquals(embedUrl, videos[0].headers?.get("Referer"))
    }

    @Test
    fun testStreamPoiExtractorSendsCustomReferer() {
        val extractor = StreamPoiExtractor()
        // eval packed script
        val packed = "eval(function(p,a,c,k,e,d){return p}('sources:[{file:\"https://streampoi.com/master.m3u8\"}]',10,1,''.split('|')))"
        server.enqueue(MockResponse().setResponseCode(200).setBody(packed))

        val embedUrl = server.url("/play/sp123").toString()
        val customReferer = "https://nekopoi.care/hentai/test-1"

        val videos = extractor.extract(embedUrl, customReferer, client)
        assertFalse(videos.isEmpty())

        val recorded = server.takeRequest()
        assertEquals(customReferer, recorded.getHeader("Referer"))
    }
}

