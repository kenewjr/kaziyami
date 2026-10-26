package app.kaziyami.extractors

import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Handles expiration and re-resolution of signed video stream URLs.
 * Never caches signed URLs beyond their TTL, and re-resolves once if an expired signature (403/410) is received.
 */
object VideoResolverHelper {

    fun resolveWithRefresh(
        pageUrl: String,
        initialVideoUrl: String,
        client: OkHttpClient = OkHttpClient(),
        resolver: (pageUrl: String) -> String
    ): String {
        val checkCode = checkStreamStatus(initialVideoUrl, client)
        if (checkCode in 200..399) {
            return initialVideoUrl
        }

        // Re-resolve once on 403 Forbidden or 410 Gone (expired token / signature)
        if (checkCode in listOf(403, 410)) {
            val freshUrl = resolver(pageUrl)
            val freshCode = checkStreamStatus(freshUrl, client)
            if (freshCode in 200..399) {
                return freshUrl
            }
        }

        return initialVideoUrl
    }

    private fun checkStreamStatus(url: String, client: OkHttpClient): Int {
        return try {
            val req = Request.Builder()
                .url(url)
                .head()
                .build()
            client.newCall(req).execute().use { it.code }
        } catch (_: Exception) {
            0
        }
    }
}
