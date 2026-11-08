package eu.kanade.tachiyomi.animeextension.id.nontonhentai

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.animestream.AnimeStreamTheme
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URLEncoder

class NontonHentai : AnimeStreamTheme(
    name = "NontonHentai",
    lang = "id",
    baseUrl = "https://nontonhentai.net",
    mirrors = listOf("https://nontonhentai.net"),
    popularPath = "/page/%d/",
    latestPath = "/page/%d/",
    searchPath = "/page/%d/?s=%s",
    customSelectors = SelectorsConfig(
        item = "div.listupd article.bs, .animposx, .listupd article, div.bsx, article.bs",
        itemTitle = ".title, h4, h2, a[title]",
        itemThumbnail = "img",
        nextPage = ".pagination .next, a.next, .hpage .r, .pagination a.r, a.r, a:contains(Selanjutnya)",
        detailsTitle = "h1.entry-title, .infox h1, h1",
        detailsThumbnail = ".thumb img, .poster img",
        detailsDescription = ".entry-content p, .synopsis, .desc, .sinopsis",
        episodeList = ".eplister ul li, .episodelist ul li",
        playerIframes = ".player-embed iframe, iframe[src]"
    ),
    customQuirks = QuirksConfig(
        customReferer = "https://nontonhentai.net/"
    )
) {

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        checkAgeConfirmed()
        val cleanQuery = query.trim()
        val genre = filters.filterIsInstance<NontonHentaiGenreFilter>().firstOrNull()?.toSlug().orEmpty()
        val order = filters.filterIsInstance<NontonHentaiSortFilter>().firstOrNull()?.toSlug().orEmpty()
        val status = filters.filterIsInstance<NontonHentaiStatusFilter>().firstOrNull()?.toSlug().orEmpty()

        val path = if (cleanQuery.isNotEmpty()) {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            if (genre.isNotEmpty() || order.isNotEmpty() || status.isNotEmpty()) {
                val params = mutableListOf<String>()
                params.add("title=$encoded")
                if (order.isNotEmpty()) params.add("order=$order")
                if (status.isNotEmpty()) params.add("status=$status")
                if (genre.isNotEmpty()) params.add("genre%5B%5D=$genre")
                params.add("page=$page")
                "/anime/?" + params.joinToString("&")
            } else {
                if (page > 1) "/page/$page/?s=$encoded" else "/?s=$encoded"
            }
        } else {
            val params = mutableListOf<String>()
            if (order.isNotEmpty()) params.add("order=$order")
            if (status.isNotEmpty()) params.add("status=$status")
            if (genre.isNotEmpty()) params.add("genre%5B%5D=$genre")
            params.add("page=$page")

            "/anime/?" + params.joinToString("&")
        }
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return super.getSearchAnime(page, query, filters)
        }

        val p1 = (page - 1) * 2 + 1
        val p2 = p1 + 1

        val page1 = fetchAnimePage(searchAnimeRequest(p1, query, filters), ::searchAnimeParse)
        if (!page1.hasNextPage || page1.animes.size >= 20 || page1.animes.isEmpty()) {
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
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return super.fetchSearchAnime(page, query, filters)
        }

        val p1 = (page - 1) * 2 + 1
        val p2 = p1 + 1
        return client.newCall(searchAnimeRequest(p1, query, filters)).asObservableSuccess().flatMap { res1 ->
            val page1 = searchAnimeParse(res1)
            if (!page1.hasNextPage || page1.animes.size >= 20 || page1.animes.isEmpty()) {
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
            AnimeFilter.Header("NontonHentai Filters"),
            NontonHentaiSortFilter(),
            NontonHentaiStatusFilter(),
            NontonHentaiGenreFilter()
        )
    }

    data class FilterOption(val name: String, val slug: String)

    class NontonHentaiSortFilter : AnimeFilter.Select<String>(
        "Sort By",
        SORTS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = SORTS[state].slug
    }

    class NontonHentaiStatusFilter : AnimeFilter.Select<String>(
        "Status",
        STATUSES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = STATUSES[state].slug
    }

    class NontonHentaiGenreFilter : AnimeFilter.Select<String>(
        "Genre",
        GENRES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = GENRES[state].slug
    }

    companion object {
        data class Genre(val name: String, val slug: String)

        val SORTS = arrayOf(
            FilterOption("Default", ""),
            FilterOption("Popular", "popular"),
            FilterOption("Latest Update", "update"),
            FilterOption("Latest Added", "latest"),
            FilterOption("Title (A-Z)", "title"),
            FilterOption("Rating", "rating")
        )

        val STATUSES = arrayOf(
            FilterOption("All", ""),
            FilterOption("Ongoing", "ongoing"),
            FilterOption("Completed", "completed")
        )

        val GENRES = arrayOf(
            Genre("All", ""),
            Genre("2D", "2d"),
            Genre("3D", "3d"),
            Genre("3P", "3p"),
            Genre("4HR+Other Asian", "4hrother-asian"),
            Genre("4P", "4p"),
            Genre("Acme · Orgasm", "acme-%c2%b7-orgasm"),
            Genre("Acme Orgasm", "acme-orgasm"),
            Genre("Action", "action"),
            Genre("Adult Cast", "adult-cast"),
            Genre("Adventure", "adventure"),
            Genre("Affair", "affair"),
            Genre("Ahegao", "ahegao"),
            Genre("Akita Shoten", "akita-shoten"),
            Genre("Amateur", "amateur"),
            Genre("Amatir", "amatir"),
            Genre("Anal", "anal"),
            Genre("Anal Sex", "anal-sex"),
            Genre("Android", "android"),
            Genre("Angel", "angel"),
            Genre("Anthropomorphic", "anthropomorphic"),
            Genre("Armpit", "armpit"),
            Genre("ASMR", "asmr"),
            Genre("AV Actress", "av-actress"),
            Genre("Avant Garde", "avant-garde"),
            Genre("Babe", "babe"),
            Genre("Back", "back"),
            Genre("BBW", "bbw"),
            Genre("BDSM", "bdsm"),
            Genre("Beautiful Girl", "beautiful-girl"),
            Genre("Bestiallity", "bestiallity"),
            Genre("Big Ass", "big-ass"),
            Genre("Big Boobs", "big-boobs"),
            Genre("Big Breasts", "big-breasts"),
            Genre("Big Butt", "big-butt"),
            Genre("Big Oppai", "big-oppai"),
            Genre("Big Tits", "big-tits"),
            Genre("BigOppai", "bigoppai"),
            Genre("Bigtis", "bigtis"),
            Genre("Bigtits", "bigtits"),
            Genre("Blackmail", "blackmail"),
            Genre("Blow", "blow"),
            Genre("Blow Job", "blow-job"),
            Genre("Blowjob", "blowjob"),
            Genre("Blue Archive", "blue-archive"),
            Genre("Bokep Indo", "bokep-indo"),
            Genre("Bondage", "bondage"),
            Genre("Boys Love", "boys-love"),
            Genre("Bride", "bride"),
            Genre("Brunette", "brunette"),
            Genre("Bukakke", "bukakke"),
            Genre("Bukkake", "bukkake"),
            Genre("Busty fetish", "busty-fetish"),
            Genre("Butt", "butt"),
            Genre("Censored", "censored"),
            Genre("Cheating", "cheating"),
            Genre("Chikan", "chikan"),
            Genre("Comedy", "comedy"),
            Genre("Commedy", "commedy"),
            Genre("Cosplay", "cosplay"),
            Genre("Cowgirl", "cowgirl"),
            Genre("Cowirl", "cowirl"),
            Genre("Cream Pie", "cream-pie"),
            Genre("Creampie", "creampie"),
            Genre("Crossdressing", "crossdressing"),
            Genre("Cuckold", "cuckold"),
            Genre("Cumshot.", "cumshot"),
            Genre("Danmachi", "danmachi"),
            Genre("Dark Skin", "dark-skin"),
            Genre("Debut Production", "debut-production"),
            Genre("Decensored", "decensored"),
            Genre("Deep Throating", "deep-throating"),
            Genre("Demon", "demon"),
            Genre("Demons", "demons"),
            Genre("Detective", "detective"),
            Genre("Digital Mosaic", "digital-mosaic"),
            Genre("DILF", "dilf"),
            Genre("Dirty Words", "dirty-words"),
            Genre("Documentary", "documentary"),
            Genre("Doggy", "doggy"),
            Genre("Doggy style", "doggy-style"),
            Genre("Doggystyle", "doggystyle"),
            Genre("Domination", "domination"),
            Genre("Double Penetration", "double-penetration"),
            Genre("Drag", "drag"),
            Genre("Drama", "drama"),
            Genre("Drug", "drug"),
            Genre("Ecchi", "ecchi"),
            Genre("Elf", "elf"),
            Genre("Embarrassment", "embarrassment"),
            Genre("Entertainer", "entertainer"),
            Genre("Erotica", "erotica"),
            Genre("Escolar", "escolar"),
            Genre("Exhibition", "exhibition"),
            Genre("Exhibitionist", "exhibitionist"),
            Genre("Facials", "facials"),
            Genre("Fantasy", "fantasy"),
            Genre("Fellatio", "fellatio"),
            Genre("Female Monster", "female-monster"),
            Genre("Female teacher", "female-teacher"),
            Genre("Femdom", "femdom"),
            Genre("fetish", "fetish"),
            Genre("Filmed", "filmed"),
            Genre("Flat", "flat"),
            Genre("Footjob", "footjob"),
            Genre("Forced", "forced"),
            Genre("Frontier Works", "frontier-works"),
            Genre("Full Yuri", "full-yuri"),
            Genre("Futanari", "futanari"),
            Genre("Futanari Gangbang", "futanari-gangbang"),
            Genre("Gangbang", "gangbang"),
            Genre("Genshin Impact", "genshin-impact"),
            Genre("Ghost", "ghost"),
            Genre("Girls Love", "girls-love"),
            Genre("Glasses", "glasses"),
            Genre("Grimosa", "grimosa"),
            Genre("Group", "group"),
            Genre("Gyaru", "gyaru"),
            Genre("Hand Job", "hand-job"),
            Genre("Handjob", "handjob"),
            Genre("Hardcore", "hardcore"),
            Genre("Harem", "harem"),
            Genre("hegao", "hegao"),
            Genre("Hentai", "hentai"),
            Genre("High sexual content", "high-sexual-content"),
            Genre("High Vision", "high-vision"),
            Genre("Historical", "historical"),
            Genre("Hoksi", "hoksi"),
            Genre("Hoods Entertainment", "hoods-entertainment"),
            Genre("Horror", "horror"),
            Genre("Housewife", "housewife"),
            Genre("Huge Butt", "huge-butt"),
            Genre("Huge Dick", "huge-dick"),
            Genre("Humilation", "humilation"),
            Genre("Humiliation", "humiliation"),
            Genre("Humillation", "humillation"),
            Genre("Hypnostis", "hypnostis"),
            Genre("Hypnotis", "hypnotis"),
            Genre("Hypnotize", "hypnotize"),
            Genre("Idol", "idol"),
            Genre("ig Oppai", "ig-oppai"),
            Genre("Image Video", "image-video"),
            Genre("Incest", "incest"),
            Genre("Infidelity", "infidelity"),
            Genre("Inflation", "inflation"),
            Genre("Intercrural", "intercrural"),
            Genre("Isekai", "isekai"),
            Genre("JAV", "jav"),
            Genre("JK", "jk"),
            Genre("Josei", "josei"),
            Genre("Kiss", "kiss"),
            Genre("Kiss Kiss", "kiss-kiss"),
            Genre("L2D", "l2d"),
            Genre("Lactation", "lactation"),
            Genre("Lady", "lady"),
            Genre("LadyLove", "ladylove"),
            Genre("Licking", "licking"),
            Genre("Love", "love"),
            Genre("Love Polygon", "love-polygon"),
            Genre("Magic", "magic"),
            Genre("Magical Sex Shift", "magical-sex-shift"),
            Genre("Mahou Shoujo", "mahou-shoujo"),
            Genre("Maid", "maid"),
            Genre("Male Monster", "male-monster"),
            Genre("Married Woman", "married-woman"),
            Genre("Martial Arts", "martial-arts"),
            Genre("Masochist", "masochist"),
            Genre("Masturbation", "masturbation"),
            Genre("Mature Woman", "mature-woman"),
            Genre("Mecha", "mecha"),
            Genre("Megane", "megane"),
            Genre("MILF", "milf"),
            Genre("Military", "military"),
            Genre("Milky Animation Label", "milky-animation-label"),
            Genre("Mind Control", "mind-control"),
            Genre("Mini Skirt.", "mini-skirt"),
            Genre("Missionary", "missionary"),
            Genre("Mizugi", "mizugi"),
            Genre("Mizugi / Swimsuit", "mizugi-swimsuit"),
            Genre("Monogatari Series", "monogatari-series"),
            Genre("Monster", "monster"),
            Genre("Monster Girl", "monster-girl"),
            Genre("Mother", "mother"),
            Genre("MS Pictures", "ms-pictures"),
            Genre("Music", "music"),
            Genre("Mystery", "mystery"),
            Genre("Mythology", "mythology"),
            Genre("Nakadashi", "nakadashi"),
            Genre("Nasty", "nasty"),
            Genre("Nasty / Hard", "nasty-hard"),
            Genre("Netorare", "netorare"),
            Genre("Nipple Fuck", "nipple-fuck"),
            Genre("No Bra", "no-bra"),
            Genre("NTR", "ntr"),
            Genre("Nu", "nu"),
            Genre("Nurse", "nurse"),
            Genre("Office", "office"),
            Genre("Office Girl", "office-girl"),
            Genre("OL", "ol"),
            Genre("Old man", "old-man"),
            Genre("Older sister", "older-sister"),
            Genre("Onee-chan", "onee-chan"),
            Genre("Onee-san", "onee-san"),
            Genre("Oppai", "oppai"),
            Genre("Oral", "oral"),
            Genre("Oral sex", "oral-sex"),
            Genre("Orgasm", "orgasm"),
            Genre("Orgia", "orgia"),
            Genre("Orgy", "orgy"),
            Genre("Original Collab", "original-collab"),
            Genre("Original Collaboration", "original-collaboration"),
            Genre("Other Fetishes", "other-fetishes"),
            Genre("Paihame", "paihame"),
            Genre("Paizuri", "paizuri"),
            Genre("Pantyhose", "pantyhose"),
            Genre("Parody", "parody"),
            Genre("Pee", "pee"),
            Genre("Pettanko", "pettanko"),
            Genre("Photograph", "photograph"),
            Genre("POV", "pov"),
            Genre("Pregnant", "pregnant"),
            Genre("Promiscuity", "promiscuity"),
            Genre("Prostitution", "prostitution"),
            Genre("Psychological", "psychological"),
            Genre("Reincarnation", "reincarnation"),
            Genre("Rimjob", "rimjob"),
            Genre("Risky Mosaic", "risky-mosaic"),
            Genre("Role Play.", "role-play"),
            Genre("Romance", "romance"),
            Genre("Rough Sex.", "rough-sex"),
            Genre("Sadist", "sadist"),
            Genre("Sailor Uniform.", "sailor-uniform"),
            Genre("Saimin", "saimin"),
            Genre("Schoogirl", "schoogirl"),
            Genre("School", "school"),
            Genre("School Girls", "school-girls"),
            Genre("Schoolgirl", "schoolgirl"),
            Genre("Sci-Fi", "sci-fi"),
            Genre("Seinen", "seinen"),
            Genre("Semi-Hentai", "semi-hentai"),
            Genre("Sensei", "sensei"),
            Genre("Sex Toys", "sex-toys"),
            Genre("SexSlave", "sexslave"),
            Genre("Sexy", "sexy"),
            Genre("Shame", "shame"),
            Genre("Shaved", "shaved"),
            Genre("Shibari", "shibari"),
            Genre("Shibari / BDSM", "shibari-bdsm"),
            Genre("Shounen", "shounen"),
            Genre("Single Work", "single-work"),
            Genre("Sister", "sister"),
            Genre("Slave", "slave"),
            Genre("Slender", "slender"),
            Genre("Slut", "slut"),
            Genre("Sluts", "sluts"),
            Genre("Solowork", "solowork"),
            Genre("Sousou No Frieren", "sousou-no-frieren"),
            Genre("Space", "space"),
            Genre("Spooning", "spooning"),
            Genre("Sports", "sports"),
            Genre("Squirt.", "squirt"),
            Genre("Squirting", "squirting"),
            Genre("Stocking", "stocking"),
            Genre("Stockings", "stockings"),
            Genre("Studio Fantasia", "studio-fantasia"),
            Genre("Succubus", "succubus"),
            Genre("Super Milk", "super-milk"),
            Genre("Super Power", "super-power"),
            Genre("Supernatural", "supernatural"),
            Genre("Supra Natural", "supra-natural"),
            Genre("Supranatural", "supranatural"),
            Genre("Suspense", "suspense"),
            Genre("Sweat", "sweat"),
            Genre("Swimsuit", "swimsuit"),
            Genre("Swimsuit / Mizugi", "swimsuit-mizugi"),
            Genre("Swimsuit / Mizugi Virgin", "swimsuit-mizugi-virgin"),
            Genre("Tall", "tall"),
            Genre("Teacher", "teacher"),
            Genre("Team Sports", "team-sports"),
            Genre("teen.", "teen"),
            Genre("Tentacle", "tentacle"),
            Genre("Tentacles", "tentacles"),
            Genre("Threesome", "threesome"),
            Genre("Titjob", "titjob"),
            Genre("Tits", "tits"),
            Genre("Titty fuck", "titty-fuck"),
            Genre("Toy", "toy"),
            Genre("Toy Sex", "toy-sex"),
            Genre("Toys", "toys"),
            Genre("Train", "train"),
            Genre("Tsundere", "tsundere"),
            Genre("Ugly Bastard", "ugly-bastard"),
            Genre("Ultra-Huge Tits", "ultra-huge-tits"),
            Genre("Uncensored", "uncensored"),
            Genre("Uncensored Exclusive Việt Nam Girl slutty Flight Attendant fucked in Hotel", "uncensored-exclusive-viet-nam-girl-slutty-flight-attendant-fucked-in-hotel"),
            Genre("Vampire", "vampire"),
            Genre("Vanilla", "vanilla"),
            Genre("Various Occupations", "various-occupations"),
            Genre("Vibrator", "vibrator"),
            Genre("Virgin", "virgin"),
            Genre("Waitress", "waitress"),
            Genre("Watersports", "watersports"),
            Genre("Women on Top", "women-on-top"),
            Genre("Workplace", "workplace"),
            Genre("y Stockings", "y-stockings"),
            Genre("Yaoi", "yaoi"),
            Genre("Young wife", "young-wife"),
            Genre("Yuri", "yuri"),
        )
    }
}
