package eu.kanade.tachiyomi.lib.core

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.core.extractors.DirectVideoExtractor
import eu.kanade.tachiyomi.lib.core.extractors.DoodExtractor
import eu.kanade.tachiyomi.lib.core.extractors.FilemoonExtractor
import eu.kanade.tachiyomi.lib.core.extractors.Mp4UploadExtractor
import eu.kanade.tachiyomi.lib.core.extractors.StreamTapeExtractor
import eu.kanade.tachiyomi.lib.core.extractors.StreamWishExtractor
import eu.kanade.tachiyomi.lib.core.extractors.VoeExtractor
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtractorRegistryTest {

    private val client = OkHttpClient()

    @Test
    fun matchesDirectVideoUrls() {
        val extractor = ExtractorRegistry.findExtractor("https://cdn.example.com/video/stream.mp4")
        assertTrue(extractor is DirectVideoExtractor)

        val videos = ExtractorRegistry.extract("https://cdn.example.com/video/stream.m3u8", client)
        assertEquals(1, videos.size)
        assertEquals("https://cdn.example.com/video/stream.m3u8", videos[0].videoUrl)
    }

    @Test
    fun matchesStreamTapeUrls() {
        val extractor = ExtractorRegistry.findExtractor("https://streamtape.com/e/68XxYa8Zb/")
        assertTrue(extractor is StreamTapeExtractor)
    }

    @Test
    fun matchesDoodUrls() {
        val extractor = ExtractorRegistry.findExtractor("https://dood.so/e/abcd1234efgh")
        assertTrue(extractor is DoodExtractor)

        val extractorWf = ExtractorRegistry.findExtractor("https://dood.wf/d/abcd1234efgh")
        assertTrue(extractorWf is DoodExtractor)
    }

    @Test
    fun matchesVoeUrls() {
        val extractor = ExtractorRegistry.findExtractor("https://voe.sx/e/xyz789")
        assertTrue(extractor is VoeExtractor)
    }

    @Test
    fun matchesStreamWishUrls() {
        val extractor = ExtractorRegistry.findExtractor("https://streamwish.to/e/embed123")
        assertTrue(extractor is StreamWishExtractor)
    }

    @Test
    fun matchesFilemoonUrls() {
        val extractor = ExtractorRegistry.findExtractor("https://filemoon.sx/e/moon123")
        assertTrue(extractor is FilemoonExtractor)
    }

    @Test
    fun matchesMp4UploadUrls() {
        val extractor = ExtractorRegistry.findExtractor("https://www.mp4upload.com/embed-456.html")
        assertTrue(extractor is Mp4UploadExtractor)
    }

    @Test
    fun unknownUrlReturnsEmptyInExtract() {
        val videos = ExtractorRegistry.extract("https://unknown-service.xyz/watch/123", client)
        assertTrue(videos.isEmpty())
    }

    @Test
    fun resolveThrowsInformativeExceptionOnUnsupportedHost() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            ExtractorRegistry.resolve("https://unsupported-host.com/embed/123", client = client)
        }
        assertTrue(ex.message!!.contains("unsupported-host.com"))
    }

    @Test
    fun resolveAllThrowsWhenNoHostsMatched() {
        val ex = assertThrows(IllegalStateException::class.java) {
            ExtractorRegistry.resolveAll(listOf("https://unsupported1.com/e/1", "https://unsupported2.com/e/2"), client = client)
        }
        assertTrue(ex.message!!.contains("unsupported1.com"))
        assertTrue(ex.message!!.contains("unsupported2.com"))
    }

    @Test
    fun deduplicateAndSortPrioritizes1080p() {
        val list = listOf(
            Video("https://example.com/360.mp4", "360p", "https://example.com/360.mp4"),
            Video("https://example.com/1080.mp4", "1080p", "https://example.com/1080.mp4"),
            Video("https://example.com/720.mp4", "720p", "https://example.com/720.mp4"),
            Video("https://example.com/1080.mp4", "1080p Duplicate", "https://example.com/1080.mp4")
        )

        val sorted = ExtractorRegistry.deduplicateAndSort(list)
        assertEquals(3, sorted.size, "Must deduplicate by videoUrl")
        assertEquals("1080p", sorted[0].quality)
        assertEquals("720p", sorted[1].quality)
        assertEquals("360p", sorted[2].quality)
    }
}
