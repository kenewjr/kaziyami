package app.kaziyami.engine

import app.kaziyami.network.LogScrubber
import app.kaziyami.network.NetworkFactory
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementations of platform services for JVM and test environments.
 */

class DefaultLogger : Logger {
    override fun debug(tag: String, message: String) {
        println("[$tag] DEBUG: ${LogScrubber.scrub(message)}")
    }

    override fun info(tag: String, message: String) {
        println("[$tag] INFO: ${LogScrubber.scrub(message)}")
    }

    override fun warn(tag: String, message: String) {
        System.err.println("[$tag] WARN: ${LogScrubber.scrub(message)}")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        System.err.println("[$tag] ERROR: ${LogScrubber.scrub(message)}")
        throwable?.printStackTrace(System.err)
    }
}

class InMemoryPreferenceStore : PreferenceStore {
    private val prefs = ConcurrentHashMap<String, String>()

    override fun getString(key: String, defaultValue: String): String {
        return prefs[key] ?: defaultValue
    }

    override fun setString(key: String, value: String) {
        prefs[key] = value
    }
}

class InMemoryCookieStore : CookieStore {
    private val cookies = ConcurrentHashMap<String, String>()

    override fun getCookie(url: String): String? = cookies[url]

    override fun setCookie(url: String, cookie: String) {
        cookies[url] = cookie
    }
}

class DefaultHttpClientProvider(
    private val client: OkHttpClient = NetworkFactory.createClient()
) : HttpClientProvider {
    override fun getClient(): OkHttpClient = client
}

class NoopChallengeSolver : ChallengeSolver {
    override fun solve(url: String, userAgent: String): Boolean = false
}
