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

class VideoExtractorsTest {

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
    fun directVideoExtractorWorks() {
        val ext = DirectVideoExtractor()
        assertTrue(ext.canExtract("https://example.com/video.mp4"))
        assertTrue(ext.canExtract("https://example.com/stream.m3u8"))
        assertFalse(ext.canExtract("https://example.com/page.html"))

        val videos = ext.extract("https://example.com/video.mp4", client)
        assertEquals(1, videos.size)
        assertEquals("https://example.com/video.mp4", videos[0].videoUrl)
    }

    @Test
    fun streamTapeExtractorParsesRobotLink() {
        val ext = StreamTapeExtractor()
        assertTrue(ext.canExtract("https://streamtape.com/e/abc123xyz/"))

        val html = """
            <html>
            <body>
                <div id="robotlink">https://streamtape.com/get_video?id=123&token=xyz</div>
            </body>
            </html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val videos = ext.extract(server.url("/e/abc123xyz").toString(), client)
        assertEquals(1, videos.size)
        assertTrue(videos[0].videoUrl!!.contains("get_video"))
    }

    @Test
    fun doodExtractorParsesPassMd5() {
        val ext = DoodExtractor()
        assertTrue(ext.canExtract("https://dood.so/e/dood123"))

        server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body>/pass_md5/token123</body></html>"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("https://doodstream.net/video_stream_direct"))

        val videos = ext.extract(server.url("/e/dood123").toString(), client)
        assertEquals(1, videos.size)
        assertTrue(videos[0].videoUrl!!.contains("doodstream.net"))
    }

    @Test
    fun voeExtractorParsesHlsSource() {
        val ext = VoeExtractor()
        assertTrue(ext.canExtract("https://voe.sx/e/voe123"))

        val html = """
            <html><body>
                <script>'hls': 'https://delivery.voe.sx/hls/master.m3u8'</script>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val videos = ext.extract(server.url("/e/voe123").toString(), client)
        assertEquals(1, videos.size)
        assertEquals("https://delivery.voe.sx/hls/master.m3u8", videos[0].videoUrl)
    }

    @Test
    fun mp4UploadExtractorParsesScriptPlayer() {
        val ext = Mp4UploadExtractor()
        assertTrue(ext.canExtract("https://www.mp4upload.com/embed-123.html"))

        val html = """
            <html><body>
                <script>player.src("https://mp4upload.com/files/123/video.mp4");</script>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val videos = ext.extract(server.url("/embed-123.html").toString(), client)
        assertEquals(1, videos.size)
        assertEquals("https://mp4upload.com/files/123/video.mp4", videos[0].videoUrl)
    }

    @Test
    fun streamWishExtractorParsesJwPlayerSource() {
        val ext = StreamWishExtractor()
        assertTrue(ext.canExtract("https://streamwish.to/e/sw123"))

        val html = """
            <html><body>
                <script>sources: [{"file":"https://streamwish.to/hls/manifest.m3u8"}]</script>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val videos = ext.extract(server.url("/e/sw123").toString(), client)
        assertEquals(1, videos.size)
        assertEquals("https://streamwish.to/hls/manifest.m3u8", videos[0].videoUrl)
    }

    @Test
    fun filemoonExtractorParsesIframeOrFile() {
        val ext = FilemoonExtractor()
        assertTrue(ext.canExtract("https://filemoon.sx/e/moon123"))

        val html = """
            <html><body>
                <script>file:"https://filemoon.sx/hls/stream.m3u8"</script>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val videos = ext.extract(server.url("/e/moon123").toString(), client)
        assertEquals(1, videos.size)
        assertEquals("https://filemoon.sx/hls/stream.m3u8", videos[0].videoUrl)
    }

    @Test
    fun streamPoiExtractorUnpacksDeanEdwards() {
        val ext = StreamPoiExtractor()
        assertTrue(ext.canExtract("https://streampoi.com/e/poi123"))
        assertTrue(ext.canExtract("https://streamruby.net/e/ruby456"))

        // Packed jwplayer script setting sources
        val packed = """
            <script type='text/javascript'>eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c.toString(a)+'\\b','g'),k[c]);return p}('0("1").2({3:[{4:"5://6.7/8/9.a"}]});',11,11,'jwplayer|vplayer|setup|sources|file|https|streamruby|net|hls|master|m3u8'.split('|'))</script>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(packed))

        val videos = ext.extract(server.url("/e/poi123").toString(), client)
        assertEquals(2, videos.size)
        assertEquals("https://streamruby.net/hls/master.m3u8", videos[0].videoUrl)
        assertTrue(videos[0].quality.contains("1080p"))
    }
}

