package eu.kanade.tachiyomi.animeextension.zh.threexchina

import app.kaziyami.core.InMemorySharedPreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
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
        val resp = mockResponse("https://3xchina.page/most-viewed/page/1/", "popular.html")
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
        val resp = mockResponse("https://3xchina.page/ama-628/", "details.html")
        val anime = source.animeDetailsParse(resp)

        assertThat(anime.title).contains("深閨少婦")
        assertThat(anime.description).contains("古裝劇情作品")
        assertThat(anime.genre).contains("China AV")
    }

    @Test
    fun episodeListParse_extractsSingleVideo() {
        val resp = mockResponse("https://3xchina.page/ama-628/", "details.html")
        val eps = source.episodeListParse(resp)

        assertThat(eps).hasSize(1)
        assertThat(eps[0].name).isEqualTo("Full Video")
        assertThat(eps[0].url).isEqualTo("/ama-628/")
    }

    @Test
    fun videoListParse_extractsIframes() {
        val resp = mockResponse("https://3xchina.page/ama-628/", "video_player.html")
        val videos = source.videoListParse(resp)

        assertThat(videos).isNotEmpty
    }

    @Test
    fun videoListParse_throwsOnUnextractableEmbed() {
        val html = """
            <div class="responsive-player">
                <iframe src="https://unknown-host.example/embed/123"></iframe>
            </div>
        """.trimIndent()
        val request = Request.Builder().url("https://3xchina.page/ama-628/").build()
        val body = html.toResponseBody("text/html; charset=utf-8".toMediaType())
        val resp = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

        org.junit.jupiter.api.assertThrows<app.kaziyami.core.SourceParseException> {
            source.videoListParse(resp)
        }
    }

    @Test
    fun searchAnimeRequest_composesSortingAndCategory() {
        val filters = source.getFilterList()
        val sortFilter = filters.filterIsInstance<ThreeXChina.ThreeXChinaSortFilter>().first()
        val catFilter = filters.filterIsInstance<ThreeXChina.ThreeXChinaCategoryFilter>().first()

        sortFilter.state = 1 // latest
        catFilter.state = 1 // chinese-av

        val reqQuery = source.searchAnimeRequest(1, "ama", filters)
        assertThat(reqQuery.url.queryParameter("s")).isEqualTo("ama")
        assertThat(reqQuery.url.queryParameter("filter")).isEqualTo("latest")

        val reqFilter = source.searchAnimeRequest(1, "", filters)
        assertThat(reqFilter.url.encodedPath).isEqualTo("/category/chinese-av/")
        assertThat(reqFilter.url.queryParameter("filter")).isEqualTo("latest")
    }

    @Test
    fun searchAnimeRequest_padsSingleCharacterQuery() {
        val req = source.searchAnimeRequest(1, "a", source.getFilterList())
        assertThat(req.url.queryParameter("s")).isEqualTo("aa")
    }

    @Test
    fun testKototoroFilterHeaderSeparation() {
        val filters = source.getFilterList()
        val headers = filters.filterIsInstance<AnimeFilter.Header>().map { it.name }
        assertThat(headers.any { it.contains("排序") || it.contains("Sort") }).isTrue()
        assertThat(headers.any { it.contains("分類") || it.contains("Category") }).isTrue()
        assertThat(headers.any { it.contains("標籤") || it.contains("Tag") }).isTrue()
        assertThat(headers.size).isGreaterThanOrEqualTo(3)
    }
}

