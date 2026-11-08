package app.kaziyami.network

/**
 * Log sanitization utility ensuring privacy and security compliance.
 * Scrubs tokens, session cookies, Cloudflare clearances, and signed video URLs from log outputs.
 */
object LogScrubber {

    private val COOKIE_REGEX = Regex("""(?i)(?:cookie|set-cookie)\s*:\s*[^\r\n]+""")
    private val TOKEN_REGEX = Regex("""(?i)(?:token|auth|bearer|signature|sig|key|pass|password)=([^&\s'"]+)""")
    private val URL_QUERY_REGEX = Regex("""(?i)https?://[^\s?]+\?([^"'\s]+)""")

    /**
     * Sanitizes a log line, redacting sensitive query strings, tokens, and cookies.
     */
    fun scrub(message: String): String {
        var result = message

        // 1. Scrub Cookies
        result = COOKIE_REGEX.replace(result, "Cookie: [REDACTED]")

        // 2. Scrub inline tokens/signatures
        result = TOKEN_REGEX.replace(result) { mr ->
            val param = mr.value.substringBefore('=')
            "$param=[REDACTED]"
        }

        // 3. Scrub full query parameters on URLs to prevent signature leakage
        result = URL_QUERY_REGEX.replace(result) { mr ->
            val base = mr.value.substringBefore('?')
            "$base?[REDACTED_QUERY]"
        }

        return result
    }
}
