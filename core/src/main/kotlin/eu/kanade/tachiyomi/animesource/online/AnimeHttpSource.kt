package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.BrowserHeaders
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest

abstract class AnimeHttpSource : AnimeSource {
    abstract val baseUrl: String

    open val supportsLatest: Boolean = true
    open val network: NetworkHelper = NetworkHelper()
    open val client: OkHttpClient = network.client
    open val headers: Headers by lazy { BrowserHeaders.defaultHeaders(baseUrl) }

    fun GET(url: String, headers: Headers = this.headers): Request =
        Request.Builder().url(url).headers(headers).build()

    fun POST(url: String, headers: Headers = this.headers, body: okhttp3.RequestBody): Request =
        Request.Builder().url(url).headers(headers).post(body).build()

    fun Response.asJsoup(): org.jsoup.nodes.Document =
        org.jsoup.Jsoup.parse(body?.string() ?: "", request.url.toString())

    override val id: Long by lazy {
        val key = "${name.lowercase()}/$lang/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) } and Long.MAX_VALUE
    }

    abstract fun popularAnimeRequest(page: Int): Request
    abstract fun popularAnimeParse(response: Response): AnimesPage

    abstract fun latestUpdatesRequest(page: Int): Request
    abstract fun latestUpdatesParse(response: Response): AnimesPage

    abstract fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request
    abstract fun searchAnimeParse(response: Response): AnimesPage

    abstract fun animeDetailsParse(response: Response): SAnime
    abstract fun episodeListParse(response: Response): List<SEpisode>
    abstract fun videoListParse(response: Response): List<Video>

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val request = popularAnimeRequest(page)
        return client.newCall(request).execute().use { response ->
            popularAnimeParse(response)
        }
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val request = searchAnimeRequest(page, query, filters)
        return client.newCall(request).execute().use { response ->
            searchAnimeParse(response)
        }
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val request = latestUpdatesRequest(page)
        return client.newCall(request).execute().use { response ->
            latestUpdatesParse(response)
        }
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val request = Request.Builder()
            .url(if (anime.url.startsWith("http")) anime.url else "$baseUrl${anime.url}")
            .headers(headers)
            .build()
        return client.newCall(request).execute().use { response ->
            animeDetailsParse(response).apply { initialized = true }
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val request = Request.Builder()
            .url(if (anime.url.startsWith("http")) anime.url else "$baseUrl${anime.url}")
            .headers(headers)
            .build()
        return client.newCall(request).execute().use { response ->
            episodeListParse(response)
        }
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val request = Request.Builder()
            .url(if (episode.url.startsWith("http")) episode.url else "$baseUrl${episode.url}")
            .headers(headers)
            .build()
        return client.newCall(request).execute().use { response ->
            videoListParse(response)
        }
    }
}
