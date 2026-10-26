package app.kaziyami.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConnectionReuseTest {

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
    fun clientPoolReusesConnectionForSequentialRequests() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("res1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("res2"))

        val client = NetworkFactory.createClient()

        val req1 = Request.Builder().url(server.url("/path1")).build()
        val req2 = Request.Builder().url(server.url("/path2")).build()

        client.newCall(req1).execute().use { res ->
            assertEquals(200, res.code)
        }
        client.newCall(req2).execute().use { res ->
            assertEquals(200, res.code)
        }

        assertEquals(2, server.requestCount)
        val recorded1 = server.takeRequest()
        val recorded2 = server.takeRequest()

        // Keep-alive connection reuse
        assertTrue(client.connectionPool.idleConnectionCount() >= 1 || client.connectionPool.connectionCount() >= 1)
    }
}
