package eu.kanade.tachiyomi.animeextension.zh.hanime1

import app.kaziyami.core.ContentPolicy
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
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
    fun popularAnimeRequest_constructsValidUrlWithoutFormatError() {
        val req1 = source.popularAnimeRequest(1)
        assertThat(req1.url.queryParameter("sort")).isEqualTo("本日排行")
        assertThat(req1.url.queryParameter("page")).isNull()

        val req2 = source.popularAnimeRequest(2)
        assertThat(req2.url.queryParameter("sort")).isEqualTo("本日排行")
        assertThat(req2.url.queryParameter("page")).isEqualTo("2")
    }

    @Test
    fun latestUpdatesRequest_constructsValidUrlWithoutFormatError() {
        val req1 = source.latestUpdatesRequest(1)
        assertThat(req1.url.queryParameter("sort")).isEqualTo("最新上市")
        assertThat(req1.url.queryParameter("page")).isNull()

        val req2 = source.latestUpdatesRequest(3)
        assertThat(req2.url.queryParameter("sort")).isEqualTo("最新上市")
        assertThat(req2.url.queryParameter("page")).isEqualTo("3")
    }

    @Test
    fun searchAnimeRequest_composesSortingAndGenre() {
        val filters = source.getFilterList()
        val sortFilter = filters.filterIsInstance<Hanime1.Hanime1SortFilter>().first()
        val genreFilter = filters.filterIsInstance<Hanime1.Hanime1GenreFilter>().first()
        val durationFilter = filters.filterIsInstance<Hanime1.Hanime1DurationFilter>().first()
        val dateFilter = filters.filterIsInstance<Hanime1.Hanime1DateFilter>().first()
        val brandFilter = filters.filterIsInstance<Hanime1.Hanime1BrandFilter>().first()

        sortFilter.state = Hanime1.SORTS.indexOfFirst { it.slug == "本月排行" }
        genreFilter.state = 4 // 3D動畫 (slug = 3DCG)
        durationFilter.state = 2 // 5分鐘以上 (slug = 5 分鐘 +)
        dateFilter.state = 1 // 過去24小時 (slug = 過去 24 小時)
        brandFilter.state = 1 // PoRO

        val req = source.searchAnimeRequest(1, "action", filters)
        assertThat(req.url.queryParameter("query")).isEqualTo("action")
        assertThat(req.url.queryParameter("sort")).isEqualTo("本月排行")
        assertThat(req.url.queryParameter("genre")).isEqualTo("3DCG")
        assertThat(req.url.queryParameter("duration")).isEqualTo("5 分鐘 +")
        assertThat(req.url.queryParameter("date")).isEqualTo("過去 24 小時")
        assertThat(req.url.queryParameter("brands[]")).isEqualTo("PoRO")
    }

    @Test
    fun searchAnimeRequest_handlesMultiSelectTags() {
        val filters = source.getFilterList()
        val tagGroups = filters.filterIsInstance<Hanime1.TagGroup>()
        assertThat(tagGroups).isNotEmpty

        val specGroup = tagGroups.first { it.name.contains("規格") }
        val uncensoredTag = specGroup.state.first { it.slug == "無碼" }
        uncensoredTag.state = true

        val themeGroup = tagGroups.first { it.name.contains("劇情主題") }
        val pureLoveTag = themeGroup.state.first { it.slug == "純愛" }
        pureLoveTag.state = true

        val req = source.searchAnimeRequest(1, "", filters)
        assertThat(req.url.queryParameter("query")).isNull()
        val tagParams = req.url.queryParameterValues("tags[]")
        assertThat(tagParams).containsExactlyInAnyOrder("無碼", "純愛")
    }

    @Test
    fun searchAnimeRequest_emptyQueryDoesNotEmitQueryParam() {
        val filters = source.getFilterList()
        val genreFilter = filters.filterIsInstance<Hanime1.Hanime1GenreFilter>().first()
        genreFilter.state = 1 // 裏番

        val req = source.searchAnimeRequest(1, "", filters)
        assertThat(req.url.queryParameter("query")).isNull()
        assertThat(req.url.queryParameter("genre")).isEqualTo("裏番")
        assertThat(req.url.toString()).doesNotContain("query=")
    }

    @Test
    fun contentPolicy_dropsLiveActionAndCosplay() {
        val safeAnime = SAnime.create().apply {
            title = "夜勤病棟 第一話"
            genre = "裏番, 3DCG"
        }
        val liveActionAnime = SAnime.create().apply {
            title = "Cosplay Show 1"
            genre = "真人, Cosplay"
        }

        val filtered = ContentPolicy.filterAnimes(listOf(safeAnime, liveActionAnime))
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].title).isEqualTo("夜勤病棟 第一話")
    }

    @Test
    fun testKototoroFilterHeaderSeparation() {
        val filters = source.getFilterList()
        val headers = filters.filterIsInstance<AnimeFilter.Header>().map { it.name }
        assertThat(headers.any { it.contains("排序") || it.contains("Sort") }).isTrue()
        assertThat(headers.any { it.contains("類型") || it.contains("Genre") }).isTrue()
        assertThat(headers.size).isGreaterThanOrEqualTo(5)
    }
}
