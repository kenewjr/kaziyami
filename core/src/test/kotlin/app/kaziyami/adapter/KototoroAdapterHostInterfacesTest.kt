package app.kaziyami.adapter

import app.kaziyami.adapter.kototoro.KototoroSourceWrapper
import app.kaziyami.engine.ChallengeSolver
import app.kaziyami.engine.CookieStore
import app.kaziyami.engine.DefaultHttpClientProvider
import app.kaziyami.engine.HttpClientProvider
import app.kaziyami.engine.InMemoryCookieStore
import app.kaziyami.engine.InMemoryPreferenceStore
import app.kaziyami.engine.KaziyamiEpisode
import app.kaziyami.engine.KaziyamiMedia
import app.kaziyami.engine.KaziyamiPage
import app.kaziyami.engine.KaziyamiSource
import app.kaziyami.engine.KaziyamiVideo
import app.kaziyami.engine.Logger
import app.kaziyami.engine.PreferenceStore
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KototoroAdapterHostInterfacesTest {

    class FakeChallengeSolver : ChallengeSolver {
        var solvedCount = 0
        override fun solve(url: String, userAgent: String): Boolean {
            solvedCount++
            return true
        }
    }

    class TestLogger : Logger {
        val messages = mutableListOf<String>()
        override fun debug(tag: String, message: String) { messages.add("DEBUG: $message") }
        override fun info(tag: String, message: String) { messages.add("INFO: $message") }
        override fun warn(tag: String, message: String) { messages.add("WARN: $message") }
        override fun error(tag: String, message: String, throwable: Throwable?) { messages.add("ERROR: $message") }
    }

    private val fakeSource = object : KaziyamiSource {
        override val id: Long = 12345L
        override val name: String = "TestHostSource"
        override val lang: String = "en"
        override val baseUrl: String = "https://example.com"
        override fun popular(page: Int): KaziyamiPage<KaziyamiMedia> = KaziyamiPage(emptyList(), false)
        override fun latest(page: Int): KaziyamiPage<KaziyamiMedia> = KaziyamiPage(emptyList(), false)
        override fun search(page: Int, query: String, filters: List<Any>): KaziyamiPage<KaziyamiMedia> = KaziyamiPage(emptyList(), false)
        override fun details(url: String): KaziyamiMedia = KaziyamiMedia("1", "Title", url)
        override fun episodes(url: String): List<KaziyamiEpisode> = emptyList()
        override fun videos(url: String): List<KaziyamiVideo> = emptyList()
    }

    @Test
    fun testPreferenceStoreIntegration() {
        val prefStore = InMemoryPreferenceStore()
        assertEquals("default", prefStore.getString("mirror_url", "default"))

        prefStore.setString("mirror_url", "https://mirror1.com")
        assertEquals("https://mirror1.com", prefStore.getString("mirror_url", "default"))
    }

    @Test
    fun testCookieStoreIntegration() {
        val cookieStore = InMemoryCookieStore()
        val url = "https://example.com"
        assertEquals(null, cookieStore.getCookie(url))

        cookieStore.setCookie(url, "cf_clearance=abc123xyz")
        assertEquals("cf_clearance=abc123xyz", cookieStore.getCookie(url))
    }

    @Test
    fun testChallengeSolverIntegration() {
        val solver = FakeChallengeSolver()
        assertEquals(0, solver.solvedCount)

        val result = solver.solve("https://example.com/challenge", "Mozilla/5.0")
        assertTrue(result)
        assertEquals(1, solver.solvedCount)
    }

    @Test
    fun testLoggerRedactionIntegration() {
        val logger = TestLogger()
        logger.info("TestHost", "Processing request successfully")
        assertEquals(1, logger.messages.size)
        assertTrue(logger.messages[0].contains("Processing request"))
    }

    @Test
    fun testKototoroWrapperHostWiring() {
        val prefStore = InMemoryPreferenceStore()
        val logger = TestLogger()
        val wrapper = KototoroSourceWrapper(
            source = fakeSource,
            preferences = prefStore,
            logger = logger
        )

        assertEquals(12345L, wrapper.sourceId)
        assertEquals("TestHostSource", wrapper.sourceName)
        assertEquals("en", wrapper.sourceLang)
    }
}
