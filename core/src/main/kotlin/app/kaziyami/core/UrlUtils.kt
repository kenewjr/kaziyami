package app.kaziyami.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

/**
 * URL normalization, resolution, and mirror comparison utilities.
 */
object UrlUtils {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "ref", "source", "_hsenc", "_hsmi", "mc_cid", "mc_eid"
    )

    /**
     * Resolves a relative, protocol-relative (//), or absolute URL against a base URL.
     */
    fun resolve(baseUrl: String, relativeOrAbsoluteUrl: String): String {
        val target = relativeOrAbsoluteUrl.trim()
        if (target.startsWith("//")) {
            val scheme = baseUrl.toHttpUrlOrNull()?.scheme ?: "https"
            return "$scheme:$target"
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target
        }
        return try {
            val baseUri = URI(baseUrl.trim())
            baseUri.resolve(target).toString()
        } catch (_: Exception) {
            val cleanBase = baseUrl.trim().removeSuffix("/")
            val cleanRel = target.removePrefix("/")
            "$cleanBase/$cleanRel"
        }
    }

    /**
     * Normalizes a URL by removing trailing slashes from the path while preserving query parameters and fragments.
     */
    fun removeTrailingSlash(url: String): String {
        val trimmed = url.trim()
        val queryIdx = trimmed.indexOf('?')
        return if (queryIdx >= 0) {
            val path = trimmed.substring(0, queryIdx).removeSuffix("/")
            val query = trimmed.substring(queryIdx)
            path + query
        } else {
            trimmed.removeSuffix("/")
        }
    }

    /**
     * Strips marketing and analytics tracking parameters (utm_*, fbclid, gclid, etc.) from a URL.
     */
    fun stripTrackingParams(url: String): String {
        val isRelative = !url.startsWith("http://") && !url.startsWith("https://")
        val parseTarget = if (isRelative) "http://dummy.local${if (url.startsWith("/")) "" else "/"}$url" else url
        val parsed = parseTarget.toHttpUrlOrNull() ?: return url
        val builder = parsed.newBuilder()
        for (name in parsed.queryParameterNames) {
            if (name.lowercase() in TRACKING_PARAMS) {
                builder.removeAllQueryParameters(name)
            }
        }
        val built = builder.build().toString()
        return if (isRelative) {
            built.removePrefix("http://dummy.local")
        } else {
            built
        }
    }

    /**
     * Replaces the host domain of a URL with a target mirror domain.
     */
    fun unifyMirror(url: String, targetDomain: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val cleanTarget = targetDomain.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
        return parsed.newBuilder()
            .host(cleanTarget)
            .build()
            .toString()
    }

    /**
     * Extracts safe relative path (including query) for storing in SAnime / SEpisode url fields,
     * ensuring domain or mirror changes do not break stored library bookmarks.
     */
    fun getPath(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return if (url.startsWith("/")) url else "/$url"
        val query = parsed.encodedQuery?.let { "?$it" } ?: ""
        val path = if (parsed.encodedPath.isEmpty()) "/" else parsed.encodedPath
        return "$path$query"
    }

    /**
     * Compares two URLs to verify if they refer to the same resource across different mirrors,
     * ignoring mirror domains, trailing slashes, and tracking query parameters.
     */
    fun sameResource(urlA: String, urlB: String): Boolean {
        val pathA = removeTrailingSlash(getPath(stripTrackingParams(urlA)))
        val pathB = removeTrailingSlash(getPath(stripTrackingParams(urlB)))
        return pathA.equals(pathB, ignoreCase = true)
    }
}
