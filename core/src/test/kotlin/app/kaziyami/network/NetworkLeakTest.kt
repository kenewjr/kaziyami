package app.kaziyami.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class NetworkLeakTest {

    private val forbiddenSubstrings = listOf("kaziyami", "aniyomi", "tachiyomi", "extension")

    @Test
    fun testBrowserHeadersDoNotLeakIdentity() {
        val testUrl = "https://example.com/video/1"
        val headerSets = listOf(
            BrowserHeaders.headersFor(testUrl, referer = "https://example.com", type = BrowserHeaders.HeaderType.NAVIGATE),
            BrowserHeaders.headersFor(testUrl, referer = "https://example.com", type = BrowserHeaders.HeaderType.XHR),
            BrowserHeaders.headersFor(testUrl, referer = "https://example.com", type = BrowserHeaders.HeaderType.IMAGE),
            BrowserHeaders.defaultHeaders("https://example.com")
        )

        for (headers in headerSets) {
            for (i in 0 until headers.size) {
                val name = headers.name(i).lowercase()
                val value = headers.value(i).lowercase()

                for (forbidden in forbiddenSubstrings) {
                    assertFalse(
                        name.contains(forbidden),
                        "Outgoing header name '$name' leaks prohibited substring '$forbidden'"
                    )
                    assertFalse(
                        value.contains(forbidden),
                        "Outgoing header value '$name: $value' leaks prohibited substring '$forbidden'"
                    )
                }
            }
        }
    }

    @Test
    fun testDefaultUserAgentIsPureStandardBrowser() {
        val ua = BrowserHeaders.DEFAULT_USER_AGENT.lowercase()
        for (forbidden in forbiddenSubstrings) {
            assertFalse(
                ua.contains(forbidden),
                "Default User-Agent '$ua' leaks prohibited substring '$forbidden'"
            )
        }
    }

    @Test
    fun testExtractorHeadersAndCookieJarDoNotLeakIdentity() {
        val cookieJar = SimpleCookieJar()
        val httpUrl = "https://example.com/stream/".toHttpUrlOrNull() ?: return
        val cookie = okhttp3.Cookie.Builder()
            .domain("example.com")
            .name("session")
            .value("standard_anon_session_token_12345")
            .build()
        cookieJar.saveFromResponse(httpUrl, listOf(cookie))

        val loaded = cookieJar.loadForRequest(httpUrl)
        for (c in loaded) {
            val fullCookie = "${c.name}=${c.value}".lowercase()
            for (forbidden in forbiddenSubstrings) {
                assertFalse(
                    fullCookie.contains(forbidden),
                    "Cookie data '$fullCookie' leaks prohibited substring '$forbidden'"
                )
            }
        }
    }

    @Test
    fun testOutgoingRequestBodyDoesNotLeakIdentity() {
        val formBody = okhttp3.FormBody.Builder()
            .add("action", "get_player")
            .add("post_id", "42")
            .build()

        val buffer = okio.Buffer()
        formBody.writeTo(buffer)
        val bodyContent = buffer.readUtf8().lowercase()

        for (forbidden in forbiddenSubstrings) {
            assertFalse(
                bodyContent.contains(forbidden),
                "Request body '$bodyContent' leaks prohibited substring '$forbidden'"
            )
        }
    }

    @Test
    fun testKototoroAdapterHeadersDoNotLeakIdentity() {
        val fakeSource = object : app.kaziyami.engine.KaziyamiSource {
            override val id = 123L
            override val name = "Nekopoi"
            override val lang = "id"
            override val baseUrl = "https://nekopoi.care"
            override fun popular(page: Int) = app.kaziyami.engine.KaziyamiPage(emptyList<app.kaziyami.engine.KaziyamiMedia>(), false)
            override fun latest(page: Int) = app.kaziyami.engine.KaziyamiPage(emptyList<app.kaziyami.engine.KaziyamiMedia>(), false)
            override fun search(page: Int, query: String, filters: List<Any>) = app.kaziyami.engine.KaziyamiPage(emptyList<app.kaziyami.engine.KaziyamiMedia>(), false)
            override fun details(url: String) = app.kaziyami.engine.KaziyamiMedia("1", "Title", url)
            override fun episodes(url: String) = emptyList<app.kaziyami.engine.KaziyamiEpisode>()
            override fun videos(url: String) = listOf(
                app.kaziyami.engine.KaziyamiVideo(url, "720p", "https://streampoi.com/video.mp4", mapOf("Referer" to url, "User-Agent" to BrowserHeaders.DEFAULT_USER_AGENT))
            )
        }
        val wrapper = app.kaziyami.adapter.kototoro.KototoroSourceWrapper(fakeSource)
        val dummyResp = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://nekopoi.care/hentai/1/ep1").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(okhttp3.ResponseBody.create(null, ""))
            .build()
        val videos = wrapper.videoListParse(dummyResp)
        for (v in videos) {
            val headers = v.headers ?: continue
            for (i in 0 until headers.size) {
                val name = headers.name(i).lowercase()
                val value = headers.value(i).lowercase()
                for (forbidden in forbiddenSubstrings) {
                    assertFalse(name.contains(forbidden), "Kototoro adapter header '$name: $value' leaks prohibited substring '$forbidden'")
                    assertFalse(value.contains(forbidden), "Kototoro adapter header '$name: $value' leaks prohibited substring '$forbidden'")
                }
            }
        }
    }
}
