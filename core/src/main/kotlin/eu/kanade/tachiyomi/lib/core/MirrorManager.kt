package eu.kanade.tachiyomi.lib.core

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe mirror and domain failover manager.
 * Tracks session health, respects user preference domain overrides, and automates seamless
 * failover across ordered mirror domains upon network/DNS or HTTP 5xx failures.
 */
class MirrorManager(
    val defaultBaseUrl: String,
    val mirrors: List<String> = emptyList(),
    private val preferences: (() -> String?)? = null
) {
    val allMirrors: List<String> = (listOf(defaultBaseUrl) + mirrors).distinct()

    private val lastHealthyMirror = AtomicReference<String?>(null)
    private val customOverride = AtomicReference<String?>(null)
    private val lock = Any()

    /**
     * Sets a user-defined manual domain override.
     */
    fun setOverrideUrl(override: String?) {
        customOverride.set(override?.trim()?.removeSuffix("/"))
    }

    /**
     * Returns the active base URL, honoring user overrides before session-cached healthy mirrors.
     */
    fun currentBase(): String {
        val userOverride = customOverride.get() ?: preferences?.invoke()?.trim()
        if (!userOverride.isNullOrEmpty()) {
            return userOverride.removeSuffix("/")
        }
        val healthy = lastHealthyMirror.get()
        return healthy ?: defaultBaseUrl.removeSuffix("/")
    }

    /**
     * Alias for [currentBase] ensuring backward compatibility with Aniyomi extension patterns.
     */
    fun getBaseUrl(): String = currentBase()

    /**
     * Transitions to the next available mirror domain upon communication failure.
     *
     * @param current The failing mirror domain.
     * @return The next mirror URL chosen.
     */
    fun failover(current: String = currentBase()): String = synchronized(lock) {
        val cleanCurrent = current.removeSuffix("/")
        val next = allMirrors.map { it.removeSuffix("/") }.firstOrNull { it != cleanCurrent }
            ?: defaultBaseUrl.removeSuffix("/")
        markHealthy(next)
        next
    }

    /**
     * Records a mirror domain as healthy for the active session.
     */
    fun markHealthy(mirrorUrl: String) {
        lastHealthyMirror.set(mirrorUrl.removeSuffix("/"))
    }

    /**
     * Records a mirror domain as failed, clearing it if it was marked as the last healthy domain.
     */
    fun markFailed(mirrorUrl: String) {
        val clean = mirrorUrl.removeSuffix("/")
        lastHealthyMirror.compareAndSet(clean, null)
    }

    /**
     * Alias for [markFailed].
     */
    fun markUnhealthy(mirrorUrl: String) = markFailed(mirrorUrl)

    /**
     * Actively tests candidate mirrors using a lightweight HTTP HEAD/GET request.
     */
    suspend fun getWorkingMirror(client: OkHttpClient): String {
        val current = currentBase()
        if (testMirror(client, current)) {
            markHealthy(current)
            return current
        }

        for (mirror in allMirrors) {
            val candidate = mirror.removeSuffix("/")
            if (candidate != current && testMirror(client, candidate)) {
                markHealthy(candidate)
                return candidate
            }
        }

        return current
    }

    private fun testMirror(client: OkHttpClient, url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 300..399
            }
        } catch (_: IOException) {
            false
        }
    }
}
