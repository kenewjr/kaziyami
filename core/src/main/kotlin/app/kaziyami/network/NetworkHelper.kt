package app.kaziyami.network

import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Exception raised when an anti-bot challenge (Cloudflare Turnstile, JavaScript challenge, etc.) is detected.
 */
class CloudflareChallengeException(
    message: String,
    val isInteractive: Boolean = false,
    val targetUrl: String = ""
) : IOException(message)

/**
 * Standard HTTP header sets mirroring real browser behavior to avoid automated bot fingerprinting.
 * Deterministic header ordering is strictly maintained across all code paths.
 */
object BrowserHeaders {
    const val DEFAULT_USER_AGENT = BrowserProfile.DEFAULT_USER_AGENT

    enum class HeaderType {
        NAVIGATE,
        XHR,
        IMAGE
    }

    /**
     * Generates a realistic, deterministic header set for navigations, API calls, or media requests.
     */
    fun headersFor(
        url: String,
        referer: String? = null,
        type: HeaderType = HeaderType.NAVIGATE,
        profile: BrowserProfile = BrowserProfile.DEFAULT
    ): Headers {
        val builder = Headers.Builder()

        // 1. User-Agent
        builder.set("User-Agent", profile.userAgent)

        // 2. Accept
        when (type) {
            HeaderType.NAVIGATE -> builder.set(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
            )
            HeaderType.XHR -> builder.set("Accept", "application/json, text/javascript, */*; q=0.01")
            HeaderType.IMAGE -> builder.set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        }

        // 3. Accept-Language & Encoding
        builder.set("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
        builder.set("Accept-Encoding", "gzip, deflate, br")

        // 4. Referer (if present)
        if (referer != null) {
            builder.set("Referer", referer)
        }

        // 5. Client Hints (Strictly verified against User-Agent)
        val clientHints = profile.getEffectiveClientHints()
        clientHints["Sec-Ch-Ua"]?.let { builder.set("Sec-Ch-Ua", it) }
        clientHints["Sec-Ch-Ua-Mobile"]?.let { builder.set("Sec-Ch-Ua-Mobile", it) }
        clientHints["Sec-Ch-Ua-Platform"]?.let { builder.set("Sec-Ch-Ua-Platform", it) }

        // 6. Fetch Metadata (Sec-Fetch-Site computed from target url & referer)
        val targetHost = if (url.isNotBlank()) {
            try { java.net.URI(url).host } catch (_: Exception) { null }
        } else null
        val refererHost = referer?.let {
            try { java.net.URI(it).host } catch (_: Exception) { null }
        }

        val siteRelation = when {
            refererHost == null -> if (type == HeaderType.IMAGE) "cross-site" else "none"
            targetHost != null && refererHost.equals(targetHost, ignoreCase = true) -> "same-origin"
            targetHost != null && isSameSite(refererHost, targetHost) -> "same-site"
            targetHost == null -> "same-origin"
            else -> "cross-site"
        }

        when (type) {
            HeaderType.NAVIGATE -> {
                builder.set("Sec-Fetch-Site", siteRelation)
                builder.set("Sec-Fetch-Mode", "navigate")
                builder.set("Sec-Fetch-User", "?1")
                builder.set("Sec-Fetch-Dest", "document")
                builder.set("Upgrade-Insecure-Requests", "1")
            }
            HeaderType.XHR -> {
                builder.set("Sec-Fetch-Site", siteRelation)
                builder.set("Sec-Fetch-Mode", "cors")
                builder.set("Sec-Fetch-Dest", "empty")
                builder.set("X-Requested-With", "XMLHttpRequest")
            }
            HeaderType.IMAGE -> {
                builder.set("Sec-Fetch-Site", if (siteRelation == "none") "cross-site" else siteRelation)
                builder.set("Sec-Fetch-Mode", "no-cors")
                builder.set("Sec-Fetch-Dest", "image")
            }
        }

        return builder.build()
    }

    private fun isSameSite(h1: String, h2: String): Boolean {
        val p1 = h1.split(".").takeLast(2).joinToString(".")
        val p2 = h2.split(".").takeLast(2).joinToString(".")
        return p1.equals(p2, ignoreCase = true)
    }

    /**
     * Backward-compatible default headers generator.
     */
    fun defaultHeaders(referer: String? = null): Headers = headersFor("", referer, HeaderType.NAVIGATE)
}

/**
 * Interceptor that injects standard browser headers into requests if omitted.
 */
class BrowserHeaderInterceptor(
    private val defaultRefererProvider: (() -> String)? = null,
    private val profile: BrowserProfile = BrowserProfile.DEFAULT
) : Interceptor {
    constructor(refererProvider: () -> String) : this(refererProvider, BrowserProfile.DEFAULT)
    constructor(profile: BrowserProfile) : this(null, profile)
    constructor() : this(null, BrowserProfile.DEFAULT)

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val defaultHeaders = BrowserHeaders.headersFor(
            url = original.url.toString(),
            referer = defaultRefererProvider?.invoke(),
            profile = profile
        )
        for (i in 0 until defaultHeaders.size) {
            val name = defaultHeaders.name(i)
            if (original.header(name) == null) {
                builder.header(name, defaultHeaders.value(i))
            }
        }

        return chain.proceed(builder.build())
    }
}

