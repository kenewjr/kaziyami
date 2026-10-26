package app.kaziyami.network

import okhttp3.Response

/**
 * Detailed classification of anti-bot challenges, network blocks, and server conditions.
 */
enum class ChallengeClass {
    NONE,
    JS_CHALLENGE,
    INTERACTIVE_CAPTCHA,
    RATE_LIMITED,
    IP_OR_REGION_BLOCK,
    WAF_OTHER,
    ORIGIN_DOWN,
    ISP_BLOCK
}

/**
 * User-facing representation of a classified challenge or block.
 */
data class ChallengeResolution(
    val classification: ChallengeClass,
    val message: String,
    val isAutoSolvable: Boolean = false,
    val requiresUserInteraction: Boolean = false,
    val isPermanentBlock: Boolean = false
)

/**
 * Classifies HTTP responses and network exceptions into actionable challenge categories.
 */
object ChallengeClassifier {

    fun classify(response: Response): ChallengeResolution {
        val code = response.code
        val headers = response.headers
        val server = headers["Server"].orEmpty().lowercase()
        val cfMitigated = headers["cf-mitigated"].orEmpty().lowercase()

        val peekBody = try {
            response.peekBody(4096).string().lowercase()
        } catch (_: Exception) {
            ""
        }

        // 1. Rate Limited (HTTP 429 or Cloudflare 1015)
        if (code == 429 || peekBody.contains("error 1015") || peekBody.contains("rate limited")) {
            return ChallengeResolution(
                classification = ChallengeClass.RATE_LIMITED,
                message = "Kaziyami: Request limit reached. Backing off...",
                isAutoSolvable = false
            )
        }

        // 2. Interactive Captcha (Turnstile checkbox, hCaptcha, reCaptcha)
        val hasInteractiveWidget = peekBody.contains("cf-turnstile") ||
            peekBody.contains("hcaptcha") ||
            peekBody.contains("h-captcha") ||
            peekBody.contains("g-recaptcha") ||
            peekBody.contains("recaptcha") ||
            peekBody.contains("verify you are human") ||
            peekBody.contains("turnstile") && peekBody.contains("checkbox")

        if (hasInteractiveWidget) {
            return ChallengeResolution(
                classification = ChallengeClass.INTERACTIVE_CAPTCHA,
                message = "Kaziyami: Interactive verification required. Open site in WebView to complete check, then retry.",
                requiresUserInteraction = true
            )
        }

        // 3. Automated JS Challenge (Cloudflare waiting room / 5s shield / browser verification)
        val isJsChallenge = cfMitigated == "challenge" ||
            peekBody.contains("cf-browser-verification") ||
            peekBody.contains("checking your browser") ||
            peekBody.contains("just a moment...") ||
            peekBody.contains("cf-challenge-running") ||
            peekBody.contains("challenge-platform") ||
            (server.contains("cloudflare") && (code == 403 || code == 503) && peekBody.contains("just a moment"))

        if (isJsChallenge) {
            return ChallengeResolution(
                classification = ChallengeClass.JS_CHALLENGE,
                message = "Kaziyami: Site protection active. Solving browser verification...",
                isAutoSolvable = true
            )
        }

        // 4. IP or Region Block (HTTP 451, Cloudflare 1020, Geo-block)
        val isIpOrRegionBlock = code == 451 ||
            (code == 403 && (
                peekBody.contains("error 1020") ||
                peekBody.contains("access denied") ||
                peekBody.contains("not available in your country") ||
                peekBody.contains("geo-blocked") ||
                peekBody.contains("restricted in your region")
            ))

        if (isIpOrRegionBlock) {
            return ChallengeResolution(
                classification = ChallengeClass.IP_OR_REGION_BLOCK,
                message = "Kaziyami: Access restricted by IP or region. VPN or alternate mirror required.",
                isPermanentBlock = true
            )
        }

        // 5. ISP / Regional DNS Block (Internet Positif, etc.)
        val isIspBlock = peekBody.contains("internet positif") ||
            peekBody.contains("internet baik") ||
            peekBody.contains("kominfo") ||
            peekBody.contains("trustpositif")

        if (isIspBlock) {
            return ChallengeResolution(
                classification = ChallengeClass.ISP_BLOCK,
                message = "Kaziyami: Domain blocked by local network or ISP. Configure Private DNS (DoH).",
                isPermanentBlock = true
            )
        }

        // 6. Origin Server Down (500, 502, 503 without challenge, 504, 521-524)
        if (code in listOf(500, 502, 504, 521, 522, 523, 524) ||
            (code == 503 && !server.contains("cloudflare"))
        ) {
            return ChallengeResolution(
                classification = ChallengeClass.ORIGIN_DOWN,
                message = "Kaziyami: Site origin server is currently down."
            )
        }

        // 7. Other WAF Block (Generic 403)
        if (code == 403) {
            return ChallengeResolution(
                classification = ChallengeClass.WAF_OTHER,
                message = "Kaziyami: Access denied by website security firewall."
            )
        }

        return ChallengeResolution(
            classification = ChallengeClass.NONE,
            message = "OK"
        )
    }
}
