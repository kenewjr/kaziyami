package eu.kanade.tachiyomi.lib.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

object UrlUtils {
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

    fun stripTrackingParams(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "ref", "source", "_hsenc", "_hsmi", "mc_cid", "mc_eid"
        )
        val builder = parsed.newBuilder()
        for (name in parsed.queryParameterNames) {
            if (name.lowercase() in trackingParams) {
                builder.removeAllQueryParameters(name)
            }
        }
        return builder.build().toString()
    }

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
}
