package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class CloudflareChallengeException(message: String) : IOException(message)

object BrowserHeaders {
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    fun defaultHeaders(referer: String? = null): Headers {
        val builder = Headers.Builder()
            .set("User-Agent", DEFAULT_USER_AGENT)
            .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .set("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
            .set("Accept-Encoding", "gzip, deflate, br")
            .set("Sec-Ch-Ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
            .set("Sec-Ch-Ua-Mobile", "?0")
            .set("Sec-Ch-Ua-Platform", "\"Windows\"")
            .set("Sec-Fetch-Dest", "document")
            .set("Sec-Fetch-Mode", "navigate")
            .set("Sec-Fetch-Site", if (referer != null) "same-origin" else "none")
            .set("Sec-Fetch-User", "?1")
            .set("Upgrade-Insecure-Requests", "1")

        if (referer != null) {
            builder.set("Referer", referer)
        }
        return builder.build()
    }
}

class BrowserHeaderInterceptor(
    private val defaultRefererProvider: (() -> String)? = null
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val defaultHeaders = BrowserHeaders.defaultHeaders(defaultRefererProvider?.invoke())
        for (i in 0 until defaultHeaders.size) {
            val name = defaultHeaders.name(i)
            if (original.header(name) == null) {
                builder.header(name, defaultHeaders.value(i))
            }
        }

        return chain.proceed(builder.build())
    }
}

class CloudflareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (isChallenge(response)) {
            val url = request.url.toString()
            response.close()
            throw CloudflareChallengeException(
                "Cloudflare challenge detected on $url. Please open the site in WebView to solve the challenge."
            )
        }

        return response
    }

    private fun isChallenge(response: Response): Boolean {
        if (response.header("cf-mitigated") == "challenge") {
            return true
        }
        val code = response.code
        if (code == 403 || code == 503) {
            val server = response.header("Server") ?: ""
            if (server.contains("cloudflare", ignoreCase = true)) {
                val peekBody = try {
                    response.peekBody(4096).string()
                } catch (_: Exception) {
                    ""
                }
                if (peekBody.contains("Just a moment...", ignoreCase = true) ||
                    peekBody.contains("Attention Required", ignoreCase = true) ||
                    peekBody.contains("cf-turnstile", ignoreCase = true) ||
                    peekBody.contains("challenge-platform", ignoreCase = true)
                ) {
                    return true
                }
            }
        }
        return false
    }
}

class RateLimitInterceptor(
    private val permitsPerSecond: Double = 3.0
) : Interceptor {
    private val hostLastAccess = ConcurrentHashMap<String, Long>()
    private val minIntervalMs = (1000.0 / permitsPerSecond).toLong()

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val now = System.currentTimeMillis()
        val last = hostLastAccess[host] ?: 0L

        val elapsed = now - last
        if (elapsed < minIntervalMs) {
            val jitter = Random.nextLong(20, 80)
            val sleepTime = (minIntervalMs - elapsed) + jitter
            try {
                Thread.sleep(sleepTime)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        hostLastAccess[host] = System.currentTimeMillis()

        return chain.proceed(chain.request())
    }
}

class RetryAfterInterceptor(
    private val maxRetries: Int = 3
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
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

class SimpleCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
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
}

class NetworkHelper(
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(SimpleCookieJar())
        .addInterceptor(RateLimitInterceptor(4.0))
        .addInterceptor(RetryAfterInterceptor(2))
        .addInterceptor(BrowserHeaderInterceptor())
        .addInterceptor(CloudflareInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    val cloudflareClient: OkHttpClient = client
}
