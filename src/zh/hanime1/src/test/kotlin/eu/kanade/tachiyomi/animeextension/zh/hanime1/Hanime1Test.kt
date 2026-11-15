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
    fun popularAnimeParse_filtersOutSponsoredGamesAndExternalAds() {
        val html = """
            <div class="search-result-items">
                <div class="video-item-container">
                    <a href="https://ero-labs.com/zh/game.html?id=1" title="慾望王座">
                        <img src="https://example.com/throne.jpg" alt="慾望王座"/>
                        <h5 class="video-item-title">慾望王座</h5>
                    </a>
                </div>
                <div class="video-item-container">
                    <a href="https://ero-labs.com/zh/game.html?id=2" title="櫻境物語">
                        <img src="https://example.com/cherry.jpg" alt="櫻境物語"/>
                        <h5 class="video-item-title">櫻境物語</h5>
                    </a>
                </div>
                <div class="video-item-container">
                    <a href="https://hanime1.me/watch?v=10101">
                        <img src="https://vdownload.hembed.com/image/10101.jpg" alt="夜勤病棟 第一話"/>
                        <h5 class="video-item-title">夜勤病棟 第一話</h5>
                    </a>
                </div>
            </div>
        """.trimIndent()
        val req = Request.Builder().url("https://hanime1.me/search?sort=本日排行").build()
        val body = html.toResponseBody("text/html; charset=utf-8".toMediaType())
        val resp = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()

        val page = source.popularAnimeParse(resp)
        assertThat(page.animes).hasSize(1)
        assertThat(page.animes[0].title).isEqualTo("夜勤病棟 第一話")
        assertThat(page.animes.any { it.title.contains("慾望王座") || it.title.contains("櫻境物語") }).isFalse()
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
    fun episodeListParse_extractsPlaylistEpisodesAndIgnoresRelatedVideos() {
        val resp = mockResponse("https://hanime1.me/watch?v=10101", "details.html")
        val eps = source.episodeListParse(resp)

        assertThat(eps).hasSize(2)
        assertThat(eps[0].name).isEqualTo("第 1 集")
        assertThat(eps[0].url).isEqualTo("/watch?v=10101")
        assertThat(eps[1].name).isEqualTo("第 2 集")
        assertThat(eps.none { it.name.contains("推薦影片") }).isTrue()
    }

    @Test
    fun episodeListParse_singleVideoFallbackWhenNoPlaylist() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>單獨影片 - Hanime1</title></head>
            <body>
            <h1 id="shareBtn-title">單獨作品 第一夜</h1>
            <div class="video-details-wrapper"><h4>單獨作品 第一夜</h4></div>
            <div class="related-watch-wrap">
                <a href="https://hanime1.me/watch?v=99999">推薦影片 A</a>
                <a href="https://hanime1.me/watch?v=88888">推薦影片 B</a>
            </div>
            </body>
            </html>
        """.trimIndent()
        val req = Request.Builder().url("https://hanime1.me/watch?v=55555").build()
        val body = html.toResponseBody("text/html; charset=utf-8".toMediaType())
        val resp = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()

        val eps = source.episodeListParse(resp)
        assertThat(eps).hasSize(1)
        assertThat(eps[0].name).isEqualTo("單獨作品 第一夜")
        assertThat(eps[0].url).isEqualTo("/watch?v=55555")
        assertThat(eps.none { it.name.contains("推薦影片") }).isTrue()
    }

    @Test
    fun popularAnimeParse_detectsCloudflareChallenge() {
        val cfHtml = "<html><head><title>Just a moment...</title></head><body><div class='cf-turnstile'></div></body></html>"
        val req = Request.Builder().url("https://hanime1.me/").build()
        val body = cfHtml.toResponseBody("text/html; charset=utf-8".toMediaType())
        val resp = Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .body(body)
            .build()

        org.junit.jupiter.api.assertThrows<app.kaziyami.core.SourceParseException> {
            source.popularAnimeParse(resp)
        }.also { ex ->
            assertThat(ex.message).contains("Cloudflare")
            assertThat(ex.message).contains("WebView")
        }
    }

    @Test
    fun searchAnimeParse_handlesChineseEmptyState() {
        val emptyHtml = "<html><head><title>搜尋結果</title></head><body><div>查無結果，沒有相關影片</div></body></html>"
        val req = Request.Builder().url("https://hanime1.me/search?query=xyz").build()
        val body = emptyHtml.toResponseBody("text/html; charset=utf-8".toMediaType())
        val resp = Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

        val page = source.searchAnimeParse(resp)
        assertThat(page.animes).isEmpty()
        assertThat(page.hasNextPage).isFalse()
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
