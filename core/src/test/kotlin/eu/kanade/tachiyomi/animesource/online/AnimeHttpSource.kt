package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.security.MessageDigest

/**
 * Headless test double for unit tests running on desktop JVM.
 * Implements execution without throwing "Stub!".
 * Resides exclusively in test source set; never packaged into APKs.
 */
abstract class AnimeHttpSource : AnimeCatalogueSource {

    abstract val baseUrl: String
    override val id: Long by lazy {
        val key = "${name.lowercase()}/$lang/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) } and Long.MAX_VALUE
    }

    open val client: OkHttpClient = OkHttpClient()
    open val headers: Headers = Headers.Builder().build()
    override val supportsLatest: Boolean = true

    open fun headersBuilder(): Headers.Builder = Headers.Builder()

    protected abstract fun popularAnimeRequest(page: Int): Request
    protected abstract fun popularAnimeParse(response: Response): AnimesPage

    protected abstract fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request
    protected abstract fun searchAnimeParse(response: Response): AnimesPage

    protected abstract fun latestUpdatesRequest(page: Int): Request
    protected abstract fun latestUpdatesParse(response: Response): AnimesPage

    open fun animeDetailsRequest(anime: SAnime): Request = Request.Builder().url(baseUrl + anime.url).build()
    protected abstract fun animeDetailsParse(response: Response): SAnime

    open fun episodeListRequest(anime: SAnime): Request = Request.Builder().url(baseUrl + anime.url).build()
    protected abstract fun episodeListParse(response: Response): List<SEpisode>

    open fun videoListRequest(episode: SEpisode): Request = Request.Builder().url(baseUrl + episode.url).build()
    protected open fun videoListParse(response: Response): List<Video> = emptyList()

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
        val request = animeDetailsRequest(anime)
        return client.newCall(request).execute().use { response ->
            animeDetailsParse(response)
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val request = episodeListRequest(anime)
        return client.newCall(request).execute().use { response ->
            episodeListParse(response)
        }
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val request = videoListRequest(episode)
        return client.newCall(request).execute().use { response ->
            videoListParse(response)
        }
    }

    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = Observable.just(AnimesPage(emptyList(), false))
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> = Observable.just(AnimesPage(emptyList(), false))
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = Observable.just(AnimesPage(emptyList(), false))
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = Observable.just(anime)
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = Observable.just(emptyList())
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> = Observable.just(emptyList())
    open fun fetchVideoUrl(video: Video): Observable<String> = Observable.just(video.videoUrl.orEmpty())
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()
}