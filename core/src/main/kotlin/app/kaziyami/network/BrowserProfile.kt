package app.kaziyami.network

/**
 * Encapsulates a consistent browser identity profile: User-Agent and corresponding Client Hints.
 * Verifies that client hints (brand, major version, platform, mobile flag) strictly align with User-Agent.
 * If consistency cannot be verified, client hints are safely omitted.
 */
data class BrowserProfile(
    val userAgent: String = DEFAULT_USER_AGENT,
    val secChUa: String? = DEFAULT_SEC_CH_UA,
    val secChUaMobile: String? = DEFAULT_SEC_CH_UA_MOBILE,
    val secChUaPlatform: String? = DEFAULT_SEC_CH_UA_PLATFORM
) {
    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        const val DEFAULT_SEC_CH_UA =
            "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\""
        const val DEFAULT_SEC_CH_UA_MOBILE = "?0"
        const val DEFAULT_SEC_CH_UA_PLATFORM = "\"Windows\""

        val DEFAULT = BrowserProfile()

        @Volatile
        private var cachedResolved: BrowserProfile? = null

        fun resolve(): BrowserProfile {
            cachedResolved?.let { return it }
            val resolved = computeResolvedProfile()
            cachedResolved = resolved
            return resolved
        }

        fun resetResolved() {
            cachedResolved = null
        }

        fun resolveRuntimeUserAgent(): String? {
            // 1. Try Android WebSettings.getDefaultUserAgent via ActivityThread or Injekt
            try {
                val context = try {
                    val atClass = Class.forName("android.app.ActivityThread")
                    atClass.getMethod("currentApplication").invoke(null)
                } catch (_: Throwable) {
                    try {
                        val injektClass = Class.forName("uy.kohesive.injekt.Injekt")
                        val instance = injektClass.getField("INSTANCE").get(null)
                        val getMethod = injektClass.getMethod("getInstance", Class::class.java)
                        getMethod.invoke(instance, Class.forName("android.app.Application"))
                    } catch (_: Throwable) {
                        null
                    }
                }

                if (context != null) {
                    val wsClass = Class.forName("android.webkit.WebSettings")
                    val getUa = wsClass.getMethod("getDefaultUserAgent", Class.forName("android.content.Context"))
                    val ua = getUa.invoke(null, context) as? String
                    if (!ua.isNullOrBlank()) {
                        return ua.trim()
                    }
                }
            } catch (_: Throwable) {
                // Fallback to system property or default
            }

            // 2. Try system property
            val sysAgent = System.getProperty("http.agent")?.trim()
            if (!sysAgent.isNullOrBlank() && (sysAgent.contains("Mozilla") || sysAgent.contains("Chrome"))) {
                return sysAgent
            }

            return null
        }

        private fun computeResolvedProfile(): BrowserProfile {
            val runtimeUa = resolveRuntimeUserAgent() ?: return DEFAULT
            val chromeMatch = Regex("""Chrome/(\d+)\.""").find(runtimeUa)
            val isAndroid = runtimeUa.contains("Android", ignoreCase = true)
            val isMobile = isAndroid || runtimeUa.contains("Mobile", ignoreCase = true)
            val platform = when {
                isAndroid -> "\"Android\""
                runtimeUa.contains("Windows", ignoreCase = true) -> "\"Windows\""
                runtimeUa.contains("Macintosh", ignoreCase = true) -> "\"macOS\""
                runtimeUa.contains("Linux", ignoreCase = true) -> "\"Linux\""
                else -> null
            }

            val (secChUa, secChUaMobile, secChUaPlatform) = if (chromeMatch != null && platform != null) {
                val major = chromeMatch.groupValues[1]
                Triple(
                    "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"$major\", \"Google Chrome\";v=\"$major\"",
                    if (isMobile) "?1" else "?0",
                    platform
                )
            } else {
                Triple(null, null, null)
            }

            return BrowserProfile(
                userAgent = runtimeUa,
                secChUa = secChUa,
                secChUaMobile = secChUaMobile,
                secChUaPlatform = secChUaPlatform
            )
        }
    }

    /**
     * Checks if client hints strictly match the user agent string.
     */
    fun areClientHintsConsistent(): Boolean {
        if (secChUa == null && secChUaMobile == null && secChUaPlatform == null) {
            return true
        }
        if (secChUa == null || secChUaMobile == null || secChUaPlatform == null) {
            return false
        }

        // 1. Chrome major version match
        val chromeMatch = Regex("""Chrome/(\d+)\.""").find(userAgent)
        if (chromeMatch != null) {
            val majorVer = chromeMatch.groupValues[1]
            if (!secChUa.contains("v=\"$majorVer\"")) {
                return false
            }
        } else {
            return false
        }

        // 2. Platform match
        val isWindows = userAgent.contains("Windows", ignoreCase = true)
        val isAndroid = userAgent.contains("Android", ignoreCase = true)
        val isMac = userAgent.contains("Macintosh", ignoreCase = true)
        val isLinux = userAgent.contains("Linux", ignoreCase = true) && !isAndroid

        when {
            isWindows && secChUaPlatform != "\"Windows\"" -> return false
            isAndroid && secChUaPlatform != "\"Android\"" -> return false
            isMac && secChUaPlatform != "\"macOS\"" -> return false
            isLinux && secChUaPlatform != "\"Linux\"" -> return false
        }

        // 3. Mobile flag match
        val isMobile = isAndroid || userAgent.contains("Mobile", ignoreCase = true)
        if (isMobile && secChUaMobile != "?1") return false
        if (!isMobile && secChUaMobile != "?0") return false

        return true
    }

    /**
     * Returns valid client hints map if consistent, or empty map if inconsistent or omitted.
     */
    fun getEffectiveClientHints(): Map<String, String> {
        if (!areClientHintsConsistent() || secChUa == null) {
            return emptyMap()
        }
        return mapOf(
            "Sec-Ch-Ua" to secChUa,
            "Sec-Ch-Ua-Mobile" to (secChUaMobile ?: "?0"),
            "Sec-Ch-Ua-Platform" to (secChUaPlatform ?: "\"Windows\"")
        )
    }
}
