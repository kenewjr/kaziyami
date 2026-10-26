package app.kaziyami.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class ChallengeClassificationTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun execute(response: MockResponse): okhttp3.Response {
        server.enqueue(response)
        val client = OkHttpClient()
        return client.newCall(Request.Builder().url(server.url("/test")).build()).execute()
    }

    @Test
    fun classifiesRateLimitedOn429() {
        val res = execute(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        val resolution = ChallengeClassifier.classify(res)
        assertEquals(ChallengeClass.RATE_LIMITED, resolution.classification)
        assertTrue(resolution.message.contains("Request limit reached"))
        res.close()
    }

    @Test
    fun classifiesRateLimitedOnCloudflare1015() {
        val res = execute(MockResponse().setResponseCode(403).setBody("<html><body>Error 1015: You are being rate limited</body></html>"))
        val resolution = ChallengeClassifier.classify(res)
        assertEquals(ChallengeClass.RATE_LIMITED, resolution.classification)
        res.close()
    }

    @Test
    fun classifiesInteractiveCaptchaTurnstile() {
        val res = execute(
            MockResponse()
                .setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setBody("<html><body><div class=\"cf-turnstile\" data-sitekey=\"abc\"></div><p>Verify you are human</p></body></html>")
        )
        val resolution = ChallengeClassifier.classify(res)
        assertEquals(ChallengeClass.INTERACTIVE_CAPTCHA, resolution.classification)
        assertTrue(resolution.requiresUserInteraction)
        assertFalse(resolution.isAutoSolvable)
        assertTrue(resolution.message.contains("Open site in WebView"))
        res.close()
    }

    @Test
    fun classifiesJsChallenge() {
        val res = execute(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Server", "cloudflare")
                .setHeader("cf-mitigated", "challenge")
                .setBody("<html><head><title>Just a moment...</title></head><body><div id=\"cf-challenge-running\">Checking your browser</div></body></html>")
        )
        val resolution = ChallengeClassifier.classify(res)
        assertEquals(ChallengeClass.JS_CHALLENGE, resolution.classification)
        assertTrue(resolution.isAutoSolvable)
        assertFalse(resolution.requiresUserInteraction)
        res.close()
    }

    @Test
    fun classifiesIpOrRegionBlockOn1020() {
        val res = execute(
            MockResponse()
                .setResponseCode(403)
                .setBody("<html><body><h1>Error 1020: Access Denied</h1><p>This website is using a security service to protect itself.</p></body></html>")
        )
        val resolution = ChallengeClassifier.classify(res)
        assertEquals(ChallengeClass.IP_OR_REGION_BLOCK, resolution.classification)
        assertTrue(resolution.isPermanentBlock)
        res.close()
    }

    @Test
    fun classifiesIpOrRegionBlockOn451() {
        val res = execute(
            MockResponse()
                .setResponseCode(451)
                .setBody("<html><body>Unavailable For Legal Reasons</body></html>")
        )
        val resolution = ChallengeClassifier.classify(res)
        assertEquals(ChallengeClass.IP_OR_REGION_BLOCK, resolution.classification)
        res.close()
    }

    @Test
    fun classifiesIspBlockOnInternetPositif() {
        val res = execute(
            MockResponse()
                .setResponseCode(200)
                .setBody("<html><head><title>Internet Positif</title></head><body>Situs Diblokir Kominfo TrustPositif</body></html>")
        )
        val resolution = ChallengeClassifier.classify(res)
        assertEquals(ChallengeClass.ISP_BLOCK, resolution.classification)
        assertTrue(resolution.isPermanentBlock)
        assertTrue(resolution.message.contains("Private DNS"))
        res.close()
    }

    @Test
    fun classifiesOriginDownOn502() {
        val res = execute(
            MockResponse()
                .setResponseCode(502)
                .setBody("<html><body>Bad Gateway</body></html>")
        )
        val resolution = ChallengeClassifier.classify(res)
        assertEquals(ChallengeClass.ORIGIN_DOWN, resolution.classification)
        res.close()
    }

    @Test
    fun classifiesWafOtherOnGeneric403() {
        val res = execute(
            MockResponse()
                .setResponseCode(403)
                .setBody("<html><body>403 Forbidden - Wordfence Security</body></html>")
        )
        val resolution = ChallengeClassifier.classify(res)
        assertEquals(ChallengeClass.WAF_OTHER, resolution.classification)
        res.close()
    }

    @Test
    fun interactiveCaptchaThrowsImmediatelyAndNeverLoops() {
        // Enqueue interactive response; client must fail fast and not retry in loop
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("<html><body><div class=\"cf-turnstile\"></div>Please verify you are human</body></html>")
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CloudflareInterceptor())
            .build()

        val ex = assertThrows(CloudflareChallengeException::class.java) {
            client.newCall(Request.Builder().url(server.url("/captcha")).build()).execute().use {}
        }

        assertTrue(ex.isInteractive)
        assertTrue(ex.message!!.contains("Open site in WebView"))
        // Exactly 1 request made, zero retry loops
        assertEquals(1, server.requestCount)
    }
}
