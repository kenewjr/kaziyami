package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class NetworkInterceptorTest {

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

    @Test
    fun browserHeaderInterceptorAddsStandardHeaders() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = OkHttpClient.Builder()
            .addInterceptor(BrowserHeaderInterceptor { "https://ref.example.com" })
            .build()

        val request = Request.Builder()
            .url(server.url("/test"))
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals(BrowserHeaders.DEFAULT_USER_AGENT, recorded!!.getHeader("User-Agent"))
        assertEquals("en-US,en;q=0.9,id;q=0.8", recorded.getHeader("Accept-Language"))
        assertEquals("https://ref.example.com", recorded.getHeader("Referer"))
    }

    @Test
    fun cloudflareInterceptorDetectsMitigatedHeader() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setBody("challenge html")
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CloudflareInterceptor())
            .build()

        val request = Request.Builder().url(server.url("/cf")).build()

        val ex = assertThrows(CloudflareChallengeException::class.java) {
            client.newCall(request).execute().use { }
        }
        assertTrue(ex.message!!.contains("Cloudflare challenge detected"))
    }

    @Test
    fun cloudflareInterceptorDetectsChallengePageBody() {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Server", "cloudflare")
                .setBody("<html><head><title>Just a moment...</title></head><body>cf challenge</body></html>")
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CloudflareInterceptor())
            .build()

        val request = Request.Builder().url(server.url("/cf2")).build()

        val ex = assertThrows(CloudflareChallengeException::class.java) {
            client.newCall(request).execute().use { }
        }
        assertTrue(ex.message!!.contains("Cloudflare challenge detected"))
    }

    @Test
    fun retryAfterInterceptorRetriesOn429() {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "1")
                .setBody("Too Many Requests")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Success After Retry")
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(RetryAfterInterceptor(maxRetries = 2))
            .build()

        val request = Request.Builder().url(server.url("/rate-limit")).build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("Success After Retry", response.body?.string())
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun simpleCookieJarStoresAndRetrievesCookies() {
        val jar = SimpleCookieJar()
        val url = "https://example.com/test".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("cf_clearance")
            .value("token123")
            .domain("example.com")
            .expiresAt(System.currentTimeMillis() + 3600_000L)
            .build()

        jar.saveFromResponse(url, listOf(cookie))

        val loaded = jar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("cf_clearance", loaded[0].name)
        assertEquals("token123", loaded[0].value)
    }
}
