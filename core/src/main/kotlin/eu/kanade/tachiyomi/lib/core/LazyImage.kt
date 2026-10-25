package eu.kanade.tachiyomi.lib.core

import org.jsoup.nodes.Element
import java.util.regex.Pattern

object LazyImage {
    private val BG_IMG_PATTERN = Pattern.compile("url\\(['\"]?(.*?)['\"]?\\)", Pattern.CASE_INSENSITIVE)

    fun extractUrl(element: Element?, baseUrl: String = ""): String = extract(element, baseUrl)

    fun extract(element: Element?, baseUrl: String = ""): String {
        if (element == null) return ""

        val candidates = listOf(
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

        for (attr in candidates) {
            val value = element.attr(attr).trim()
            if (value.isNotEmpty() && !isPlaceholder(value)) {
                return UrlUtils.resolve(baseUrl, value)
            }
        }

        // Check srcset
        val srcset = element.attr("srcset").ifEmpty { element.attr("data-srcset") }
        if (srcset.isNotEmpty()) {
            val parsedSrcset = parseSrcset(srcset)
            if (parsedSrcset.isNotEmpty()) {
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

        // Check noscript tag child
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

        // Check child img
        val childImg = element.selectFirst("img")
        if (childImg != null && childImg != element) {
            val childUrl = extract(childImg, baseUrl)
            if (childUrl.isNotEmpty()) {
                return childUrl
            }
        }

        return ""
    }

    private fun isPlaceholder(url: String): Boolean {
        return url.startsWith("data:image/svg+xml") ||
            url.startsWith("data:image/gif") ||
            url.contains("data:image/png;base64,R0lGOD") ||
            url.contains("blank.gif") ||
            url.contains("default-thumb") ||
            url.contains("spacer.png")
    }

    private fun parseSrcset(srcset: String): String {
        // e.g. "https://example.com/img-175.webp 175w, https://example.com/img-500.webp 500w"
        val entries = srcset.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (entries.isEmpty()) return ""
        val last = entries.last().split("\\s+".toRegex())
        return if (last.isNotEmpty()) last[0].trim() else ""
    }
}
