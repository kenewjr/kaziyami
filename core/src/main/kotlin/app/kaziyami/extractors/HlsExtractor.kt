package app.kaziyami.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import app.kaziyami.core.UrlUtils
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Lightweight HLS master playlist parser that extracts variant quality streams.
 */
object HlsExtractor {
    fun extract(masterUrl: String, headers: Headers, client: OkHttpClient, prefix: String = ""): List<Video> {
        val videos = mutableListOf<Video>()
        try {
            val req = Request.Builder().url(masterUrl).headers(headers).build()
            val response = client.newCall(req).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            if (!body.contains("#EXT-X-STREAM-INF")) return emptyList()

            val lines = body.lines()
            var currentQuality = ""

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                    val resMatch = Regex("""RESOLUTION=\d+x(\d+)""").find(trimmed)
                    currentQuality = resMatch?.groupValues?.get(1)?.let { "${it}p" } ?: ""
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val streamUrl = UrlUtils.resolve(masterUrl, trimmed)
                    val qName = currentQuality.ifEmpty { "Default" }
                    val fullLabel = if (prefix.isNotBlank()) "$prefix $qName" else qName
                    videos.add(Video(streamUrl, fullLabel, streamUrl, headers))
                    currentQuality = ""
                }
            }
        } catch (_: Throwable) {
        }
        return videos
    }
}
