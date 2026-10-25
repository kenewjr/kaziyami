package eu.kanade.tachiyomi.lib.core

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class MirrorManager(
    val defaultBaseUrl: String,
    val mirrors: List<String> = emptyList(),
    private var preferences: (() -> String?)? = null
) {
    val allMirrors: List<String> = (listOf(defaultBaseUrl) + mirrors).distinct()
    private var lastHealthyMirror: String? = null
    private var customOverride: String? = null

    fun setOverrideUrl(override: String?) {
        customOverride = override?.trim()
    }

    fun getBaseUrl(): String {
        val userOverride = customOverride ?: preferences?.invoke()?.trim()
        if (!userOverride.isNullOrEmpty()) {
            return userOverride.removeSuffix("/")
        }
        return lastHealthyMirror ?: defaultBaseUrl.removeSuffix("/")
    }

    fun failover(current: String = getBaseUrl()): String {
        val next = allMirrors.firstOrNull { it.removeSuffix("/") != current.removeSuffix("/") }
            ?: defaultBaseUrl
        markHealthy(next)
        return next
    }

    fun markHealthy(mirrorUrl: String) {
        lastHealthyMirror = mirrorUrl.removeSuffix("/")
    }

    fun markUnhealthy(mirrorUrl: String) {
        if (lastHealthyMirror == mirrorUrl.removeSuffix("/")) {
            lastHealthyMirror = null
        }
    }

    suspend fun getWorkingMirror(client: OkHttpClient): String {
        val current = getBaseUrl()
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
        val request = Request.Builder()
            .url(url)
            .head()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 300..399
            }
        } catch (_: IOException) {
            false
        }
    }
}
