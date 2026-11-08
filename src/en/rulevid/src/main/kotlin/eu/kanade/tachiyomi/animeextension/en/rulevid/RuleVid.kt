package eu.kanade.tachiyomi.animeextension.en.rulevid

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.network.GET
import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.kvs.KvsTheme
import okhttp3.Request
import java.net.URLEncoder

class RuleVid : KvsTheme(
    name = "RuleVid",
    lang = "en",
    baseUrl = "https://rulevid.com",
    mirrors = listOf("https://rulevid.com"),
    popularPath = "/most-popular/%d/",
    latestPath = "/latest-updates/%d/",
    searchPath = "/search/%s/?from_videos=%d",
    customSelectors = SelectorsConfig(
        item = ".video-card, .item",
        itemTitle = "a.video-card-thumb-wrap img[alt], a[title], img[alt], .title",
        itemThumbnail = "img.video-card-thumb, img",
        itemUrl = "a.video-card-thumb-wrap, a[href*='/video/']",
        nextPage = ".pagination .active + a, a[data-parameters*='from']",
        detailsTitle = "h1, .headline h1",
        detailsThumbnail = ".video-card-thumb, img",
        detailsDescription = ".video-details, .description, .item-description",
        detailsGenre = "a[href*='/tags/'], a[href*='/categories/']"
    ),
    customQuirks = QuirksConfig(
        customReferer = "https://rulevid.com/"
    )
) {

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        checkAgeConfirmed()
        val cleanQuery = query.trim()
        val genre = filters.filterIsInstance<RuleVidGenreFilter>().firstOrNull()?.toSlug().orEmpty()
        val sort = filters.filterIsInstance<RuleVidSortFilter>().firstOrNull()?.toSlug().orEmpty()

        val path = when {
            cleanQuery.isNotBlank() -> {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                if (page > 1) "/search/$encoded/?from_videos=$page" else "/search/$encoded/"
            }
            genre.isNotEmpty() -> {
                if (page == 1) "/tags/$genre/" else "/tags/$genre/$page/"
            }
            sort.isNotEmpty() -> {
                if (page == 1) "/$sort/" else "/$sort/$page/"
            }
            else -> {
                if (page == 1) "/most-popular/" else "/most-popular/$page/"
            }
        }
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("RuleVid Filters"),
            RuleVidSortFilter(),
            RuleVidGenreFilter()
        )
    }

    class RuleVidSortFilter : AnimeFilter.Select<String>(
        "Sort By",
        SORTS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = SORTS[state].slug
    }

    class RuleVidGenreFilter : AnimeFilter.Select<String>(
        "Genre / Tag",
        GENRES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = GENRES[state].slug
    }

    companion object {
        data class Option(val name: String, val slug: String)

        val SORTS = arrayOf(
            Option("Most Popular", "most-popular"),
            Option("Latest Updates", "latest-updates"),
            Option("Top Rated", "top-rated"),
            Option("Most Viewed", "most-viewed")
        )

        val GENRES = arrayOf(
            Option("All", ""),
            Option("3D", "3d"),
            Option("Anal", "anal"),
            Option("Animation", "animation"),
            Option("BDSM", "bdsm"),
            Option("Big Ass", "large-ass"),
            Option("Big Cock", "big-penis"),
            Option("Blender", "blender"),
            Option("Blowjob", "blowjob"),
            Option("Bondage", "bondage"),
            Option("Breeding", "breeding"),
            Option("Compilation", "compilation"),
            Option("Cosplay", "cosplay"),
            Option("Creampie", "creampie"),
            Option("Cunnilingus", "cunnilingus"),
            Option("Dark Skin", "dark-skin"),
            Option("Deepthroat", "deepthroat"),
            Option("Doggystyle", "doggystyle-position"),
            Option("Elf", "elf"),
            Option("Facial", "facial"),
            Option("Femdom", "femdom"),
            Option("Footjob", "footjob"),
            Option("Furry", "furry"),
            Option("Futanari", "futanari"),
            Option("Gangbang", "gangbang"),
            Option("Genshin Impact", "genshin-impact"),
            Option("Glasses", "glasses"),
            Option("Handjob", "handjob"),
            Option("Harem", "harem"),
            Option("Hentai", "hentai"),
            Option("Lactation", "lactation"),
            Option("Lesbian", "lesbian"),
            Option("Maid", "maid"),
            Option("Masturbation", "masturbation"),
            Option("MILF", "milf"),
            Option("MMD", "mmd"),
            Option("Monster", "monster"),
            Option("Netorare (NTR)", "ntr"),
            Option("Nurse", "nurse"),
            Option("Orgy", "orgy"),
            Option("Overwatch", "overwatch"),
            Option("Paizuri", "titty-fuck"),
            Option("POV", "pov"),
            Option("Pregnant", "pregnant"),
            Option("Public", "public"),
            Option("Rimjob", "rimjob"),
            Option("Schoolgirl", "schoolgirl"),
            Option("SFM", "sfm"),
            Option("Stockings", "stockings"),
            Option("Succubus", "succubus"),
            Option("Swimsuit", "swimsuit"),
            Option("Tentacles", "tentacles"),
            Option("Threesome", "threesome"),
            Option("Toys", "sex-toys"),
            Option("Uncensored", "uncensored"),
            Option("Vanilla", "vanilla"),
            Option("Virgin", "virgin"),
            Option("X-Ray", "x-ray"),
            Option("Yuri", "yuri")
        )
    }
}
