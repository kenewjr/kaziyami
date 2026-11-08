package eu.kanade.tachiyomi.animeextension.zh.threexchina

import app.kaziyami.core.InMemorySharedPreferences
import eu.kanade.tachiyomi.animesource.model.SAnime
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThreeXChinaTest {

    private lateinit var source: ThreeXChina

    @BeforeEach
    fun setup() {
        source = ThreeXChina()
        source.preferences.edit().putBoolean("age_gate_confirmed", true).apply()
    }

    private fun mockResponse(url: String, fixtureName: String): Response {
        val stream = javaClass.getResourceAsStream("/3xchina/$fixtureName")
            ?: error("Fixture /3xchina/$fixtureName not found")
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
        val resp = mockResponse("https://3xchina.net/most-viewed/page/1/", "popular.html")
        val page = source.popularAnimeParse(resp)

        assertThat(page.animes).hasSize(2)
        val first = page.animes[0]
        assertThat(first.title).contains("深閨少婦")
        assertThat(first.url).isEqualTo("/ama-628/")
        assertThat(first.thumbnail_url).isEqualTo("https://3xchina.page/wp-content/uploads/2026/11/ama628.jpg")
        assertThat(page.hasNextPage).isTrue()
    }

    @Test
    fun animeDetailsParse_extractsMetadata() {
        val resp = mockResponse("https://3xchina.net/ama-628/", "details.html")
        val anime = source.animeDetailsParse(resp)

        assertThat(anime.title).contains("深閨少婦")
        assertThat(anime.description).contains("古裝劇情作品")
        assertThat(anime.genre).contains("China AV")
    }

    @Test
    fun episodeListParse_extractsSingleVideo() {
        val resp = mockResponse("https://3xchina.net/ama-628/", "details.html")
        val eps = source.episodeListParse(resp)

        assertThat(eps).hasSize(1)
        assertThat(eps[0].name).isEqualTo("Full Video")
        assertThat(eps[0].url).isEqualTo("https://3xchina.net/ama-628/")
    }

    @Test
    fun videoListParse_extractsIframes() {
        val resp = mockResponse("https://3xchina.net/ama-628/", "video_player.html")
        val videos = source.videoListParse(resp)

        assertThat(videos).isNotEmpty
    }

    @Test
    fun searchAnimeRequest_composesSortingAndCategory() {
        val filters = source.getFilterList()
        val sortFilter = filters.filterIsInstance<ThreeXChina.ThreeXChinaSortFilter>().first()
        val catFilter = filters.filterIsInstance<ThreeXChina.ThreeXChinaCategoryFilter>().first()

        sortFilter.state = 1 // most-viewed
        catFilter.state = 1 // chinese-av

        val reqQuery = source.searchAnimeRequest(1, "ama", filters)
        assertThat(reqQuery.url.queryParameter("s")).isEqualTo("ama")

        val reqFilter = source.searchAnimeRequest(1, "", filters)
        assertThat(reqFilter.url.encodedPath).isEqualTo("/category/chinese-av/")
    }
}

