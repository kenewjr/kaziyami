package eu.kanade.tachiyomi.animeextension.id.nekopoi

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.generic.GenericThemeSource
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.generic.SourceConfig
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URLEncoder

class Nekopoi : GenericThemeSource(
    config = SourceConfig(
        name = "Nekopoi",
        lang = "id",
        baseUrl = "https://nekopoi.care",
        mirrors = listOf("https://nekopoi.app", "https://nekopoi.me"),
        popularPath = "/category/hentai/page/%d/",
        latestPath = "/page/%d/",
        searchPath = "/search/%s/page/%d/",
        selectors = SelectorsConfig(
            item = ".nk-search-results > ul > li, .nk-search-results li, .nk-post-card, article.type-post, .result ul li, .post-item, .eropost",
            itemTitle = "h2, .nk-search-info h2, h2 a, .title a, a[title]",
            itemUrl = "a.nk-search-item, h2 a, .title a, a[href]",
            itemThumbnail = ".nk-search-thumb, img",
            nextPage = "a.next, .pagination a.next, .nav-links a.next, a.next.page-numbers, .next.page-numbers",
            detailsTitle = "h1.entry-title, .title h1, h1",
            detailsThumbnail = ".imgdesc img, .thumb img, .entry-content img",
            detailsDescription = ".nk-search-desc, .entry-content, .desc",
            detailsGenre = ".genre a, .genres a, .tag-links a",
            detailsStatus = ".status, span:contains(Status)",
            detailsAuthor = ".producers, .studio",
            episodeList = ".episodelist ul li, .listeps ul li, .nk-episode-grid a, .tabs-anime a",
            episodeUrl = "a[href]",
            episodeName = ".epl-num, .title, a",
            episodeDate = ".epl-date, .date",
            playerIframes = ".player-embed iframe, #stream iframe, iframe[src]"
        ),
        quirks = QuirksConfig(
            customReferer = "https://nekopoi.care/"
        )
    )
) {

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        checkAgeConfirmed()
        val cleanQuery = query.trim()
        val path = if (cleanQuery.isNotEmpty()) {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            String.format(config.searchPath, encoded, page)
        } else {
            val category = filters.filterIsInstance<NekopoiCategoryFilter>().firstOrNull()?.let {
                when (it.values[it.state]) {
                    "3D Hentai" -> "3d-hentai"
                    "Jav Cosplay" -> "jav-cosplay"
                    else -> "hentai"
                }
            } ?: "hentai"
            "/category/$category/page/$page/"
        }
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val cacheKey = "popular_$page"
        val cached = pageCache.get(cacheKey)
        if (cached != null) return cached

        val p1 = (page - 1) * 2 + 1
        val p2 = p1 + 1

        val page1 = fetchAnimePage(popularAnimeRequest(p1), ::popularAnimeParse)
        if (!page1.hasNextPage || page1.animes.isEmpty()) {
            pageCache.put(cacheKey, page1)
            return page1
        }

        val page2 = runCatching {
            fetchAnimePage(popularAnimeRequest(p2), ::popularAnimeParse)
        }.getOrNull()

        val combined = if (page2 != null && page2.animes.isNotEmpty()) {
            val distinct = (page1.animes + page2.animes).distinctBy { it.url }
            AnimesPage(distinct, page2.hasNextPage)
        } else {
            page1
        }

        pageCache.put(cacheKey, combined)
        return combined
    }

    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> {
        val p1 = (page - 1) * 2 + 1
        val p2 = p1 + 1
        return client.newCall(popularAnimeRequest(p1)).asObservableSuccess().flatMap { res1 ->
            val page1 = popularAnimeParse(res1)
            if (!page1.hasNextPage || page1.animes.isEmpty()) {
                Observable.just(page1)
            } else {
                client.newCall(popularAnimeRequest(p2)).asObservableSuccess().map { res2 ->
                    val page2 = popularAnimeParse(res2)
                    val distinct = (page1.animes + page2.animes).distinctBy { it.url }
                    AnimesPage(distinct, page2.hasNextPage)
                }.onErrorReturn { page1 }
            }
        }
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val cacheKey = "latest_$page"
        val cached = pageCache.get(cacheKey)
        if (cached != null) return cached

        val p1 = (page - 1) * 2 + 1
        val p2 = p1 + 1

        val page1 = fetchAnimePage(latestUpdatesRequest(p1), ::latestUpdatesParse)
        if (!page1.hasNextPage || page1.animes.isEmpty()) {
            pageCache.put(cacheKey, page1)
            return page1
        }

        val page2 = runCatching {
            fetchAnimePage(latestUpdatesRequest(p2), ::latestUpdatesParse)
        }.getOrNull()

        val combined = if (page2 != null && page2.animes.isNotEmpty()) {
            val distinct = (page1.animes + page2.animes).distinctBy { it.url }
            AnimesPage(distinct, page2.hasNextPage)
        } else {
            page1
        }

        pageCache.put(cacheKey, combined)
        return combined
    }

    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> {
        val p1 = (page - 1) * 2 + 1
        val p2 = p1 + 1
        return client.newCall(latestUpdatesRequest(p1)).asObservableSuccess().flatMap { res1 ->
            val page1 = latestUpdatesParse(res1)
            if (!page1.hasNextPage || page1.animes.isEmpty()) {
                Observable.just(page1)
            } else {
                client.newCall(latestUpdatesRequest(p2)).asObservableSuccess().map { res2 ->
                    val page2 = latestUpdatesParse(res2)
                    val distinct = (page1.animes + page2.animes).distinctBy { it.url }
                    AnimesPage(distinct, page2.hasNextPage)
                }.onErrorReturn { page1 }
            }
        }
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val p1 = (page - 1) * 2 + 1
        val p2 = p1 + 1

        val page1 = fetchAnimePage(searchAnimeRequest(p1, query, filters), ::searchAnimeParse)
        if (!page1.hasNextPage || page1.animes.isEmpty()) {
            return page1
        }

        val page2 = runCatching {
            fetchAnimePage(searchAnimeRequest(p2, query, filters), ::searchAnimeParse)
        }.getOrNull()

        return if (page2 != null && page2.animes.isNotEmpty()) {
            val distinct = (page1.animes + page2.animes).distinctBy { it.url }
            AnimesPage(distinct, page2.hasNextPage)
        } else {
            page1
        }
    }

    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        val p1 = (page - 1) * 2 + 1
        val p2 = p1 + 1
        return client.newCall(searchAnimeRequest(p1, query, filters)).asObservableSuccess().flatMap { res1 ->
            val page1 = searchAnimeParse(res1)
            if (!page1.hasNextPage || page1.animes.isEmpty()) {
                Observable.just(page1)
            } else {
                client.newCall(searchAnimeRequest(p2, query, filters)).asObservableSuccess().map { res2 ->
                    val page2 = searchAnimeParse(res2)
                    val distinct = (page1.animes + page2.animes).distinctBy { it.url }
                    AnimesPage(distinct, page2.hasNextPage)
                }.onErrorReturn { page1 }
            }
        }
    }

    private fun fetchAnimePage(request: Request, parser: (Response) -> AnimesPage): AnimesPage {
        return client.newCall(request).execute().use { response ->
            parser(response)
        }
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Nekopoi Filters"),
            NekopoiCategoryFilter()
        )
    }

    class NekopoiCategoryFilter : AnimeFilter.Select<String>(
        "Category",
        arrayOf("All", "Hentai", "3D Hentai", "Jav Cosplay"),
        0
    )
}
