package app.kaziyami.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowserProfileTest {

    @Test
    fun defaultProfileClientHintsAreConsistent() {
        val profile = BrowserProfile.DEFAULT
        assertTrue(profile.areClientHintsConsistent(), "Default profile client hints must be consistent with User-Agent")
        val hints = profile.getEffectiveClientHints()
        assertEquals(3, hints.size)
        assertEquals("\"Windows\"", hints["Sec-Ch-Ua-Platform"])
        assertEquals("?0", hints["Sec-Ch-Ua-Mobile"])
        assertTrue(hints["Sec-Ch-Ua"]!!.contains("126"))
    }

    @Test
    fun clientHintsDisagreeWithUserAgentVersionFailsConsistency() {
        // User agent says Chrome 126, but Sec-Ch-Ua says v=120
        val inconsistentProfile = BrowserProfile(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            secChUa = "\"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""
        )
        assertFalse(inconsistentProfile.areClientHintsConsistent())
        // Hints must be safely omitted when inconsistent
        assertTrue(inconsistentProfile.getEffectiveClientHints().isEmpty())
    }

    @Test
    fun clientHintsDisagreeWithPlatformFailsConsistency() {
        // User agent says Windows NT, but Sec-Ch-Ua-Platform says Android
        val platformMismatch = BrowserProfile(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            secChUaPlatform = "\"Android\""
        )
        assertFalse(platformMismatch.areClientHintsConsistent())
        assertTrue(platformMismatch.getEffectiveClientHints().isEmpty())
    }

    @Test
    fun clientHintsDisagreeWithMobileFlagFailsConsistency() {
        // Desktop Windows UA with Mobile = ?1
        val mobileMismatch = BrowserProfile(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            secChUaMobile = "?1"
        )
        assertFalse(mobileMismatch.areClientHintsConsistent())
        assertTrue(mobileMismatch.getEffectiveClientHints().isEmpty())
    }

    @Test
    fun headerOrderIsDeterministicAcrossCalls() {
        val h1 = BrowserHeaders.headersFor("https://example.com/item", referer = "https://example.com", type = BrowserHeaders.HeaderType.NAVIGATE)
        val h2 = BrowserHeaders.headersFor("https://another.com/detail", referer = "https://another.com", type = BrowserHeaders.HeaderType.NAVIGATE)

        val names1 = (0 until h1.size).map { h1.name(it) }
        val names2 = (0 until h2.size).map { h2.name(it) }

        assertEquals(names1, names2, "Header order must be 100% deterministic across all requests of the same type")

        // Assert expected exact canonical header ordering
        val expectedPrefix = listOf("User-Agent", "Accept", "Accept-Language", "Accept-Encoding", "Referer", "Sec-Ch-Ua")
        for (i in expectedPrefix.indices) {
            assertEquals(expectedPrefix[i], names1[i])
        }
    }
}
