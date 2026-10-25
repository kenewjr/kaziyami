package app.kaziyami.network

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
}
