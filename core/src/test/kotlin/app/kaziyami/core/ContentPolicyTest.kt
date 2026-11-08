package app.kaziyami.core

import eu.kanade.tachiyomi.animesource.model.SAnime
import app.kaziyami.engine.KaziyamiMedia
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentPolicyTest {

    @Test
    fun `detects minor indicators across all target languages`() {
        // English
        assertTrue(ContentPolicy.isMinorIndicator("Schoolgirl loli adventure"))
        assertTrue(ContentPolicy.isMinorIndicator("Underage girls party"))
        assertTrue(ContentPolicy.isMinorIndicator("Elementary school student"))
        assertTrue(ContentPolicy.isMinorIndicator("Cute shota in bedroom"))

        // Indonesian
        assertTrue(ContentPolicy.isMinorIndicator("Bocil viral hot link"))
        assertTrue(ContentPolicy.isMinorIndicator("Anak SD pacaran"))
        assertTrue(ContentPolicy.isMinorIndicator("Anak SMP terbaru"))
        assertTrue(ContentPolicy.isMinorIndicator("Gadis bawah umur"))

        // Japanese Romanization
        assertTrue(ContentPolicy.isMinorIndicator("Kodomo no jikan"))
        assertTrue(ContentPolicy.isMinorIndicator("Enjo kousai club"))

        // Legitimate non-violating content
        assertFalse(ContentPolicy.isMinorIndicator("Mahou Shoujo Charlotte"))
        assertFalse(ContentPolicy.isMinorIndicator("Schoolgirl Crush Episode 1"))
        assertFalse(ContentPolicy.isMinorIndicator("Jackerman 3D Blender Animation"))
        assertFalse(ContentPolicy.isMinorIndicator("Zaooaz 4K Belle x Yi Xuan"))
    }

    @Test
    fun `detects non-consensual and live-action pornography`() {
        assertTrue(ContentPolicy.isProhibitedContent("Live-action JAV cosplay"))
        assertTrue(ContentPolicy.isProhibitedContent("Hidden cam spy voyeur"))
        assertTrue(ContentPolicy.isProhibitedContent("Deepfake celebrity leak"))
        assertTrue(ContentPolicy.isProhibitedContent("Kamera tersembunyi hotel"))
        assertTrue(ContentPolicy.isProhibitedContent("Skandal nyata viral"))

        assertFalse(ContentPolicy.isProhibitedContent("Full 3D SFM Rendered Episode"))
        assertFalse(ContentPolicy.isProhibitedContent("Animated Hentai Episode 2"))
    }

    @Test
    fun `filters SAnime list correctly`() {
        val safeAnime = SAnime.create().apply {
            title = "Jackerman 3D Feature"
            genre = "3D, Animation, Fantasy"
        }
        val minorAnime = SAnime.create().apply {
            title = "Forbidden Loli Video"
            genre = "Animation"
        }
        val liveActionAnime = SAnime.create().apply {
            title = "Live Action Idol"
            genre = "JAV, Real Life"
        }

        val filtered = ContentPolicy.filterAnimes(listOf(safeAnime, minorAnime, liveActionAnime))
        assertEquals(1, filtered.size)
        assertEquals("Jackerman 3D Feature", filtered[0].title)
    }

    @Test
    fun `filters KaziyamiMedia list correctly`() {
        val safeMedia = KaziyamiMedia(
            id = "/video/1",
            title = "Jackerman 3D Feature",
            url = "/video/1",
            coverUrl = "",
            status = 0,
            genres = listOf("3D", "Animation"),
            description = "Clean 3D animation"
        )
        val prohibitedMedia = KaziyamiMedia(
            id = "/video/2",
            title = "Spy Cam Hotel",
            url = "/video/2",
            coverUrl = "",
            status = 0,
            genres = listOf("Voyeur"),
            description = "Hidden camera video"
        )

        val filtered = ContentPolicy.filterMedia(listOf(safeMedia, prohibitedMedia))
        assertEquals(1, filtered.size)
        assertEquals("Jackerman 3D Feature", filtered[0].title)
    }
}

