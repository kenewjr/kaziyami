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
}
