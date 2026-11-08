package eu.kanade.tachiyomi.animeextension.zh.hanime1

import app.kaziyami.core.InMemorySharedPreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Hanime1Test {

    private lateinit var source: Hanime1

    @BeforeEach
    fun setup() {
        source = Hanime1()
        source.preferences.edit().putBoolean("age_gate_confirmed", true).apply()
    }

    private fun mockResponse(url: String, fixtureName: String): Response {
        val stream = javaClass.getResourceAsStream("/hanime1/$fixtureName")
            ?: error("Fixture /hanime1/$fixtureName not found")
        val content = stream.bufferedReader().readText()
        val request = Request.Builder().url(url).build()
        val body = content.toResponseBody("text/html; charset=utf-8".toMediaType())
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    @Test
    fun popularAnimeParse_extractsItemsAndNextPage() {
        val resp = mockResponse("https://hanime1.me/search?sort=本日排行&page=1", "popular.html")
        val page = source.popularAnimeParse(resp)

        assertThat(page.animes).hasSize(3)
        val first = page.animes[0]
        assertThat(first.title).isEqualTo("夜勤病棟 第一話")
        assertThat(first.url).isEqualTo("/watch?v=10101")
        assertThat(first.thumbnail_url).isEqualTo("https://vdownload.hembed.com/image/10101.jpg")
        assertThat(page.hasNextPage).isTrue()
    }

    @Test
    fun animeDetailsParse_extractsMetadata() {
        val resp = mockResponse("https://hanime1.me/watch?v=10101", "details.html")
        val anime = source.animeDetailsParse(resp)

        assertThat(anime.title).isEqualTo("夜勤病棟 第一話")
        assertThat(anime.description).contains("經典醫療病棟故事第一話")
        assertThat(anime.genre).contains("裏番")
    }

    @Test
    fun episodeListParse_extractsRelatedEpisodes() {
        val resp = mockResponse("https://hanime1.me/watch?v=10101", "details.html")
        val eps = source.episodeListParse(resp)

        assertThat(eps).hasSize(2)
        assertThat(eps[0].name).isEqualTo("第 1 集")
        assertThat(eps[0].url).isEqualTo("https://hanime1.me/watch?v=10101")
        assertThat(eps[1].name).isEqualTo("第 2 集")
    }

    @Test
    fun videoListParse_extractsDirectVideoSources() {
        val resp = mockResponse("https://hanime1.me/watch?v=10101", "video_player.html")
        val videos = source.videoListParse(resp)

        assertThat(videos).isNotEmpty
        assertThat(videos.any { it.videoUrl?.contains("10101_1080p.mp4") == true }).isTrue()
    }

    @Test
    fun searchAnimeRequest_composesSortingAndGenre() {
        val filters = source.getFilterList()
        val sortFilter = filters.filterIsInstance<Hanime1.Hanime1SortFilter>().first()
        val genreFilter = filters.filterIsInstance<Hanime1.Hanime1GenreFilter>().first()

        sortFilter.state = 4 // 本月排行
        genreFilter.state = 4 // 3DCG

        val req = source.searchAnimeRequest(1, "action", filters)
        assertThat(req.url.queryParameter("query")).isEqualTo("action")
        assertThat(req.url.queryParameter("sort")).isEqualTo("本月排行")
        assertThat(req.url.queryParameter("genre")).isEqualTo("3DCG")
    }
}

