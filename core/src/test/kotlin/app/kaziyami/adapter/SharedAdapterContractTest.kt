package app.kaziyami.adapter

import app.kaziyami.adapter.aniyomi.AniyomiSourceAdapter
import app.kaziyami.adapter.kototoro.KototoroPlugin
import app.kaziyami.adapter.kototoro.KototoroSourceWrapper
import app.kaziyami.engine.KaziyamiEpisode
import app.kaziyami.engine.KaziyamiMedia
import app.kaziyami.engine.KaziyamiPage
import app.kaziyami.engine.KaziyamiSource
import app.kaziyami.engine.KaziyamiTrack
import app.kaziyami.engine.KaziyamiVideo
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SharedAdapterContractTest {

    private val fakeSource = object : KaziyamiSource {
        override val id: Long = 7073589375897212735L
        override val name: String = "FakeNekopoi"
        override val lang: String = "id"
        override val baseUrl: String = "https://nekopoi.care"

        override fun popular(page: Int): KaziyamiPage<KaziyamiMedia> = KaziyamiPage(
            listOf(
                KaziyamiMedia("1", "Kanojo x Kanojo Episode 1", "/hentai/kanojo-1/", "https://nekopoi.care/cover1.jpg", 0, listOf("Hentai", "Romance"), "Test description")
            ),
            true
        )

        override fun latest(page: Int): KaziyamiPage<KaziyamiMedia> = popular(page)

        override fun search(page: Int, query: String, filters: List<Any>): KaziyamiPage<KaziyamiMedia> = popular(page)

        override fun details(url: String): KaziyamiMedia = KaziyamiMedia(
            "1", "Kanojo x Kanojo Episode 1", url, "https://nekopoi.care/cover1.jpg", 1, listOf("Hentai", "Romance"), "Full anime details"
        )

        override fun episodes(url: String): List<KaziyamiEpisode> = listOf(
            KaziyamiEpisode("Episode 1", "$url/ep1", 1.0f, 1700000000000L),
            KaziyamiEpisode("Episode 2", "$url/ep2", 2.0f, 1700100000000L)
        )

        override fun videos(url: String): List<KaziyamiVideo> = listOf(
            KaziyamiVideo(
                url = url,
                quality = "StreamPoi 720p",
                videoUrl = "https://streampoi.com/master.m3u8",
                headers = mapOf("Referer" to url, "User-Agent" to "Mozilla/5.0"),
                subtitles = listOf(KaziyamiTrack("https://streampoi.com/sub.vtt", "id"))
            )
        )
    }

    private lateinit var aniyomiAdapter: AniyomiSourceAdapter
    private lateinit var kototoroWrapper: KototoroSourceWrapper

    private fun mockResponse(url: String): Response = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body("<html></html>".toResponseBody())
        .build()

    @BeforeEach
    fun setup() {
        aniyomiAdapter = AniyomiSourceAdapter(fakeSource)
        kototoroWrapper = KototoroSourceWrapper(fakeSource)
        KototoroPlugin.clear()
        KototoroPlugin.registerSource(fakeSource)
    }

    @Test
    fun popularAnimeEquivalence() {
        val dummyResp = mockResponse(fakeSource.baseUrl)
        val aniyomiPage = aniyomiAdapter.popularAnimeParse(dummyResp)
        val kototoroPage = kototoroWrapper.popularAnimeParse(dummyResp)

        assertEquals(aniyomiPage.hasNextPage, kototoroPage.hasNextPage)
        assertEquals(aniyomiPage.animes.size, kototoroPage.animes.size)
        assertEquals(aniyomiPage.animes[0].title, kototoroPage.animes[0].title)
        assertEquals(aniyomiPage.animes[0].url, kototoroPage.animes[0].url)
        assertEquals(aniyomiPage.animes[0].thumbnail_url, kototoroPage.animes[0].thumbnail_url)
    }

    @Test
    fun detailsEquivalence() {
        val dummyResp = mockResponse("https://nekopoi.care/hentai/kanojo-1/")
        val aniyomiDetails = aniyomiAdapter.animeDetailsParse(dummyResp)
        val kototoroDetails = kototoroWrapper.animeDetailsParse(dummyResp)

        assertEquals(aniyomiDetails.title, kototoroDetails.title)
        assertEquals(aniyomiDetails.genre, kototoroDetails.genre)
        assertEquals(aniyomiDetails.description, kototoroDetails.description)
        assertEquals(aniyomiDetails.status, kototoroDetails.status)
    }

    @Test
    fun episodeListEquivalence() {
        val dummyResp = mockResponse("https://nekopoi.care/hentai/kanojo-1/")
        val aniyomiEps = aniyomiAdapter.episodeListParse(dummyResp)
        val kototoroEps = kototoroWrapper.episodeListParse(dummyResp)

        assertEquals(aniyomiEps.size, kototoroEps.size)
        assertEquals(aniyomiEps[0].name, kototoroEps[0].name)
        assertEquals(aniyomiEps[0].episode_number, kototoroEps[0].episode_number)
        assertEquals(aniyomiEps[0].url, kototoroEps[0].url)
    }

    @Test
    fun videoListEquivalence() {
        val dummyResp = mockResponse("https://nekopoi.care/hentai/kanojo-1/ep1")
        val aniyomiVideos = aniyomiAdapter.videoListParse(dummyResp)
        val kototoroVideos = kototoroWrapper.videoListParse(dummyResp)

        assertEquals(aniyomiVideos.size, kototoroVideos.size)
        assertEquals(aniyomiVideos[0].quality, kototoroVideos[0].quality)
        assertEquals(aniyomiVideos[0].videoUrl, kototoroVideos[0].videoUrl)
        assertEquals(aniyomiVideos[0].headers?.get("Referer"), kototoroVideos[0].headers?.get("Referer"))
        assertEquals(aniyomiVideos[0].subtitleTracks.size, kototoroVideos[0].subtitleTracks.size)
        assertEquals(aniyomiVideos[0].subtitleTracks[0].lang, kototoroVideos[0].subtitleTracks[0].lang)
    }

    @Test
    fun pluginRegistryTest() {
        assertEquals(1, KototoroPlugin.getSources().size)
        val found = KototoroPlugin.findSourceById(fakeSource.id)
        assertNotNull(found)
        assertEquals("FakeNekopoi", found?.name)

        val byName = KototoroPlugin.findSourceByName("fakenekopoi")
        assertNotNull(byName)
        assertEquals(fakeSource.id, byName?.id)
    }
}
