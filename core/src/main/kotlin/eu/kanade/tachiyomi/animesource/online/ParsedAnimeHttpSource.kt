package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class ParsedAnimeHttpSource : AnimeHttpSource() {

    abstract fun popularAnimeSelector(): String
    abstract fun popularAnimeFromElement(element: Element): SAnime
    abstract fun popularAnimeNextPageSelector(): String?

    abstract fun latestUpdatesSelector(): String
    abstract fun latestUpdatesFromElement(element: Element): SAnime
    abstract fun latestUpdatesNextPageSelector(): String?

    abstract fun searchAnimeSelector(): String
    abstract fun searchAnimeFromElement(element: Element): SAnime
    abstract fun searchAnimeNextPageSelector(): String?

    abstract fun animeDetailsParse(document: Document): SAnime
    abstract fun episodeListSelector(): String
    abstract fun episodeFromElement(element: Element): SEpisode

    abstract fun videoListSelector(): String
    abstract fun videoFromElement(element: Element): Video
    abstract fun videoUrlParse(document: Document): String

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = Jsoup.parse(response.body?.string() ?: "", baseUrl)
        val animes = document.select(popularAnimeSelector()).map { popularAnimeFromElement(it) }
        val hasNextPage = popularAnimeNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(animes, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = Jsoup.parse(response.body?.string() ?: "", baseUrl)
        val animes = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(animes, hasNextPage)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = Jsoup.parse(response.body?.string() ?: "", baseUrl)
        val animes = document.select(searchAnimeSelector()).map { searchAnimeFromElement(it) }
        val hasNextPage = searchAnimeNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(animes, hasNextPage)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val document = Jsoup.parse(response.body?.string() ?: "", baseUrl)
        return animeDetailsParse(document)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = Jsoup.parse(response.body?.string() ?: "", baseUrl)
        return document.select(episodeListSelector()).map { episodeFromElement(it) }
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = Jsoup.parse(response.body?.string() ?: "", baseUrl)
        return document.select(videoListSelector()).map { videoFromElement(it) }
    }
}
