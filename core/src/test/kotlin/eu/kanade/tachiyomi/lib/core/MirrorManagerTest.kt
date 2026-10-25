package eu.kanade.tachiyomi.lib.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MirrorManagerTest {

    @Test
    fun defaultBaseUrlReturned() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            mirrors = listOf("https://mirror1.example.com", "https://mirror2.example.com")
        )
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
        assertEquals("https://mirror1.example.com", manager.getBaseUrl())
    }

    @Test
    fun explicitOverrideRespected() {
        val manager = MirrorManager(
            defaultBaseUrl = "https://primary.example.com",
            mirrors = listOf("https://mirror1.example.com")
        )

        manager.setOverrideUrl("https://custom.override.com")
        assertEquals("https://custom.override.com", manager.getBaseUrl())

        // Clearing override returns to active mirror
        manager.setOverrideUrl(null)
        assertEquals("https://primary.example.com", manager.getBaseUrl())
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
}
