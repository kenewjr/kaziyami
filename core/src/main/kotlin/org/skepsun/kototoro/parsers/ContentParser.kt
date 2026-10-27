package org.skepsun.kototoro.parsers

import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.config.ContentSourceConfig
import org.skepsun.kototoro.parsers.config.SimpleContentSourceConfig
import org.skepsun.kototoro.parsers.model.*

abstract class ContentLoaderContext {
    abstract val httpClient: OkHttpClient
    abstract val cookieJar: CookieJar
    open fun getConfig(source: ContentSource): ContentSourceConfig = SimpleContentSourceConfig()
    open fun requestBrowserAction(parser: ContentParser, url: String): Nothing {
        throw UnsupportedOperationException("Browser is not available")
    }
}

interface ContentParser {
    val source: ContentSource
    val availableSortOrders: Set<SortOrder>
    val filterCapabilities: ContentListFilterCapabilities
    val config: ContentSourceConfig
    val configKeyDomain: ConfigKey.Domain
    val domain: String

    suspend fun getList(offset: Int, order: SortOrder, filter: ContentListFilter): List<Content>
    suspend fun getDetails(manga: Content): Content
    suspend fun getPages(chapter: ContentChapter): List<ContentPage>
    suspend fun getPageUrl(page: ContentPage): String
    suspend fun getFilterOptions(): ContentListFilterOptions
    suspend fun getFavicons(): Favicons
    fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>)
    fun getRequestHeaders(): Headers
}

