package app.kaziyami.adapter.kototoro

import app.kaziyami.engine.KaziyamiSource
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.ContentParser
import org.skepsun.kototoro.parsers.model.ContentParserSource

/**
 * Registry mapping Kototoro's [ContentParserSource] enum constants
 * to active [KaziyamiSource] implementations.
 */
object KototoroParserRegistry {

    private val sourceFactories = mutableMapOf<ContentParserSource, () -> KaziyamiSource>()

    init {
        registerDefaultSources()
    }

    fun register(source: ContentParserSource, factory: () -> KaziyamiSource) {
        sourceFactories[source] = factory
    }

    fun createParser(source: ContentParserSource, context: ContentLoaderContext): ContentParser {
        val factory = sourceFactories[source]
            ?: throw IllegalArgumentException("No KaziyamiSource registered for Kototoro source: ${source.name}")
        val kaziyamiSource = factory()
        return KototoroParserAdapter(kaziyamiSource, source, context)
    }

    private fun registerDefaultSources() {
        val mappings = mapOf(
            ContentParserSource.NEKOPOI to "eu.kanade.tachiyomi.animeextension.id.nekopoi.Nekopoi",
            ContentParserSource.INDO_HENTAI to "eu.kanade.tachiyomi.animeextension.id.indohentai.IndoHentai",
            ContentParserSource.NONTON_HENTAI to "eu.kanade.tachiyomi.animeextension.id.nontonhentai.NontonHentai",
            ContentParserSource.HENTAIMAMA to "eu.kanade.tachiyomi.animeextension.en.hentaimama.Hentaimama",
            ContentParserSource.JAVSUBBED to "eu.kanade.tachiyomi.animeextension.en.javsubbed.JAVSubbed",
            ContentParserSource.HANIME to "eu.kanade.tachiyomi.animeextension.en.hanime.Hanime"
        )

        for ((sourceEnum, className) in mappings) {
            register(sourceEnum) {
                try {
                    val clazz = Class.forName(className)
                    clazz.getDeclaredConstructor().newInstance() as KaziyamiSource
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to instantiate $className for $sourceEnum", e)
                }
            }
        }
    }
}

