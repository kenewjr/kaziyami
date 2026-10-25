package eu.kanade.tachiyomi.lib.core

import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Robust image extractor that resolves lazy-loaded thumbnails across varied CMS engines and themes.
 * Inspects standard data attributes, srcset, noscript blocks, and CSS background-image declarations,
 * while rejecting 1x1 transparent GIFs and placeholder images.
 */
object LazyImage {
    private val BG_IMG_PATTERN = Pattern.compile("url\\(['\"]?(.*?)['\"]?\\)", Pattern.CASE_INSENSITIVE)

    private val CANDIDATE_ATTRIBUTES = listOf(
        "data-src",
        "data-lazy-src",
        "data-original",
        "data-lazy",
        "data-url",
        "data-cfsrc",
        "data-src-webp",
        "content",
        "src"
    )

    /**
     * Extracts an absolute thumbnail URL from an HTML element or its descendants.
     */
    fun extractUrl(element: Element?, baseUrl: String = ""): String = extract(element, baseUrl)

    /**
     * Extracts an absolute thumbnail URL from an HTML element.
     */
    fun extract(element: Element?, baseUrl: String = ""): String {
        if (element == null) return ""

        for (attr in CANDIDATE_ATTRIBUTES) {
            val value = element.attr(attr).trim()
            if (value.isNotEmpty() && !isPlaceholder(value)) {
                return UrlUtils.resolve(baseUrl, value)
            }
        }

        // Check srcset or data-srcset (select largest resolution)
        val srcset = element.attr("srcset").ifEmpty { element.attr("data-srcset") }
        if (srcset.isNotEmpty()) {
            val parsedSrcset = parseLargestFromSrcset(srcset)
            if (parsedSrcset.isNotEmpty() && !isPlaceholder(parsedSrcset)) {
                return UrlUtils.resolve(baseUrl, parsedSrcset)
            }
        }

        // Check style="background-image: url(...)"
        val style = element.attr("style")
        if (style.isNotEmpty()) {
            val matcher = BG_IMG_PATTERN.matcher(style)
            if (matcher.find()) {
                val url = matcher.group(1).trim()
                if (url.isNotEmpty() && !isPlaceholder(url)) {
                    return UrlUtils.resolve(baseUrl, url)
                }
            }
        }

        // Check noscript fallback tag
        val noscript = element.selectFirst("noscript")
        if (noscript != null) {
            val noscriptImg = noscript.selectFirst("img")
            if (noscriptImg != null) {
                val noscriptUrl = extract(noscriptImg, baseUrl)
                if (noscriptUrl.isNotEmpty()) {
                    return noscriptUrl
                }
            }
        }

        // Check child img tag if current element is a container (e.g. <a> or <div>)
        val childImg = element.selectFirst("img")
        if (childImg != null && childImg != element) {
            val childUrl = extract(childImg, baseUrl)
            if (childUrl.isNotEmpty()) {
                return childUrl
            }
        }

        return ""
    }

    /**
     * Identifies placeholder images, 1x1 transparent tracking pixels, and empty data URIs.
     */
    fun isPlaceholder(url: String): Boolean {
        val lower = url.lowercase().trim()
        if (lower.isEmpty()) return true
        if (lower.startsWith("data:image/svg+xml")) return true
        if (lower.startsWith("data:image/gif") && (lower.contains("r0lgodlhaqaba") || lower.length < 100)) return true
        if (lower.contains("data:image/png;base64,r0lgod")) return true
        if (lower.endsWith("blank.gif") || lower.endsWith("spacer.png") || lower.endsWith("pixel.gif") || lower.endsWith("1x1.png")) return true
        if (lower.contains("default-thumb") || lower.contains("no-image.jpg") || lower.contains("placeholder")) return true
        return false
    }

    /**
     * Parses srcset values (e.g. "image-300.jpg 300w, image-600.jpg 600w") and selects the candidate with the highest resolution.
     */
    fun parseLargestFromSrcset(srcset: String): String {
        val entries = srcset.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (entries.isEmpty()) return ""

        var bestUrl = ""
        var maxScore = -1.0

        for (entry in entries) {
            val parts = entry.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            val candidateUrl = parts[0]
            val descriptor = if (parts.size > 1) parts[1].lowercase() else ""

            val score = when {
                descriptor.endsWith("w") -> descriptor.removeSuffix("w").toDoubleOrNull() ?: 0.0
                descriptor.endsWith("x") -> (descriptor.removeSuffix("x").toDoubleOrNull() ?: 1.0) * 1000.0
                else -> 0.0
            }

            if (score >= maxScore) {
                maxScore = score
                bestUrl = candidateUrl
            }
        }

        return bestUrl.ifEmpty { entries.last().split("\\s+".toRegex()).firstOrNull() ?: "" }
    }
}
