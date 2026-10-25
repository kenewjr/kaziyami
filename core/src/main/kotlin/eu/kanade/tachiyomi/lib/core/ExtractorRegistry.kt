package eu.kanade.tachiyomi.lib.core

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.core.extractors.DirectVideoExtractor
import eu.kanade.tachiyomi.lib.core.extractors.DoodExtractor
import eu.kanade.tachiyomi.lib.core.extractors.FilemoonExtractor
import eu.kanade.tachiyomi.lib.core.extractors.Mp4UploadExtractor
import eu.kanade.tachiyomi.lib.core.extractors.StreamTapeExtractor
import eu.kanade.tachiyomi.lib.core.extractors.StreamWishExtractor
import eu.kanade.tachiyomi.lib.core.extractors.VideoExtractor
import eu.kanade.tachiyomi.lib.core.extractors.VoeExtractor
import okhttp3.OkHttpClient

object ExtractorRegistry {
    private val extractors = mutableListOf<VideoExtractor>(
        DirectVideoExtractor(),
        StreamTapeExtractor(),
        DoodExtractor(),
        VoeExtractor(),
        Mp4UploadExtractor(),
        StreamWishExtractor(),
        FilemoonExtractor()
    )

    fun register(extractor: VideoExtractor) {
        extractors.add(0, extractor)
    }

    fun findExtractor(url: String): VideoExtractor? {
        return extractors.firstOrNull { it.canExtract(url) }
    }

    fun extract(url: String, client: OkHttpClient): List<Video> {
        val extractor = findExtractor(url)
        return extractor?.extract(url, client) ?: emptyList()
    }

    fun extractAll(urls: List<String>, client: OkHttpClient): List<Video> {
        return urls.flatMap { extract(it, client) }
    }
}
