package app.kaziyami.core

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MirrorResilienceTest {

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
    fun testFailoverOrderAndRecovery() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.nekopoi.care",
            mirrors = listOf("https://backup1.nekopoi.app", "https://backup2.nekopoi.link")
        )

        assertEquals("https://primary.nekopoi.care", manager.currentBase())

        // Simulate failure on primary
        val next = manager.failover("https://primary.nekopoi.care")
        assertEquals("https://backup1.nekopoi.app", next)
        assertEquals("https://backup1.nekopoi.app", manager.currentBase())

        // Recovery: Primary comes back healthy
        manager.markHealthy("https://primary.nekopoi.care")
        assertEquals("https://primary.nekopoi.care", manager.currentBase())
    }

    @Test
    fun testUserManualDomainOverridePrioritized() {
        var preferenceVal = "https://user-override.nekopoi.app"
        val manager = MirrorManager(
            defaultBaseUrl = "https://default.nekopoi.care",
            mirrors = listOf("https://backup.nekopoi.link"),
            preferences = { preferenceVal }
        )

        assertEquals("https://user-override.nekopoi.app", manager.currentBase())

        // Manual in-memory override beats preference
        manager.setOverrideUrl("https://manual.nekopoi.site")
        assertEquals("https://manual.nekopoi.site", manager.currentBase())
    }

    @Test
    fun testRelativeUrlStorageUnaffectedByMirrorChange() {
        val cleanUrl = UrlUtils.stripTrackingParams("https://old-domain.com/anime/overflow-ep-1/?utm_source=tracker")
        val path = UrlUtils.getPath(cleanUrl)
        assertEquals("/anime/overflow-ep-1/", path)

        // Resolving relative path against old mirror vs new mirror
        val oldResolved = UrlUtils.resolve("https://old-domain.com", path)
        val newResolved = UrlUtils.resolve("https://new-mirror.app", path)

        assertEquals("https://old-domain.com/anime/overflow-ep-1/", oldResolved)
        assertEquals("https://new-mirror.app/anime/overflow-ep-1/", newResolved)
    }

    @Test
    fun testRemoteMirrorAllowlistValidation() {
        val jsonPayload = """
            {
              "nekopoi": [
                "https://cdn.nekopoi.app",
                "https://malicious-site.com",
                "http://insecure.nekopoi.care",
                "https://nekopoi.link"
              ]
            }
        """.trimIndent()

        val mirrors = RemoteMirrorFetcher.parseAndValidate(jsonPayload, "nekopoi")

        // Only valid HTTPS approved domains accepted
        assertEquals(2, mirrors.size)
        assertTrue(mirrors.contains("https://cdn.nekopoi.app"))
        assertTrue(mirrors.contains("https://nekopoi.link"))
        assertFalse(mirrors.contains("https://malicious-site.com"))
        assertFalse(mirrors.contains("http://insecure.nekopoi.care"))
    }

    @Test
    fun testRemoteMirrorFetcherWithMockServer() {
        val json = """
            {
              "hanime": [
                "https://hanime.tv",
                "https://guest.freeanimehentai.net"
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val client = OkHttpClient.Builder().build()
        val mirrors = RemoteMirrorFetcher.fetchMirrors(
            sourceSlug = "hanime",
            repoUrl = server.url("/mirrors.json").toString(),
            client = client
        )

        assertEquals(2, mirrors.size)
        assertTrue(mirrors.contains("https://hanime.tv"))
        assertTrue(mirrors.contains("https://guest.freeanimehentai.net"))
    }
}
