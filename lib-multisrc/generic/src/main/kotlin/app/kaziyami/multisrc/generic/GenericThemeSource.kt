package app.kaziyami.multisrc.generic

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import app.kaziyami.core.ContentPolicy
import app.kaziyami.core.DateParser
import app.kaziyami.core.EpisodeNumberParser
import app.kaziyami.core.ExtractorRegistry
import app.kaziyami.core.HtmlText
import app.kaziyami.core.InMemorySharedPreferences
import app.kaziyami.core.LazyImage
import app.kaziyami.core.MirrorManager
import app.kaziyami.core.SourceParseException
import app.kaziyami.core.UrlUtils
import app.kaziyami.engine.KaziyamiEpisode
import app.kaziyami.engine.KaziyamiMedia
import app.kaziyami.engine.KaziyamiPage
import app.kaziyami.engine.KaziyamiSource
import app.kaziyami.engine.KaziyamiTrack
import app.kaziyami.engine.KaziyamiVideo
import app.kaziyami.network.BrowserHeaders
import app.kaziyami.network.NetworkConfig
import app.kaziyami.network.TtlCache
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal declarative base class for anime extension sources.
 * Automates URL normalization, selector drift detection, relative path storage,
 * episode deduplication, and mirror failover.
 */
open class GenericThemeSource(
    val config: SourceConfig,
    customPreferences: SharedPreferences? = null
) : ParsedAnimeHttpSource(), ConfigurableAnimeSource, KaziyamiSource {

    val preferences: SharedPreferences by lazy {
        customPreferences ?: runCatching {
            Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
        }.getOrElse {
            InMemorySharedPreferences()
        }
    }

    val pageCache = TtlCache<String, AnimesPage>(defaultTtlMs = NetworkConfig.POPULAR_CACHE_TTL_MS)
    val detailCache = TtlCache<String, SAnime>(defaultTtlMs = NetworkConfig.DETAIL_PAGE_CACHE_TTL_MS)
    val episodeListCache = TtlCache<String, List<SEpisode>>(defaultTtlMs = NetworkConfig.EPISODE_LIST_CACHE_TTL_MS)

    override val name: String = config.name
    override val lang: String = config.lang
    override val supportsLatest: Boolean = true

    init {
        try {
            Log.i("Kaziyami", "Kaziyami $name v14.5 loaded, libVersion 14")
        } catch (_: Throwable) {
            // Logging failure must never break source construction
        }
        syncHostUserAgent()
    }

    private fun syncHostUserAgent() {
        runCatching {
            val hostUa = network.defaultUserAgentProvider()
            if (hostUa.isNotBlank()) {
                app.kaziyami.network.BrowserProfile.setRuntimeUserAgent(hostUa)
            }
        }
    }

    override val client: OkHttpClient by lazy {
        @Suppress("DEPRECATION")
        runCatching { network.client }
            .recoverCatching { network.cloudflareClient }
            .getOrElse { app.kaziyami.network.NetworkHelper().client }
    }

    override fun headersBuilder(): Headers.Builder {
        syncHostUserAgent()
        return BrowserHeaders.defaultHeaders(baseUrl).newBuilder()
    }

    val mirrorManager = MirrorManager(
        defaultBaseUrl = config.baseUrl,
        mirrors = config.mirrors,
        preferences = { getDomainPreference() }
    )

    override val baseUrl: String
        get() = mirrorManager.getBaseUrl()

    private val matchedSelectorCache = ConcurrentHashMap<String, String>()

    open fun getDomainPreference(): String? {
        val custom = preferences.getString("custom_domain_override", "")?.trim().orEmpty()
        if (custom.isNotBlank()) return custom
        return preferences.getString("preferred_domain", "")?.takeIf { it.isNotBlank() }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        val agePref = SwitchPreferenceCompat(context).apply {
            key = "age_gate_confirmed"
            title = "I am 18 or older"
            summary = "Confirm age requirement (18+) to access adult content"
            setDefaultValue(false)
        }
        screen.addPreference(agePref)

        val mirrorsList = (listOf(config.baseUrl) + config.mirrors).distinct().toTypedArray()

        val domainPref = ListPreference(context).apply {
            key = "preferred_domain"
            title = "Preferred domain"
            summary = "%s"
            entries = mirrorsList
            entryValues = mirrorsList
            setDefaultValue(config.baseUrl)
        }
        screen.addPreference(domainPref)

        val customDomainPref = EditTextPreference(context).apply {
            key = "custom_domain_override"
            title = "Custom domain override"
            summary = "Enter custom domain or mirror URL (leave blank to use defaults)"
            setDefaultValue("")
        }
        screen.addPreference(customDomainPref)

        val qualityPref = ListPreference(context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            summary = "%s"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080p", "720p", "480p", "360p")
            setDefaultValue("1080p")
        }
        screen.addPreference(qualityPref)

        val hostPref = EditTextPreference(context).apply {
            key = "preferred_host"
            title = "Preferred video host"
            summary = "Prioritize specific video embed host (e.g. streamtape, voe, doodstream)"
            setDefaultValue("")
        }
        screen.addPreference(hostPref)
    }

    open fun isAgeConfirmed(): Boolean = preferences.getBoolean("age_gate_confirmed", false)

    protected fun checkAgeConfirmed() {
        if (!isAgeConfirmed()) {
            throw IllegalStateException("Age verification required: Confirm 'I am 18 or older' in extension settings to access $name.")
        }
    }

    /**
     * Tries an ordered list of candidate selectors (primary + alternates), remembers which one
     * succeeded in the active session, and returns the matched elements.
     */
    fun selectFirstMatching(document: Element, selectorKey: String, candidates: List<String>): Pair<String, Elements> {
        val cached = matchedSelectorCache[selectorKey]
        if (cached != null) {
            val els = document.select(cached)
            if (els.isNotEmpty()) return Pair(cached, els)
        }
        for (candidate in candidates) {
            val els = document.select(candidate)
            if (els.isNotEmpty()) {
                matchedSelectorCache[selectorKey] = candidate
                return Pair(candidate, els)
            }
        }
        return Pair("", Elements())
    }

    // ============================= Popular Anime =============================

    public override fun popularAnimeRequest(page: Int): Request {
        checkAgeConfirmed()
        val path = safeFormatPage(config.popularPath, page)
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = config.selectors.item

    public override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val (_, elements) = selectFirstMatching(document, "popular_item", config.selectors.itemCandidates)

        if (elements.isEmpty()) {
            if (isEmptyState(document)) {
                return AnimesPage(emptyList(), false)
            }
            throw SourceParseException(
                source = name,
                pageType = "popular anime list",
                selectorName = config.selectors.item,
                urlPath = UrlUtils.getPath(response.request.url.toString())
            )
        }

        val animeList = ContentPolicy.filterAnimes(elements.map { popularAnimeFromElement(it) }.distinctBy { it.url })
        val hasNextPage = popularAnimeNextPageSelector()?.let { selector ->
            document.selectFirst(selector) != null
        } ?: false

        return AnimesPage(animeList, hasNextPage)
    }

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val titleEl = element.selectFirst(config.selectors.itemTitle)
        val title = titleEl?.attr("title")?.takeIf { it.isNotBlank() }
            ?: titleEl?.text()?.takeIf { it.isNotBlank() }
            ?: element.attr("title").takeIf { it.isNotBlank() }
            ?: element.selectFirst("a[title]")?.attr("title")
            ?: element.selectFirst("img")?.attr("alt")
            ?: element.attr("alt")

        anime.title = if (config.quirks.stripTitleBoilerplate) {
            HtmlText.stripBoilerplate(title)
        } else {
            HtmlText.clean(title)
        }

        val linkEl = if (element.tagName() == "a" && element.hasAttr("href")) element
            else element.selectFirst(config.selectors.itemUrl) ?: element.selectFirst("a[href]")
        val href = linkEl?.attr("href") ?: ""
        // Library safety: store clean relative path
        anime.url = UrlUtils.getPath(href)

        val imgEl = element.selectFirst(config.selectors.itemThumbnail)
            ?: if (element.tagName() == "img" || element.hasAttr("style") || element.className().contains("thumb")) element else null
        anime.thumbnail_url = LazyImage.extractUrl(imgEl, baseUrl)
        return anime
    }

    override fun popularAnimeNextPageSelector(): String? = config.selectors.nextPage

    // ============================= Latest Updates =============================

    public override fun latestUpdatesRequest(page: Int): Request {
        checkAgeConfirmed()
        val path = safeFormatPage(config.latestPath, page)
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    private fun safeFormatPage(template: String, page: Int): String {
        return template.replace("%d", page.toString())
    }

    override fun latestUpdatesSelector(): String = config.selectors.item

    public override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val (_, elements) = selectFirstMatching(document, "latest_item", config.selectors.itemCandidates)

        if (elements.isEmpty()) {
            if (isEmptyState(document)) {
                return AnimesPage(emptyList(), false)
            }
            throw SourceParseException(
                source = name,
                pageType = "latest updates list",
                selectorName = config.selectors.item,
                urlPath = UrlUtils.getPath(response.request.url.toString())
            )
        }

        val animeList = ContentPolicy.filterAnimes(elements.map { latestUpdatesFromElement(it) }.distinctBy { it.url })
        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.selectFirst(selector) != null
        } ?: false

        return AnimesPage(animeList, hasNextPage)
    }

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = config.selectors.nextPage

    // ============================= Search & Filters =============================

    public override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        checkAgeConfirmed()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val path = config.searchPath.replace("%s", encodedQuery).replace("%d", page.toString())
        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = config.selectors.item

    public override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val (_, elements) = selectFirstMatching(document, "search_item", config.selectors.itemCandidates)

        if (elements.isEmpty()) {
            if (isEmptyState(document)) {
                return AnimesPage(emptyList(), false)
            }
            throw SourceParseException(
                source = name,
                pageType = "search results",
                selectorName = config.selectors.item,
                urlPath = UrlUtils.getPath(response.request.url.toString())
            )
        }

        val animeList = ContentPolicy.filterAnimes(elements.map { searchAnimeFromElement(it) }.distinctBy { it.url })
        val hasNextPage = searchAnimeNextPageSelector()?.let { selector ->
            document.selectFirst(selector) != null
        } ?: false

        return AnimesPage(animeList, hasNextPage)
    }

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String? = config.selectors.nextPage

    // ============================= Anime Details =============================

    public override fun animeDetailsRequest(anime: SAnime): Request {
        checkAgeConfirmed()
        return GET(UrlUtils.resolve(baseUrl, anime.url), headers)
    }

    public override fun animeDetailsParse(response: Response): SAnime = super.animeDetailsParse(response)

    public override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        val titleEl = config.selectors.detailsTitleCandidates.asSequence()
            .mapNotNull { document.selectFirst(it) }
            .firstOrNull()
        val title = titleEl?.text() ?: ""
        anime.title = if (config.quirks.stripTitleBoilerplate) {
            HtmlText.stripBoilerplate(title)
        } else {
            HtmlText.clean(title)
        }

        val thumbEl = document.selectFirst(config.selectors.detailsThumbnail)
        anime.thumbnail_url = LazyImage.extractUrl(thumbEl, baseUrl)

        val descEl = document.select(config.selectors.detailsDescription)
        val descText = descEl.map { el ->
            if (el.tagName() == "meta") el.attr("content") else el.text()
        }.map { HtmlText.clean(it) }.filter { it.isNotBlank() }.distinct().joinToString("\n\n")
        anime.description = descText

        val genres = document.select(config.selectors.detailsGenre).map { HtmlText.clean(it.text()) }
        if (genres.isNotEmpty()) {
            anime.genre = genres.joinToString(", ")
        }

        val statusText = document.selectFirst(config.selectors.detailsStatus)?.text() ?: ""
        anime.status = when {
            statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
            statusText.contains("Completed", ignoreCase = true) || statusText.contains("Tamat", ignoreCase = true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }

        val authorEl = document.selectFirst(config.selectors.detailsAuthor)
        anime.author = authorEl?.text()?.let { HtmlText.clean(it) }

        if (!ContentPolicy.isSafeAnime(anime)) {
            throw SourceParseException(
                source = name,
                pageType = "anime details",
                selectorName = "ContentPolicy",
                urlPath = "Prohibited content indicator detected"
            )
        }

        return anime
    }

    // ============================= Episode List =============================

    public override fun episodeListRequest(anime: SAnime): Request {
        checkAgeConfirmed()
        return GET(UrlUtils.resolve(baseUrl, anime.url), headers)
    }

    override fun episodeListSelector(): String = config.selectors.episodeList

    override fun episodeFromElement(element: Element): SEpisode {
        val ep = SEpisode.create()
        val linkEl = if (element.tagName() == "a" && element.hasAttr("href")) element
            else element.selectFirst(config.selectors.episodeUrl) ?: element.selectFirst("a[href]")
        val href = linkEl?.attr("href") ?: ""
        // Library safety: store clean relative path
        ep.url = UrlUtils.getPath(href)

        val nameEl = element.selectFirst(config.selectors.episodeName) ?: linkEl
        val rawName = nameEl?.text()?.takeIf { it.isNotBlank() } ?: "Episode"
        ep.name = HtmlText.clean(rawName)
        ep.episode_number = EpisodeNumberParser.parse(ep.name)

        val dateEl = element.selectFirst(config.selectors.episodeDate)
        if (dateEl != null) {
            ep.date_upload = DateParser.parse(dateEl.text())
        }

        return ep
    }

    public override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val (_, elements) = selectFirstMatching(document, "episode_list", config.selectors.episodeListCandidates)

        if (elements.isEmpty()) {
            if (isEmptyState(document)) {
                return emptyList()
            }
            // Check if page represents a single-episode or movie video release
            val hasVideoPlayer = selectFirstMatching(document, "player_iframes", config.selectors.playerIframesCandidates).second.isNotEmpty() ||
                document.select("video, iframe, #player, .player, .play-box, .player-container, #stream, .stream, .video-content, .embed, [id*=player], [class*=player], [id*=stream], [class*=stream]").isNotEmpty() ||
                document.select("a[href*='download'], a[href*='drive'], a[href*='mega.'], a[href*='mediafire'], .linksbt, .dlx, .download, .box-download").isNotEmpty() ||
                document.select("#playex, .playex, [id*=play], [class*=play], .sist_player, #playeroptionsul, .playeroptions").isNotEmpty() ||
                response.request.url.encodedPath.contains("/episodes/") ||
                response.request.url.encodedPath.contains("/episode/")

            if (hasVideoPlayer) {
                val ep = SEpisode.create().apply {
                    url = UrlUtils.getPath(response.request.url.toString())
                    name = "Full Episode"
                    episode_number = 1.0f
                    val dateEl = document.selectFirst(".released-date, time, .upload-date, .date, .post-date, .time")
                    if (dateEl != null) {
                        date_upload = DateParser.parse(dateEl.text())
                    }
                }
                return listOf(ep)
            }

            throw SourceParseException(
                source = name,
                pageType = "episode list",
                selectorName = config.selectors.episodeList,
                urlPath = UrlUtils.getPath(response.request.url.toString())
            )
        }

        val episodes = elements.map { episodeFromElement(it) }
        val deduped = episodes.distinctBy { it.url }
        val ordered = if (config.quirks.episodesReversed) {
            deduped.reversed()
        } else {
            deduped
        }
        return ordered.sortedByDescending { it.episode_number }
    }

    // ============================= Video List =============================

    public override fun videoListRequest(episode: SEpisode): Request {
        checkAgeConfirmed()
        return GET(UrlUtils.resolve(baseUrl, episode.url), headers)
    }

    public override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val iframeUrls = config.selectors.playerIframesCandidates.flatMap { selector ->
            document.select(selector).mapNotNull { element: Element ->
                val src = element.attr("data-src").ifEmpty {
                    element.attr("data-lazy-src").ifEmpty {
                        element.attr("src")
                    }
                }
                if (src.isNotBlank() && isVideoCandidateUrl(src)) src else null
            }
        }.toMutableList()

        document.select("video source[src], video[src], video source[data-src], video[data-src]").forEach { el ->
            val src = el.attr("data-src").ifEmpty { el.attr("src") }
            if (src.isNotBlank() && isVideoCandidateUrl(src)) iframeUrls.add(src)
        }

        val resolvedUrls = iframeUrls.distinct().map { UrlUtils.resolve(baseUrl, it) }

        val preferredQuality = preferences.getString("preferred_quality", "1080p") ?: "1080p"
        val preferredHost = preferences.getString("preferred_host", "")?.takeIf { it.isNotBlank() }
        val referer = response.request.url.toString()
        val videos = ExtractorRegistry.resolveSequential(
            urls = resolvedUrls,
            preferredHost = preferredHost,
            referer = referer,
            client = client
        ).let {
            ExtractorRegistry.deduplicateAndSort(it, preferredQuality = preferredQuality, preferredHost = preferredHost)
        }

        if (videos.isEmpty()) {
            val fallback = resolvedUrls.firstOrNull() ?: response.request.url.toString()
            return listOf(
                Video(
                    url = fallback,
                    quality = "Stream (Open in WebView if blocked)",
                    videoUrl = fallback,
                    headers = headers
                )
            )
        }
        return videos
    }

    override fun videoFromElement(element: Element): Video {
        throw UnsupportedOperationException("videoFromElement is not used in GenericThemeSource")
    }

    override fun videoUrlParse(document: Document): String {
        throw UnsupportedOperationException("videoUrlParse is not used in GenericThemeSource")
    }

    override fun videoListSelector(): String {
        throw UnsupportedOperationException("videoListSelector is not used in GenericThemeSource")
    }

    // ============================= Filters =============================

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Press 'Reset' to clear all filters"),
            GenericSortFilter()
        )
    }

    open class GenericSortFilter(
        name: String = "Sort",
        values: Array<String> = arrayOf("Default", "Popular", "Latest", "A-Z", "Rating"),
        state: Int = 0
    ) : AnimeFilter.Select<String>(name, values, state)

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val cacheKey = "popular_$page"
        val cached = pageCache.get(cacheKey)
        if (cached != null) return cached
        val fresh = super.getPopularAnime(page)
        pageCache.put(cacheKey, fresh)
        return fresh
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val cacheKey = "latest_$page"
        val cached = pageCache.get(cacheKey)
        if (cached != null) return cached
        val fresh = super.getLatestUpdates(page)
        pageCache.put(cacheKey, fresh)
        return fresh
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val cached = detailCache.get(anime.url)
        if (cached != null) return cached
        val fresh = super.getAnimeDetails(anime)
        detailCache.put(anime.url, fresh)
        return fresh
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val cached = episodeListCache.get(anime.url)
        if (cached != null) return cached
        val fresh = super.getEpisodeList(anime)
        episodeListCache.put(anime.url, fresh)
        return fresh
    }

    /**
     * Checks if a document contains explicit empty-state indicators rather than selector drift.
     */
    open fun isEmptyState(document: Document): Boolean {
        val body = document.body() ?: return true
        val bodyText = body.text().lowercase().trim()
        if (bodyText.isEmpty()) return true
        return bodyText.contains("no results") ||
            bodyText.contains("no videos") ||
            bodyText.contains("no anime") ||
            bodyText.contains("nothing found") ||
            bodyText.contains("not found") ||
            bodyText.contains("tidak ada") ||
            bodyText.contains("ditemukan") ||
            bodyText.contains("0 results") ||
            document.selectFirst(".empty, .not-found, .notfound, .no-results, .error-404, .search-empty") != null
    }

    // ============================= KaziyamiSource Implementation =============================

    override val id: Long
        get() = super.id

    override fun popular(page: Int): KaziyamiPage<KaziyamiMedia> {
        val req = popularAnimeRequest(page)
        val res = client.newCall(req).execute()
        return parsePopularResponse(res)
    }

    override fun latest(page: Int): KaziyamiPage<KaziyamiMedia> {
        val req = latestUpdatesRequest(page)
        val res = client.newCall(req).execute()
        return parseLatestResponse(res)
    }

    override fun search(page: Int, query: String, filters: List<Any>): KaziyamiPage<KaziyamiMedia> {
        val filterList = (filters.firstOrNull() as? AnimeFilterList) ?: getFilterList()
        val req = searchAnimeRequest(page, query, filterList)
        val res = client.newCall(req).execute()
        return parseSearchResponse(res)
    }

    override fun details(url: String): KaziyamiMedia {
        val sAnime = SAnime.create().apply { this.url = url }
        val req = animeDetailsRequest(sAnime)
        val res = client.newCall(req).execute()
        return parseDetailsResponse(res)
    }

    override fun episodes(url: String): List<KaziyamiEpisode> {
        val sAnime = SAnime.create().apply { this.url = url }
        val req = episodeListRequest(sAnime)
        val res = client.newCall(req).execute()
        return parseEpisodesResponse(res)
    }

    override fun videos(url: String): List<KaziyamiVideo> {
        val sEpisode = SEpisode.create().apply { this.url = url }
        val req = videoListRequest(sEpisode)
        val res = client.newCall(req).execute()
        return parseVideosResponse(res)
    }

    fun parsePopularResponse(response: Response): KaziyamiPage<KaziyamiMedia> {
        val page = popularAnimeParse(response)
        return KaziyamiPage(ContentPolicy.filterMedia(page.animes.map { it.toKaziyamiMedia() }), page.hasNextPage)
    }

    fun parseLatestResponse(response: Response): KaziyamiPage<KaziyamiMedia> {
        val page = latestUpdatesParse(response)
        return KaziyamiPage(ContentPolicy.filterMedia(page.animes.map { it.toKaziyamiMedia() }), page.hasNextPage)
    }

    fun parseSearchResponse(response: Response): KaziyamiPage<KaziyamiMedia> {
        val page = searchAnimeParse(response)
        return KaziyamiPage(ContentPolicy.filterMedia(page.animes.map { it.toKaziyamiMedia() }), page.hasNextPage)
    }

    fun parseDetailsResponse(response: Response): KaziyamiMedia {
        val doc = response.asJsoup()
        val parsed = animeDetailsParse(doc)
        val path = UrlUtils.getPath(response.request.url.toString())
        parsed.url = path
        return parsed.toKaziyamiMedia()
    }

    fun parseEpisodesResponse(response: Response): List<KaziyamiEpisode> {
        return episodeListParse(response).map { it.toKaziyamiEpisode() }
    }

    fun parseVideosResponse(response: Response): List<KaziyamiVideo> {
        return videoListParse(response).map { it.toKaziyamiVideo() }
    }

    companion object {
        fun isVideoCandidateUrl(url: String): Boolean {
            val lower = url.lowercase().trim()
            if (lower.isEmpty() || lower.startsWith("javascript:") || lower.startsWith("about:") || lower.startsWith("data:")) return false
            val nonVideo = listOf(
                "discord.com", "discordapp.com",
                "smartfloralab.com", "a-ads.com", "ad.a-ads.com",
                "facebook.com", "twitter.com", "x.com", "instagram.com",
                "google.com", "googletagmanager.com", "google-analytics.com",
                "histats.com", "disqus.com", "doubleclick.net", "adservice", "googleads",
                "/widget", "recaptcha", "hcaptcha"
            )
            return nonVideo.none { lower.contains(it) }
        }

        fun SAnime.toKaziyamiMedia(): KaziyamiMedia {
            val resolvedUrl = try { url } catch (_: Exception) { "" }
            return KaziyamiMedia(
                id = resolvedUrl,
                title = try { title } catch (_: Exception) { "" },
                url = resolvedUrl,
                coverUrl = thumbnail_url.orEmpty(),
                status = status,
                genres = genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                description = description.orEmpty()
            )
        }

        fun SEpisode.toKaziyamiEpisode(): KaziyamiEpisode = KaziyamiEpisode(
            name = name,
            url = url,
            episodeNumber = episode_number,
            dateUpload = date_upload
        )

        fun Video.toKaziyamiVideo(): KaziyamiVideo = KaziyamiVideo(
            url = url,
            quality = quality,
            videoUrl = videoUrl.orEmpty(),
            headers = headers?.let { h -> (0 until h.size).associate { h.name(it) to h.value(it) } } ?: emptyMap(),
            subtitles = subtitleTracks.map { KaziyamiTrack(it.url, it.lang) }
        )
    }
}
