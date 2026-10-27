package app.kaziyami.core

import eu.kanade.tachiyomi.animesource.model.Video
import app.kaziyami.extractors.DirectVideoExtractor
import app.kaziyami.extractors.DoodExtractor
import app.kaziyami.extractors.FilemoonExtractor
import app.kaziyami.extractors.Mp4UploadExtractor
import app.kaziyami.extractors.StreamTapeExtractor
import app.kaziyami.extractors.StreamWishExtractor
import app.kaziyami.extractors.VideoExtractor
import app.kaziyami.extractors.VoeExtractor
import okhttp3.OkHttpClient
import java.net.URI

/**
 * Central registry that routes embed URLs to specialized video host extractors.
 * Handles deduplication, quality sorting, and throws informative exceptions when unsupported hosts are encountered.
 */
object ExtractorRegistry {

    private val extractors = mutableListOf<VideoExtractor>(
        DirectVideoExtractor(),
        StreamTapeExtractor(),
        DoodExtractor(),
        VoeExtractor(),
        Mp4UploadExtractor(),
        StreamWishExtractor(),
        FilemoonExtractor(),
        app.kaziyami.extractors.StreamPoiExtractor()
    )

    /**
     * Registers a custom video host extractor, placing it at the front of the resolution priority list.
     */
    fun register(extractor: VideoExtractor) {
        synchronized(extractors) {
            extractors.add(0, extractor)
        }
    }

    /**
     * Finds a compatible extractor for the given embed URL.
     */
    fun findExtractor(url: String): VideoExtractor? {
        synchronized(extractors) {
            return extractors.firstOrNull { it.canExtract(url) }
        }
    }

    /**
     * Resolves an embed URL into playable Video instances with quality labelling.
     * Throws an IllegalArgumentException if the embed host has no matching extractor.
     */
    fun resolve(embedUrl: String, referer: String = "", client: OkHttpClient = OkHttpClient()): List<Video> {
        val extractor = findExtractor(embedUrl)
            ?: throw IllegalArgumentException("Unsupported video host for embed URL: '$embedUrl'. No registered extractor matched this host.")

        val extracted = extractor.extract(embedUrl, referer, client)
        if (extracted.isEmpty()) {
            throw IllegalStateException("Extractor '${extractor.name}' found for '$embedUrl' but returned no playable streams.")
        }
        return deduplicateAndSort(extracted)
    }

    /**
     * Resolves multiple candidate embed URLs. If no streams can be extracted from any host,
     * throws an IllegalStateException listing all failed or unsupported hosts.
     */
    fun resolveAll(urls: List<String>, referer: String = "", client: OkHttpClient = OkHttpClient()): List<Video> {
        val results = mutableListOf<Video>()
        val unhandled = mutableListOf<String>()

        for (url in urls) {
            val extractor = findExtractor(url)
            if (extractor == null) {
                val host = try { URI(url).host ?: url } catch (_: Exception) { url }
                unhandled.add(host)
                continue
            }
            try {
                val videos = extractor.extract(url, referer, client)
                results.addAll(videos)
            } catch (e: Exception) {
                val host = try { URI(url).host ?: url } catch (_: Exception) { url }
                unhandled.add("$host (${e.message ?: "extraction failed"})")
            }
        }

        if (results.isEmpty() && urls.isNotEmpty()) {
            throw IllegalStateException(
                "Unable to extract any playable streams from embed candidates [${urls.size}]. " +
                    "Unsupported or failed hosts: ${unhandled.joinToString(", ")}"
            )
        }

        return deduplicateAndSort(results)
    }

    /**
     * Sequentially tries candidate embed URLs, prioritizing the user's preferred host and
     * stopping at the first working stream. Only probes subsequent hosts on failure.
     */
    fun resolveSequential(
        urls: List<String>,
        preferredHost: String? = null,
        referer: String = "",
        client: OkHttpClient = OkHttpClient()
    ): List<Video> {
        if (urls.isEmpty()) return emptyList()

        val orderedUrls = if (!preferredHost.isNullOrBlank()) {
            urls.sortedByDescending { it.contains(preferredHost, ignoreCase = true) }
        } else {
            urls
        }

        val unhandled = mutableListOf<String>()
        for (url in orderedUrls) {
            val extractor = findExtractor(url)
            if (extractor == null) {
                val host = try { URI(url).host ?: url } catch (_: Exception) { url }
                unhandled.add(host)
                continue
            }
            try {
                val videos = extractor.extract(url, referer, client)
                if (videos.isNotEmpty()) {
                    // Stop at first working extractor to reduce request burst
                    return deduplicateAndSort(videos, preferredHost = preferredHost)
                }
            } catch (e: Exception) {
                val host = try { URI(url).host ?: url } catch (_: Exception) { url }
                unhandled.add("$host (${e.message ?: "failed"})")
            }
        }

        if (unhandled.isNotEmpty() && urls.isNotEmpty()) {
            throw IllegalStateException(
                "Sequential extraction failed across all candidates [${urls.size}]. Failed/unhandled: ${unhandled.joinToString(", ")}"
            )
        }
        return emptyList()
    }

    /**
     * Extracts streams without throwing on missing extractor, returning empty list if unhandled.
     */
    fun extract(url: String, client: OkHttpClient): List<Video> {
        val extractor = findExtractor(url) ?: return emptyList()
        return try {
            deduplicateAndSort(extractor.extract(url, client))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Extracts streams across multiple URLs without throwing on individual extractor absence.
     */
    fun extractAll(urls: List<String>, client: OkHttpClient): List<Video> {
        val all = urls.flatMap { extract(it, client) }
        return deduplicateAndSort(all)
    }

    /**
     * Deduplicates video stream entries by URL and sorts candidates with highest resolution first.
     */
    fun deduplicateAndSort(
        videos: List<Video>,
        preferredQuality: String = "1080p",
        preferredHost: String? = null
    ): List<Video> {
        val unique = videos.distinctBy { it.videoUrl }
        return unique.sortedWith(
            compareByDescending<Video> { video ->
                var score = 0
                val q = video.quality.lowercase()
                if (preferredHost != null && q.contains(preferredHost.lowercase())) score += 1000
                if (q.contains(preferredQuality.lowercase())) score += 500
                when {
                    q.contains("1080p") || q.contains("1080") -> score += 100
                    q.contains("720p") || q.contains("720") -> score += 70
                    q.contains("480p") || q.contains("480") -> score += 40
                    q.contains("360p") || q.contains("360") -> score += 20
                }
                score
            }
        )
    }
}