/**
 * Interceptor checking and updating host circuit breakers.
 */
class CircuitBreakerInterceptor(
    private val circuitBreaker: HostCircuitBreaker
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        circuitBreaker.checkPermission(host)

        try {
            val response = chain.proceed(chain.request())
            if (response.code in 200..399) {
                circuitBreaker.recordSuccess(host)
            } else if (response.code in listOf(500, 502, 503, 504)) {
                circuitBreaker.recordFailure(host, "HTTP ${response.code}")
            }
            return response
        } catch (e: Exception) {
            circuitBreaker.recordFailure(host, e.message ?: "Connection error")
            throw e
        }
    }
}

/**
 * Detects Cloudflare interstitials, Turnstile challenges, and block responses.
 */
object ChallengeDetector {
    fun isChallenge(response: Response): Boolean {
        val res = ChallengeClassifier.classify(response)
        return res.classification in listOf(
            ChallengeClass.JS_CHALLENGE,
            ChallengeClass.INTERACTIVE_CAPTCHA
        )
    }
}

/**
 * Interceptor that monitors incoming HTTP responses for challenge/block pages.
 */
class CloudflareInterceptor(
    private val rateLimiter: TokenBucketRateLimiter? = null
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        HostDiagnostics.recordRequest(host)

        val response = chain.proceed(request)
        val resolution = ChallengeClassifier.classify(response)

        if (resolution.classification != ChallengeClass.NONE) {
            HostDiagnostics.recordChallenge(host, resolution.classification)
        }

        when (resolution.classification) {
            ChallengeClass.RATE_LIMITED -> {
                HostDiagnostics.recordRateLimit(host)
                rateLimiter?.lowerBudget(host)
                // Let retry interceptor handle 429 backoff
                return response
            }
            ChallengeClass.INTERACTIVE_CAPTCHA -> {
                val url = request.url.toString()
                response.close()
                throw CloudflareChallengeException(
                    message = "${resolution.message} Target: $url",
                    isInteractive = true,
                    targetUrl = url
                )
            }
            ChallengeClass.JS_CHALLENGE -> {
                val url = request.url.toString()
                response.close()
                throw CloudflareChallengeException(
                    message = "Cloudflare challenge detected on $url. ${resolution.message}",
                    isInteractive = false,
                    targetUrl = url
                )
            }
            ChallengeClass.IP_OR_REGION_BLOCK,
            ChallengeClass.ISP_BLOCK -> {
                val url = request.url.toString()
                response.close()
                throw IOException("${resolution.message} (Target: $url)")
            }
            else -> {
                // Pass through normal response or origin errors
                return response
            }
        }
    }
}

/**
 * Rate-limiting interceptor that enforces token bucket budgets, concurrency caps, and jitter per host.
 */
class RateLimitInterceptor(
    val rateLimiter: TokenBucketRateLimiter = TokenBucketRateLimiter()
) : Interceptor {

    constructor(permitsPerSecond: Double) : this(
        TokenBucketRateLimiter(
            defaultCapacity = permitsPerSecond.coerceAtLeast(1.0),
            defaultRefillRatePerSec = permitsPerSecond.coerceAtLeast(0.5)
        )
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        rateLimiter.acquire(host)
        try {
            return chain.proceed(chain.request())
        } finally {
            rateLimiter.release(host)
        }
    }
}

