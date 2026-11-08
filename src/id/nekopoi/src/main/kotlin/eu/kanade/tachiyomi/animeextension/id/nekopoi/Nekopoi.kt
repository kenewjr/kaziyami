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
        val genre = filters.filterIsInstance<NekopoiGenreFilter>().firstOrNull()?.toSlug().orEmpty()
        val sort = filters.filterIsInstance<NekopoiSortFilter>().firstOrNull()?.toSlug().orEmpty()
        val category = filters.filterIsInstance<NekopoiCategoryFilter>().firstOrNull()?.let {
            when (it.values[it.state]) {
                "3D Hentai" -> "3d-hentai"
                "Jav Cosplay" -> "jav-cosplay"
                "Hentai" -> "hentai"
                else -> ""
            }
        }.orEmpty()

        val path = when {
            cleanQuery.isNotEmpty() -> {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                String.format(config.searchPath, encoded, page)
            }
            genre.isNotEmpty() -> {
                if (page == 1) "/$genre/" else "/$genre/page/$page/"
            }
            sort.isNotEmpty() -> {
                "/category/$sort/page/$page/"
            }
            category.isNotEmpty() -> {
                "/category/$category/page/$page/"
            }
            else -> {
                val defaultCat = filters.filterIsInstance<NekopoiCategoryFilter>().firstOrNull()?.let {
                    when (it.values[it.state]) {
                        "3D Hentai" -> "3d-hentai"
                        "Jav Cosplay" -> "jav-cosplay"
                        else -> "hentai"
                    }
                } ?: "hentai"
                "/category/$defaultCat/page/$page/"
            }
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
            NekopoiSortFilter(),
            NekopoiCategoryFilter(),
            NekopoiGenreFilter()
        )
    }

    class NekopoiSortFilter : AnimeFilter.Select<String>(
        "Sort By",
        SORTS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = SORTS[state].slug
    }

    class NekopoiCategoryFilter : AnimeFilter.Select<String>(
        "Category",
        arrayOf("All", "Hentai", "3D Hentai", "Jav Cosplay"),
        0
    )

    class NekopoiGenreFilter : AnimeFilter.Select<String>(
        "Genre",
        GENRES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = GENRES[state].slug
    }

    companion object {
        data class Option(val name: String, val slug: String)

        val SORTS = arrayOf(
            Option("Latest Updates", ""),
            Option("Popular Hentai", "hentai"),
            Option("3D Hentai", "3d-hentai"),
            Option("Jav Cosplay", "jav-cosplay")
        )

        val GENRES = arrayOf(
            Option("All", ""),
            Option("Action", "genres/action"),
            Option("Ahegao", "genres/ahegao"),
            Option("Anal", "genres/anal"),
            Option("Armpit", "genres/armpit"),
            Option("BDSM", "genres/bdsm"),
            Option("Big Oppai", "genres/big-oppai"),
            Option("Blackmail", "genres/blackmail"),
            Option("Blonde", "genres/blonde"),
            Option("Blowjob", "genres/blowjob"),
            Option("Bondage", "genres/bondage"),
            Option("Cheating", "genres/cheating"),
            Option("Comedy", "genres/comedy"),
            Option("Creampie", "genres/creampie"),
            Option("Dark Skin", "genres/dark-skin"),
            Option("DILF", "genres/dilf"),
            Option("Elf", "genres/elf"),
            Option("Exhibitionist", "genres/exhibitionist"),
            Option("Fellatio", "genres/fellatio"),
            Option("Female Monster", "genres/female-monster"),
            Option("Femdom", "genres/femdom"),
            Option("Footjob", "genres/footjob"),
            Option("Furry", "genres/furry"),
            Option("Futanari", "genres/futanari"),
            Option("Gangbang", "genres/gangbang"),
            Option("Gore", "genres/gore"),
            Option("Gyaru", "genres/gyaru"),
            Option("Handjob", "genres/handjob"),
            Option("Harem", "genres/harem"),
            Option("Horror", "genres/horror"),
            Option("Housewife", "genres/housewife"),
            Option("Humiliation", "genres/humiliation"),
            Option("Hypnotize", "genres/hypnotize"),
            Option("Incest", "genres/incest"),
            Option("Intercrural", "genres/intercrural"),
            Option("Lactation", "genres/lactation"),
            Option("Maid", "genres/maid"),
            Option("Male Monster", "genres/male-monster"),
            Option("Masturbation", "genres/masturbation"),
            Option("Megane", "genres/megane"),
            Option("MILF", "genres/milf"),
            Option("Mind Control", "genres/mind-control"),
            Option("Monster", "genres/monster"),
            Option("Netorare", "genres/netorare"),
            Option("Nipple Fuck", "genres/nipple-fuck"),
            Option("Nurse", "genres/nurse"),
            Option("Old man", "genres/old-man"),
            Option("Onee-san", "genres/onee-san"),
            Option("Oral", "genres/oral"),
            Option("Paihame", "genres/paihame"),
            Option("Paizuri", "genres/paizuri"),
            Option("Pantyhose", "genres/pantyhose"),
            Option("Pregnant", "genres/pregnant"),
            Option("Prostitution", "genres/prostitution"),
            Option("Romance", "genres/romance"),
            Option("Saimin", "genres/saimin"),
            Option("Schoolgirl", "genres/schoolgirl"),
            Option("Semi-Hentai", "genres/semi-hentai"),
            Option("Sex Toys", "genres/sex-toys"),
            Option("Shibari", "genres/shibari"),
            Option("Stocking", "genres/stocking"),
            Option("Succubus", "genres/succubus"),
            Option("Supranatural", "genres/supranatural"),
            Option("Swimsuit", "genres/swimsuit"),
            Option("Tentacles", "genres/tentacles"),
            Option("Threesome", "genres/threesome"),
            Option("Tsundere", "genres/tsundere"),
            Option("Ugly Bastard", "genres/ugly-bastard"),
            Option("Uncensored", "genres/uncensored"),
            Option("Vanilla", "genres/vanilla"),
            Option("Virgin", "genres/virgin"),
            Option("Yaoi", "genres/yaoi"),
            Option("Yuri", "genres/yuri")
        )
    }
}
