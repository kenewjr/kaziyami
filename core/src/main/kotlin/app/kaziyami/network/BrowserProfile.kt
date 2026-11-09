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

        fun setRuntimeUserAgent(ua: String) {
            val clean = ua.trim()
            if (clean.isNotBlank()) {
                cachedResolved = computeProfileForUserAgent(clean)
            }
        }

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
            // 1. Try Injekt: NetworkHelper.defaultUserAgentProvider() or Application -> WebSettings
            try {
                val injektKt = Class.forName("uy.kohesive.injekt.InjektKt")
                val getInjekt = injektKt.getMethod("getInjekt")
                val scope = getInjekt.invoke(null)
                val getInstance = scope.javaClass.getMethod("getInstance", java.lang.reflect.Type::class.java)

                try {
                    val nhClass = Class.forName("eu.kanade.tachiyomi.network.NetworkHelper")
                    val nh = getInstance.invoke(scope, nhClass)
                    if (nh != null) {
                        val getUa = nhClass.getMethod("defaultUserAgentProvider")
                        val ua = getUa.invoke(nh) as? String
                        if (!ua.isNullOrBlank()) return ua.trim()
                    }
                } catch (_: Throwable) {}

                try {
                    val appClass = Class.forName("android.app.Application")
                    val app = getInstance.invoke(scope, appClass)
                    if (app != null) {
                        val wsClass = Class.forName("android.webkit.WebSettings")
                        val getUa = wsClass.getMethod("getDefaultUserAgent", Class.forName("android.content.Context"))
                        val ua = getUa.invoke(null, app) as? String
                        if (!ua.isNullOrBlank()) return ua.trim()
                    }
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}

            // 2. Try Android ActivityThread -> WebSettings.getDefaultUserAgent
            try {
                val atClass = Class.forName("android.app.ActivityThread")
                val app = try {
                    atClass.getMethod("currentApplication").invoke(null)
                } catch (_: Throwable) {
                    try {
                        atClass.getMethod("getApplication").invoke(null)
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (app != null) {
                    val wsClass = Class.forName("android.webkit.WebSettings")
                    val getUa = wsClass.getMethod("getDefaultUserAgent", Class.forName("android.content.Context"))
                    val ua = getUa.invoke(null, app) as? String
                    if (!ua.isNullOrBlank()) return ua.trim()
                }
            } catch (_: Throwable) {}

            // 3. Try system property http.agent
            val sysAgent = System.getProperty("http.agent")?.trim().orEmpty()
            if (sysAgent.isNotBlank()) {
                if (sysAgent.contains("Mozilla") || sysAgent.contains("Chrome")) {
                    return sysAgent
                }
                if (sysAgent.contains("Android")) {
                    val match = Regex("""\((Linux; U; Android [^)]+)\)""").find(sysAgent)
                    val osInfo = match?.groupValues?.get(1)?.replace("; U;", ";") ?: "Linux; Android 10; K"
                    return "Mozilla/5.0 ($osInfo) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"
                }
            }

            // 4. Android runtime detection without explicit UA: fallback to modern generic Android UA
            val isAndroidRuntime = System.getProperty("java.vendor")?.contains("Android", ignoreCase = true) == true ||
                System.getProperty("java.vm.vendor")?.contains("The Android Project", ignoreCase = true) == true
            if (isAndroidRuntime) {
                return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"
            }

            return null
        }

        fun computeProfileForUserAgent(runtimeUa: String): BrowserProfile {
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

            // Android WebViews and mobile apps omit Sec-Ch-Ua client hints by default to avoid Turnstile conflicts
            val (secChUa, secChUaMobile, secChUaPlatform) = if (!isAndroid && chromeMatch != null && platform != null) {
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

        private fun computeResolvedProfile(): BrowserProfile {
            val runtimeUa = resolveRuntimeUserAgent() ?: return DEFAULT
            return computeProfileForUserAgent(runtimeUa)
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
