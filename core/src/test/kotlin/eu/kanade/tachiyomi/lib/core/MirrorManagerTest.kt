package eu.kanade.tachiyomi.lib.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MirrorManagerTest {

    @Test
    fun defaultBaseUrlReturned() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            mirrors = listOf("https://mirror1.example.com", "https://mirror2.example.com")
        )
        assertEquals("https://primary.example.com", manager.currentBase())
        assertEquals("https://primary.example.com", manager.getBaseUrl())
    }

    @Test
    fun failoverSelectsNextWorkingMirror() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            mirrors = listOf("https://mirror1.example.com", "https://mirror2.example.com")
        )

        val next = manager.failover("https://primary.example.com")
        assertEquals("https://mirror1.example.com", next)
        assertEquals("https://mirror1.example.com", manager.currentBase())
    }

    @Test
    fun explicitOverrideRespected() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            mirrors = listOf("https://mirror1.example.com")
        )

        manager.setOverrideUrl("https://custom.override.com")
        assertEquals("https://custom.override.com", manager.currentBase())

        // Clearing override returns to active mirror
        manager.setOverrideUrl(null)
        assertEquals("https://primary.example.com", manager.currentBase())
    }

    @Test
    fun mirrorListingContainsAll() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            mirrors = listOf("https://mirror1.example.com")
        )
        val all = manager.allMirrors
        assertEquals(2, all.size)
        assertTrue(all.contains("https://primary.example.com"))
        assertTrue(all.contains("https://mirror1.example.com"))
    }

    @Test
    fun markFailedClearsHealthyMirror() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            mirrors = listOf("https://mirror1.example.com")
        )
        manager.markHealthy("https://mirror1.example.com")
        assertEquals("https://mirror1.example.com", manager.currentBase())

        manager.markFailed("https://mirror1.example.com")
        assertEquals("https://primary.example.com", manager.currentBase())
    }

    @Test
    fun threadSafeConcurrentAccess() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            mirrors = listOf("https://mirror1.example.com", "https://mirror2.example.com")
        )
        val executor = Executors.newFixedThreadPool(8)
        for (i in 0 until 100) {
            executor.submit {
                manager.currentBase()
                if (i % 2 == 0) manager.failover()
                if (i % 3 == 0) manager.markHealthy("https://mirror2.example.com")
            }
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    }

    @Test
    fun preferenceProviderRespected() {
        var prefValue: String? = "https://pref.example.com"
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            preferences = { prefValue }
        )
        assertEquals("https://pref.example.com", manager.currentBase())

        prefValue = null
        assertEquals("https://primary.example.com", manager.currentBase())
    }

    @Test
    fun markUnhealthyClearsMirror() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            mirrors = listOf("https://backup.example.com")
        )
        manager.markHealthy("https://backup.example.com")
        assertEquals("https://backup.example.com", manager.currentBase())

        manager.markUnhealthy("https://backup.example.com")
        assertEquals("https://primary.example.com", manager.currentBase())
    }

    @Test
    fun getWorkingMirrorDetectsResponsiveEndpoint() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200))
        server.start()

        try {
            val serverUrl = server.url("/").toString().removeSuffix("/")
            val manager = MirrorManager(
                defaultBaseUrl = "http://127.0.0.1:1", // dead port
                mirrors = listOf(serverUrl)
            )
            val client = okhttp3.OkHttpClient()
            val working = manager.getWorkingMirror(client)
            assertEquals(serverUrl, working)
            assertEquals(serverUrl, manager.currentBase())
        } finally {
            server.shutdown()
        }
    }
}
