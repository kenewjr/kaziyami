package app.kaziyami.adapter.kototoro

import app.kaziyami.adapter.aniyomi.AniyomiSourceAdapter
import app.kaziyami.engine.DefaultHttpClientProvider
import app.kaziyami.engine.DefaultLogger
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

/**
 * Kototoro plugin descriptor and registry.
 * Enables loading Kaziyami sources directly into Kototoro (v2.3+) via its
 * embedded source-host runtime, AniyomiSourceRuntime, or standalone JAR import.
 */
object KototoroPlugin {

    const val PLUGIN_VERSION = "1.4.1"
    const val PLUGIN_NAME = "Kaziyami Kototoro Suite"

    private val registeredSources = mutableListOf<KaziyamiSource>()

    fun registerSource(source: KaziyamiSource) {
        registeredSources.removeAll { it.id == source.id }
        registeredSources.add(source)
    }

    fun getSources(): List<KaziyamiSource> = registeredSources.toList()

    fun findSourceById(id: Long): KaziyamiSource? = registeredSources.firstOrNull { it.id == id }

    fun findSourceByName(name: String): KaziyamiSource? = registeredSources.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun clear() {
        registeredSources.clear()
    }
}

/**
 * Kototoro-compatible source descriptor wrapping [KaziyamiSource].
 * Implements equivalent methods matching Kototoro's AniyomiSourceRuntime reflection calls:
 * - popular(page)
 * - latest(page)
 * - search(page, query, filters)
 * - details(url)
 * - chapters(url)
 * - pages(url)
 */
open class KototoroSourceWrapper(
    val source: KaziyamiSource,
    val httpProvider: HttpClientProvider = DefaultHttpClientProvider(),
    val preferences: PreferenceStore = InMemoryPreferenceStore(),
    val logger: Logger = DefaultLogger()
) : AniyomiSourceAdapter(source) {

    val sourceId: Long get() = source.id
    val sourceName: String get() = source.name
    val sourceLang: String get() = source.lang

    /**
     * Kototoro Reflection Contract:
     * AniyomiSourceRuntime invokes getPopularAnime, getLatestUpdates, getSearchAnime,
     * getAnimeDetails, getEpisodeList, getVideoList.
     * All are natively implemented by inheriting from AniyomiSourceAdapter!
     */
}
