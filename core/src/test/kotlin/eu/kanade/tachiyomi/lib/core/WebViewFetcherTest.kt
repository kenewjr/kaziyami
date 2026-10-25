package eu.kanade.tachiyomi.lib.core

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class WebViewFetcherTest {

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
    fun defaultWebViewFetcherLoadsHtml() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body>Rendered HTML</body></html>"))

        val fetcher = DefaultWebViewFetcher(client)
        val html = fetcher.fetchHtml(server.url("/js-page").toString())
        assertEquals("<html><body>Rendered HTML</body></html>", html)
    }

    @Test
    fun defaultWebViewFetcherThrowsOnFailure() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val fetcher = DefaultWebViewFetcher(client)
        assertThrows(IOException::class.java) {
            runBlocking {
                fetcher.fetchHtml(server.url("/error").toString())
            }
        }
    }
}
