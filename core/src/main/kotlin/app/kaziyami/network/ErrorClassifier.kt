package app.kaziyami.network

/**
 * Classifies technical HTTP errors, network timeouts, and stream parsing failures
 * into short, accurate, user-friendly UI messages.
 */
object ErrorClassifier {

    enum class ErrorType {
        CHALLENGE_REQUIRED,
        ALL_MIRRORS_FAILED,
        NO_PLAYABLE_VIDEO,
        HOST_BLOCKED,
        GEO_RESTRICTED,
        GENERIC_NETWORK_ERROR
    }

    data class UserError(
        val type: ErrorType,
        val message: String,
        val isRecoverableInWebView: Boolean = false
    )

    fun classify(
        httpCode: Int = 0,
        headers: Map<String, String> = emptyMap(),
        bodySnippet: String = "",
        unsupportedHosts: List<String> = emptyList()
    ): UserError {
        val serverHeader = headers["Server"].orEmpty().lowercase()
        val cfMitigated = headers["cf-mitigated"].orEmpty().lowercase()
        val bodyLower = bodySnippet.lowercase()

        // 1. Cloudflare / Turnstile / DDOS protection challenge
        if (cfMitigated == "challenge" ||
            bodyLower.contains("just a moment...") ||
            bodyLower.contains("cloudflare") && (httpCode == 403 || httpCode == 503) ||
            bodyLower.contains("cf-browser-verification")
        ) {
            return UserError(
                type = ErrorType.CHALLENGE_REQUIRED,
                message = "Kaziyami: Site protection active. Open the site in WebView to solve challenge.",
                isRecoverableInWebView = true
            )
        }

        // 2. Geo-restriction / Region blocking
        if (httpCode == 451 ||
            bodyLower.contains("not available in your country") ||
            bodyLower.contains("geo-blocked") ||
            bodyLower.contains("restricted in your region")
        ) {
            return UserError(
                type = ErrorType.GEO_RESTRICTED,
                message = "Kaziyami: Access restricted by region. A VPN may be required."
            )
        }

        // 3. Video host blocked
        if (httpCode in listOf(403, 410) || bodyLower.contains("video removed") || bodyLower.contains("access denied")) {
            return UserError(
                type = ErrorType.HOST_BLOCKED,
                message = "Kaziyami: Video host connection blocked. Try another server."
            )
        }

        // 4. No playable video found with seen hosts list
        if (unsupportedHosts.isNotEmpty()) {
            val hostList = unsupportedHosts.distinct().joinToString(", ")
            return UserError(
                type = ErrorType.NO_PLAYABLE_VIDEO,
                message = "Kaziyami: No playable video found. Detected unsupported hosts: [$hostList]."
            )
        }

        // 5. Site down across all mirrors
        if (httpCode in listOf(502, 503, 504) || httpCode == 0) {
            return UserError(
                type = ErrorType.ALL_MIRRORS_FAILED,
                message = "Kaziyami: Site unreachable across all configured mirrors."
            )
        }

        return UserError(
            type = ErrorType.GENERIC_NETWORK_ERROR,
            message = "Kaziyami: Network error (HTTP $httpCode). Check internet connection."
        )
    }
}
