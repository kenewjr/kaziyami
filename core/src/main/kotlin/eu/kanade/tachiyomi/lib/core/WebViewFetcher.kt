package eu.kanade.tachiyomi.lib.core

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

interface WebViewFetcher {
    suspend fun fetchHtml(url: String, headers: Headers? = null): String
}

class DefaultWebViewFetcher(
    private val client: OkHttpClient
) : WebViewFetcher {
    override suspend fun fetchHtml(url: String, headers: Headers?): String {
        val reqBuilder = Request.Builder().url(url)
        if (headers != null) {
            reqBuilder.headers(headers)
        }
        return client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to load page: HTTP ${response.code}")
            }
            response.body?.string() ?: ""
        }
    }
}
