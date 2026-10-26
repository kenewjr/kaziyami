package app.kaziyami.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Opt-in fetcher for remote mirror updates hosted on the official Kaziyami GitHub repository.
 *
 * Privacy & Security Guarantees:
 * - Operates only when explicitly enabled by user preference (default: FALSE).
 * - Uses a standalone isolated OkHttpClient: NO cookies, NO tracking headers, NO app identity headers.
 * - Enforces an allowlist of permitted HTTPS domains to protect against unauthorized mirror hijacking.
 */
object RemoteMirrorFetcher {

    private const val MIRRORS_REPO_URL =
        "https://raw.githubusercontent.com/kenewjr/kaziyami/master/repo/mirrors.json"

    // Only allow verified trusted domain patterns
    private val ALLOWED_DOMAIN_PATTERNS = listOf(
        Regex("^https://(?:[a-zA-Z0-9-]+\\.)?nekopoi\\.(?:care|app|link|site|top|club)$"),
        Regex("^https://(?:[a-zA-Z0-9-]+\\.)?indohentai\\.(?:net|org|com|cc)$"),
        Regex("^https://(?:[a-zA-Z0-9-]+\\.)?nontonhentai\\.(?:net|org|site)$"),
        Regex("^https://(?:[a-zA-Z0-9-]+\\.)?hentaimama\\.(?:io|org|net)$"),
        Regex("^https://(?:[a-zA-Z0-9-]+\\.)?javsubbed\\.(?:net|org|com)$"),
        Regex("^https://(?:[a-zA-Z0-9-]+\\.)?hanime\\.(?:tv|site)$"),
        Regex("^https://(?:[a-zA-Z0-9-]+\\.)?freeanimehentai\\.(?:net|org)$")
    )

    private val isolatedClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Fetches and parses the latest static mirror list from the official repo.
     */
    fun fetchMirrors(
        sourceSlug: String,
        repoUrl: String = MIRRORS_REPO_URL,
        client: OkHttpClient = isolatedClient
    ): List<String> {
        val request = Request.Builder()
            .url(repoUrl)
            .header("Accept", "application/json")
            .build()

        val jsonText = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string() ?: return emptyList()
            }
        } catch (_: IOException) {
            return emptyList()
        }

        return parseAndValidate(jsonText, sourceSlug)
    }

    /**
     * Validates and filters mirror candidates against the strict security allowlist.
     */
    fun parseAndValidate(jsonText: String, sourceSlug: String): List<String> {
        return try {
            val root = Json.parseToJsonElement(jsonText).jsonObject
            val array = root[sourceSlug]?.jsonArray ?: return emptyList()
            val validated = mutableListOf<String>()

            for (elem in array) {
                val candidate = elem.jsonPrimitive.content.trim().removeSuffix("/")
                if (isAllowedUrl(candidate)) {
                    validated.add(candidate)
                }
            }
            validated.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Validates that the URL uses HTTPS and matches approved domain patterns.
     */
    fun isAllowedUrl(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.port != -1 && uri.port != 443) {
            return false
        }
        return ALLOWED_DOMAIN_PATTERNS.any { it.matches(url) }
    }
}
