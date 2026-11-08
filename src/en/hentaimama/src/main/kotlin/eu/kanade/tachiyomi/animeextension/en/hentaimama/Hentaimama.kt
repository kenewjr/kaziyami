package eu.kanade.tachiyomi.animeextension.en.hentaimama

import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.dooplay.DooPlayTheme
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.net.URLEncoder

class Hentaimama : DooPlayTheme(
    name = "Hentaimama",
    lang = "en",
    baseUrl = "https://hentaimama.io",
    mirrors = listOf("https://hentaimama.tv"),
    popularPath = "/tvshows/page/%d/",
    latestPath = "/episodes/page/%d/",
    searchPath = "/?s=%s&page=%d",
    customSelectors = SelectorsConfig(
        item = "article.item.tvshows, article.item, .items article, article.series-card, .series-card",
        itemTitle = "h3.sc-title a, .sc-title a, .data h3 a, h3 a, h2 a, a[title]",
        itemUrl = "a.sc-poster, .data h3 a, a[href]",
        itemThumbnail = "img",
        nextPage = ".pagination a.next, a.next, .nav-links a.next",
        detailsTitle = "h1, .data h1",
        detailsThumbnail = ".poster img, .thumb img",
        detailsDescription = "meta[property='og:description'], meta[name='description'], .wp-content p, #info .wp-content, .desc p, p.ep-sim-desc",
        episodeList = "#episodes a.dt-se-item, .dt-se-list a.dt-se-item, a.dt-se-item, #episodes ul.episodios li, .episodios li",
        episodeName = ".dt-se-title, b.dt-se-title, .episodiotitle a, .numerando, .dt-se-num",
        episodeDate = ".dt-se-date, span.dt-se-date, .date",
        episodeUrl = "a[href], a",
        playerIframes = ".playex iframe, .source-box iframe, iframe.metaframe, iframe[src]"
    ),
    customQuirks = QuirksConfig(
        usesAjaxPlayer = true,
        customReferer = "https://hentaimama.io/"
    )
) {
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        checkAgeConfirmed()
        var category = "tvshows"
        var genre = ""

        for (filter in filters) {
            when (filter) {
                is HentaimamaCategoryFilter -> category = filter.toSlug()
                is HentaimamaGenreFilter -> genre = filter.toSlug()
                else -> {}
            }
        }

        val cleanQuery = query.trim()
        val path = when {
            cleanQuery.isNotBlank() -> {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                if (page == 1) "/?s=$encoded" else "/?s=$encoded&page=$page"
            }
            genre.isNotEmpty() -> {
                if (page == 1) "/genre/$genre/" else "/genre/$genre/page/$page/"
            }
            category == "episodes" -> {
                if (page == 1) "/episodes/" else "/episodes/page/$page/"
            }
            else -> {
                if (page == 1) "/tvshows/" else "/tvshows/page/$page/"
            }
        }

        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Hentaimama Filters"),
            HentaimamaCategoryFilter(),
            HentaimamaGenreFilter()
        )
    }

    class HentaimamaCategoryFilter : AnimeFilter.Select<String>(
        "Category",
        CATEGORIES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = CATEGORIES[state].slug
    }

    class HentaimamaGenreFilter : AnimeFilter.Select<String>(
        "Genre",
        GENRES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = GENRES[state].slug
    }

    companion object {
        data class Option(val name: String, val slug: String)

        val CATEGORIES = arrayOf(
            Option("TV Shows", "tvshows"),
            Option("Episodes", "episodes")
        )

        val GENRES = arrayOf(
            Option("All", ""),
            Option("3D", "3d"),
            Option("Adventure", "adventure"),
            Option("Ahegao", "ahegao"),
            Option("Anal", "anal"),
            Option("Blowjob", "blowjob"),
            Option("Comedy", "comedy"),
            Option("Condom", "condom"),
            Option("Creampie", "creampie"),
            Option("Cunnilingus", "cunnilingus"),
            Option("Domination", "domination"),
            Option("Drama", "drama"),
            Option("Hand Job", "hand-job"),
            Option("Horny Slut", "horny-slut"),
            Option("Housewife", "housewife"),
            Option("Humiliation", "humiliation"),
            Option("Large Breasts", "large-breasts"),
            Option("Masturbation", "masturbation"),
            Option("MILF", "milf"),
            Option("Public Sex", "public-sex"),
            Option("Romance", "romance"),
            Option("School Girls", "school-girls"),
            Option("Sci-Fi & Fantasy", "sci-fi-fantasy"),
            Option("Supernatural", "supernatural"),
            Option("Tentacles", "tentacles"),
            Option("Uncensored", "uncensored"),
            Option("Vanilla", "vanilla"),
            Option("Virgins", "virgins"),
            Option("X-Ray", "x-ray")
        )
    }
}