/**
 * Handles HTTP 429 and 503 responses with exponential backoff and respects 'Retry-After' response headers.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0

        while ((response.code == 429 || response.code == 503) && tryCount < maxRetries) {
            tryCount++
            val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
            val waitMs = if (retryAfterSec != null && retryAfterSec > 0) {
                retryAfterSec * 1000
            } else {
                (1000L * (1 shl tryCount)) + Random.nextLong(50, 200)
            }

            response.close()
            try {
                Thread.sleep(waitMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }

            response = chain.proceed(request)
        }

        return response
    }
}

typealias RetryAfterInterceptor = RetryInterceptor

/**
 * In-memory thread-safe cookie storage shared across calls with per-host isolation and clearance tracking.
 */
class SimpleCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        saveCookies(url.host, cookies)
    }

    fun saveCookies(host: String, cookies: List<Cookie>) {
        val list = store.getOrPut(host) { mutableListOf() }
        synchronized(list) {
            for (cookie in cookies) {
                list.removeAll { it.name == cookie.name }
                list.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(list) {
            list.removeAll { it.expiresAt < now }
            return list.toList()
        }
    }

    /**
     * Checks if a valid, unexpired cf_clearance cookie is present for the host.
     */
    fun hasValidClearance(host: String): Boolean {
        val list = store[host] ?: return false
        val now = System.currentTimeMillis()
        synchronized(list) {
            return list.any { it.name == "cf_clearance" && it.expiresAt > now }
        }
    }

    /**
     * Returns the expiry timestamp of the cf_clearance cookie, or null if absent.
     */
    fun getClearanceExpiry(host: String): Long? {
        val list = store[host] ?: return null
        synchronized(list) {
            return list.firstOrNull { it.name == "cf_clearance" }?.expiresAt
        }
    }

    /**
     * Checks if cf_clearance is within the given threshold of expiration.
     */
    fun isClearanceNearExpiry(host: String, thresholdMs: Long = 60_000L): Boolean {
        val expiry = getClearanceExpiry(host) ?: return false
        val now = System.currentTimeMillis()
        return (expiry - now) < thresholdMs
    }

    /**
     * Synchronizes cookies from Android's android.webkit.CookieManager if present at runtime.
     */
    fun syncFromAndroidWebView(url: HttpUrl) {
        try {
            val cmClass = Class.forName("android.webkit.CookieManager")
            val getInstance = cmClass.getMethod("getInstance")
            val cookieManager = getInstance.invoke(null)
            val getCookie = cmClass.getMethod("getCookie", String::class.java)
            val rawHeader = getCookie.invoke(cookieManager, url.toString()) as? String
            if (!rawHeader.isNullOrBlank()) {
                val parsed = rawHeader.split(";").mapNotNull { part ->
                    val trimmed = part.trim()
                    val eq = trimmed.indexOf('=')
                    if (eq > 0) {
                        val name = trimmed.substring(0, eq).trim()
                        val value = trimmed.substring(eq + 1).trim()
                        Cookie.Builder()
                            .domain(url.host)
                            .name(name)
                            .value(value)
                            .build()
                    } else null
                }
                saveCookies(url.host, parsed)
            }
        } catch (_: Throwable) {
            // JVM environment without Android WebView
        }
    }
}

/**
 * Builds compliant OkHttpClient instances configured with connection pooling, rate limits, and challenge detection.
 */
object NetworkFactory {
    fun createClient(
        baseClient: OkHttpClient = OkHttpClient(),
        permitsPerSecond: Double = NetworkConfig.DEFAULT_REQUESTS_PER_PERIOD.toDouble(),
        maxRetries: Int = NetworkConfig.MAX_RETRIES_ON_429.coerceAtLeast(2),
        cookieJar: CookieJar = SimpleCookieJar(),
        circuitBreaker: HostCircuitBreaker = HostCircuitBreaker()
    ): OkHttpClient {
        val rateLimiter = TokenBucketRateLimiter(
            defaultCapacity = permitsPerSecond.coerceAtLeast(1.0),
            defaultRefillRatePerSec = permitsPerSecond.coerceAtLeast(0.5)
        )
        val pool = ConnectionPool(5, 5, TimeUnit.MINUTES)

        return baseClient.newBuilder()
            .connectionPool(pool)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .cookieJar(cookieJar)
            .addInterceptor(CircuitBreakerInterceptor(circuitBreaker))
            .addInterceptor(RateLimitInterceptor(rateLimiter))
            .addInterceptor(RetryInterceptor(maxRetries))
            .addInterceptor(BrowserHeaderInterceptor())
            .addInterceptor(CloudflareInterceptor(rateLimiter))
            .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

class NetworkHelper(
    val client: OkHttpClient = NetworkFactory.createClient()
) {
    val cloudflareClient: OkHttpClient = client
}
