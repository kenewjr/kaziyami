package app.kaziyami.network

import java.util.regex.Pattern

/**
 * Log sanitization utility ensuring privacy and security compliance.
 * Scrubs tokens, session cookies, Cloudflare clearances, and signed video URLs from log outputs.
 */
object LogScrubber {

    private val COOKIE_PATTERN = Pattern.compile(
        "(?:cookie|set-cookie)\\s*:\\s*[^\\r\\n]+",
        Pattern.CASE_INSENSITIVE
    )

    private val TOKEN_PATTERN = Pattern.compile(
        "(?:token|auth|bearer|signature|sig|key|pass|password)=([^&\\s'\"]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val URL_QUERY_PATTERN = Pattern.compile(
        "https?://[^\\s?]+\\?([^\\s'\"]+)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Sanitizes a log line, redacting sensitive query strings, tokens, and cookies.
     */
    fun scrub(message: String): String {
        var result = message

        // 1. Scrub Cookies
        val cookieMatcher = COOKIE_PATTERN.matcher(result)
        result = cookieMatcher.replaceAll("Cookie: [REDACTED]")

        // 2. Scrub inline tokens/signatures
        val tokenMatcher = TOKEN_PATTERN.matcher(result)
        result = tokenMatcher.replaceAll { mr ->
            val param = mr.group().substringBefore('=')
            "$param=[REDACTED]"
        }

        // 3. Scrub full query parameters on URLs to prevent signature leakage
        val urlMatcher = URL_QUERY_PATTERN.matcher(result)
        result = urlMatcher.replaceAll { mr ->
            val fullUrl = mr.group()
            val base = fullUrl.substringBefore('?')
            "$base?[REDACTED_QUERY]"
        }

        return result
    }
}
