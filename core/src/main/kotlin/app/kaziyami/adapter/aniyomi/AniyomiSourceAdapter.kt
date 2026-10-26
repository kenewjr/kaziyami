package app.kaziyami.adapter.aniyomi

import app.kaziyami.engine.KaziyamiEpisode
import app.kaziyami.engine.KaziyamiMedia
import app.kaziyami.engine.KaziyamiSource
import app.kaziyami.engine.KaziyamiVideo
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Adapter bridging a platform-neutral [KaziyamiSource] into Aniyomi's [AnimeHttpSource] contract.
 */
open class AniyomiSourceAdapter(
    val engineSource: KaziyamiSource
) : AnimeHttpSource() {

    override val name: String = engineSource.name
    override val baseUrl: String = engineSource.baseUrl
    override val lang: String = engineSource.lang
    override val supportsLatest: Boolean = true

    override fun popularAnimeRequest(page: Int): Request {
        return Request.Builder().url(baseUrl).build()
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val result = engineSource.popular(1)
        return AnimesPage(result.items.map { it.toSAnime() }, result.hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return Request.Builder().url(baseUrl).build()
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val result = engineSource.latest(1)
        return AnimesPage(result.items.map { it.toSAnime() }, result.hasNextPage)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return Request.Builder().url("$baseUrl/?s=$query").build()
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val result = engineSource.search(1, "")
        return AnimesPage(result.items.map { it.toSAnime() }, result.hasNextPage)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val media = engineSource.details(response.request.url.toString())
        return media.toSAnime()
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val episodes = engineSource.episodes(response.request.url.toString())
        return episodes.map { it.toSEpisode() }
    }

    override fun videoListParse(response: Response): List<Video> {
        val videos = engineSource.videos(response.request.url.toString())
        return videos.map { it.toVideo() }
    }

    companion object {
        fun KaziyamiMedia.toSAnime(): SAnime {
            val media = this
            return SAnime.create().apply {
                url = media.url
                title = media.title
                thumbnail_url = media.coverUrl
                status = media.status
                genre = media.genres.joinToString(", ")
                description = media.description
            }
        }

        fun KaziyamiEpisode.toSEpisode(): SEpisode {
            val ep = this
            return SEpisode.create().apply {
                url = ep.url
                name = ep.name
                episode_number = ep.episodeNumber
                date_upload = ep.dateUpload
            }
        }

        fun KaziyamiVideo.toVideo(): Video {
            val v = this
            val headerBuilder = Headers.Builder()
            v.headers.forEach { (k, valStr) -> headerBuilder.add(k, valStr) }
            val tracks = v.subtitles.map { Track(it.url, it.lang) }
            return Video(
                url = v.url,
                quality = v.quality,
                videoUrl = v.videoUrl,
                headers = headerBuilder.build(),
                subtitleTracks = tracks
            )
        }
    }
}
