package app.kaziyami.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ErrorClassifierTest {

    @Test
    fun testCloudflareChallengeMapping() {
        val err = ErrorClassifier.classify(
            httpCode = 403,
            headers = mapOf("Server" to "cloudflare"),
            bodySnippet = "Just a moment... Enable JavaScript and cookies to continue."
        )

        assertEquals(ErrorClassifier.ErrorType.CHALLENGE_REQUIRED, err.type)
        assertTrue(err.isRecoverableInWebView)
        assertTrue(err.message.contains("Open the site in WebView"))
    }

    @Test
    fun testGeoRestrictedMapping() {
        val err = ErrorClassifier.classify(
            httpCode = 451,
            bodySnippet = "This content is not available in your country."
        )

        assertEquals(ErrorClassifier.ErrorType.GEO_RESTRICTED, err.type)
        assertTrue(err.message.contains("Access restricted by region. A VPN may be required"))
    }

    @Test
    fun testNoPlayableVideoMapping() {
        val err = ErrorClassifier.classify(
            unsupportedHosts = listOf("unknownembed.to", "videocdn.xyz")
        )

        assertEquals(ErrorClassifier.ErrorType.NO_PLAYABLE_VIDEO, err.type)
        assertTrue(err.message.contains("unknownembed.to, videocdn.xyz"))
    }

    @Test
    fun testHostBlockedMapping() {
        val err = ErrorClassifier.classify(
            httpCode = 410,
            bodySnippet = "Video removed by copyright claim."
        )

        assertEquals(ErrorClassifier.ErrorType.HOST_BLOCKED, err.type)
        assertTrue(err.message.contains("Video host connection blocked"))
    }

    @Test
    fun testAllMirrorsFailedMapping() {
        val err = ErrorClassifier.classify(httpCode = 502)

        assertEquals(ErrorClassifier.ErrorType.ALL_MIRRORS_FAILED, err.type)
        assertTrue(err.message.contains("Site unreachable across all configured mirrors"))
    }
}
