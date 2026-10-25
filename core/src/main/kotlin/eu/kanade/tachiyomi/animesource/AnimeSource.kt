package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

interface AnimeSource {
    val id: Long
    val name: String
    val lang: String

    suspend fun getPopularAnime(page: Int): AnimesPage
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage
    suspend fun getLatestUpdates(page: Int): AnimesPage
    suspend fun getAnimeDetails(anime: SAnime): SAnime
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>
    suspend fun getVideoList(episode: SEpisode): List<Video>

    fun getFilterList(): AnimeFilterList = AnimeFilterList()
}
