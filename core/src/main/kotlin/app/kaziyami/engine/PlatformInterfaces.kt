package app.kaziyami.engine

import okhttp3.OkHttpClient

/**
 * Platform-neutral interfaces abstracting host-specific services.
 * The core engine never imports android.* or host application classes.
 */

interface HttpClientProvider {
    fun getClient(): OkHttpClient
}

interface ChallengeSolver {
    fun solve(url: String, userAgent: String): Boolean
}

interface CookieStore {
    fun getCookie(url: String): String?
    fun setCookie(url: String, cookie: String)
}

interface PreferenceStore {
    fun getString(key: String, defaultValue: String): String
    fun setString(key: String, value: String)
}

interface Logger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * Pure platform-neutral data models for media, episodes, and stream endpoints.
 */

data class KaziyamiMedia(
    val id: String,
    val title: String,
    val url: String,
    val coverUrl: String = "",
    val status: Int = 0,
    val genres: List<String> = emptyList(),
    val description: String = ""
)

data class KaziyamiEpisode(
    val name: String,
    val url: String,
    val episodeNumber: Float = 0f,
    val dateUpload: Long = 0L
)

data class KaziyamiTrack(
    val url: String,
    val lang: String
)

data class KaziyamiVideo(
    val url: String,
    val quality: String,
    val videoUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<KaziyamiTrack> = emptyList()
)

data class KaziyamiPage<T>(
    val items: List<T>,
    val hasNextPage: Boolean
)

/**
 * Platform-neutral source contract implemented by all 6 Kaziyami adult anime scrapers.
 */
interface KaziyamiSource {
    val id: Long
    val name: String
    val lang: String
    val baseUrl: String

    fun popular(page: Int): KaziyamiPage<KaziyamiMedia>
    fun latest(page: Int): KaziyamiPage<KaziyamiMedia>
    fun search(page: Int, query: String, filters: List<Any> = emptyList()): KaziyamiPage<KaziyamiMedia>
    fun details(url: String): KaziyamiMedia
    fun episodes(url: String): List<KaziyamiEpisode>
    fun videos(url: String): List<KaziyamiVideo>
}
