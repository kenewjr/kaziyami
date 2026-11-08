package eu.kanade.tachiyomi.animeextension.en.hanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import app.kaziyami.core.DateParser
import app.kaziyami.core.ExtractorRegistry
import app.kaziyami.core.HtmlText
import app.kaziyami.core.UrlUtils
import app.kaziyami.multisrc.generic.GenericThemeSource
import app.kaziyami.multisrc.generic.QuirksConfig
import app.kaziyami.multisrc.generic.SelectorsConfig
import app.kaziyami.multisrc.generic.SourceConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Hanime : GenericThemeSource(
    config = SourceConfig(
        name = "Hanime",
        lang = "en",
        baseUrl = "https://hanime.tv",
        mirrors = listOf("https://hhanime.tv"),
        popularPath = "/browse/trending?page=%d",
        latestPath = "/?page=%d",
        searchPath = "/search?q=%s&page=%d",
        selectors = SelectorsConfig(
            item = "a[href*='/videos/hentai/'], a[href*='/hentai/video/'], div.card, a.card, div.video-card",
            itemTitle = "h3, .title, a[title]",
            itemUrl = "a[href]",
            itemThumbnail = "img",
            nextPage = ".pagination a.next, a.next",
            detailsTitle = "h1, .video-details h1, h1.entry-title",
            detailsThumbnail = "meta[property='og:image'], .poster img, img",
            detailsDescription = ".description, .video-description, p",
            detailsGenre = "a[href*='/genre/'], a[href*='/tag/'], .tags a",
            detailsAuthor = ".brand, .studio",
            episodeList = ".playlist-item, a[href*='/videos/hentai/'], a[href*='/hentai/video/']",
            playerIframes = "iframe[src], video source"
        ),
        quirks = QuirksConfig(
            customReferer = "https://hanime.tv/"
        )
    )
) {

    companion object {
        private val M3U8_REGEX = Pattern.compile("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE)
        private val MP4_REGEX = Pattern.compile("""(https?://[^\s"'<>]+\.mp4[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE)
        private const val HANDSHAKE_SECRET = "htv-insecure-handshake-v1"
        private const val HANDSHAKE_AAD = "htv-insecure-v1"

        data class Genre(val name: String, val slug: String)

        val GENRES = arrayOf(
            Genre("All", ""),
            Genre("3D", "3d"),
            Genre("Ahegao", "ahegao"),
            Genre("Anal", "anal"),
            Genre("BDSM", "bdsm"),
            Genre("Big Boobs", "big boobs"),
            Genre("Blow Job", "blow job"),
            Genre("Bondage", "bondage"),
            Genre("Boob Job", "boob job"),
            Genre("Censored", "censored"),
            Genre("Comedy", "comedy"),
            Genre("Cosplay", "cosplay"),
            Genre("Creampie", "creampie"),
            Genre("Dark Skin", "dark skin"),
            Genre("Facial", "facial"),
            Genre("Fantasy", "fantasy"),
            Genre("Filmed", "filmed"),
            Genre("Foot Job", "foot job"),
            Genre("Futanari", "futanari"),
            Genre("Gangbang", "gangbang"),
            Genre("Glasses", "glasses"),
            Genre("Hand Job", "hand job"),
            Genre("Harem", "harem"),
            Genre("HD", "hd"),
            Genre("Horror", "horror"),
            Genre("Incest", "incest"),
            Genre("Inflation", "inflation"),
            Genre("Lactation", "lactation"),
            Genre("Maid", "maid"),
            Genre("Masturbation", "masturbation"),
            Genre("Milf", "milf"),
            Genre("Mind Break", "mind break"),
            Genre("Mind Control", "mind control"),
            Genre("Monster", "monster"),
            Genre("Nekomimi", "nekomimi"),
            Genre("NTR", "ntr"),
            Genre("Nurse", "nurse"),
            Genre("Orgy", "orgy"),
            Genre("Plot", "plot"),
            Genre("POV", "pov"),
            Genre("Pregnant", "pregnant"),
            Genre("Public Sex", "public sex"),
            Genre("Rimjob", "rimjob"),
            Genre("School Girl", "school girl"),
            Genre("Softcore", "softcore"),
            Genre("Swimsuit", "swimsuit"),
            Genre("Teacher", "teacher"),
            Genre("Tentacle", "tentacle"),
            Genre("Threesome", "threesome"),
            Genre("Toys", "toys"),
            Genre("Trap", "trap"),
            Genre("Tsundere", "tsundere"),
            Genre("Ugly Bastard", "ugly bastard"),
            Genre("Uncensored", "uncensored"),
            Genre("Vanilla", "vanilla"),
            Genre("Virgin", "virgin"),
            Genre("Watersports", "watersports"),
            Genre("X-Ray", "x-ray"),
            Genre("Yaoi", "yaoi"),
            Genre("Yuri", "yuri")
        )
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val page = super.popularAnimeParse(response)
        val hasNext = page.hasNextPage || page.animes.size >= 20
        return AnimesPage(page.animes, hasNext)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val page = super.latestUpdatesParse(response)
        val hasNext = page.hasNextPage || page.animes.size >= 20
        return AnimesPage(page.animes, hasNext)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        checkAgeConfirmed()
        var genreSlug = ""
        var sortIndex = 0

        for (filter in filters) {
            when (filter) {
                is HanimeGenreFilter -> genreSlug = filter.toSlug()
                is HanimeSortFilter -> sortIndex = filter.state
                else -> {}
            }
        }

        val basePath = when {
            genreSlug.isNotBlank() -> {
                val encodedGenre = URLEncoder.encode(genreSlug, "UTF-8").replace("+", "%20")
                "/browse/tags/$encodedGenre?page=$page"
            }
            sortIndex == 1 -> {
                if (page > 1) "/?page=$page" else "/"
            }
            else -> {
                String.format(config.popularPath, page)
            }
        }

        val cleanQuery = query.trim()
        val path = if (cleanQuery.isNotBlank()) {
            val sep = if (basePath.contains("?")) "&" else "?"
            val encodedQ = URLEncoder.encode(cleanQuery, "UTF-8")
            "$basePath${sep}q=$encodedQ"
        } else {
            basePath
        }

        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val body = response.body?.string().orEmpty()
        if (body.trimStart().startsWith("{")) {
            return try {
                val json = Json.parseToJsonElement(body).jsonObject
                val data = json["data"]?.jsonArray ?: return AnimesPage(emptyList(), false)
                val animes = data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val slug = obj["slug"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: ""
                    val cover = obj["cover_url"]?.jsonPrimitive?.content
                        ?: obj["poster_url"]?.jsonPrimitive?.content.orEmpty()
                    SAnime.create().apply {
                        this.title = HtmlText.clean(name)
                        this.url = "/videos/hentai/$slug"
                        this.thumbnail_url = cover
                    }
                }
                val query = response.request.url.queryParameter("q").orEmpty().lowercase()
                val filtered = if (query.isNotBlank()) {
                    animes.filter { it.title.lowercase().contains(query) }
                } else {
                    animes
                }
                val pageSize = 24
                val pageIndex = (response.request.url.queryParameter("page")?.toIntOrNull() ?: 0)
                val paged = filtered.drop(pageIndex * pageSize).take(pageSize)
                val hasNext = (pageIndex + 1) * pageSize < filtered.size
                AnimesPage(paged, hasNext)
            } catch (_: Exception) {
                AnimesPage(emptyList(), false)
            }
        }

        val doc = org.jsoup.Jsoup.parse(body, baseUrl)
        val (_, elements) = selectFirstMatching(doc, "search_item", config.selectors.itemCandidates)
        if (elements.isEmpty()) {
            if (isEmptyState(doc)) return AnimesPage(emptyList(), false)
            throw app.kaziyami.core.SourceParseException(
                source = name,
                pageType = "search results",
                selectorName = config.selectors.item,
                urlPath = UrlUtils.getPath(response.request.url.toString())
            )
        }
        val animeList = elements.map { searchAnimeFromElement(it) }
        val query = response.request.url.queryParameter("q").orEmpty().lowercase()
        val filtered = if (query.isNotBlank()) {
            animeList.filter { it.title.lowercase().contains(query) }
        } else {
            animeList
        }
        val finalAnimes = if (filtered.isNotEmpty()) filtered else animeList
        val hasNextPage = searchAnimeNextPageSelector()?.let { selector ->
            doc.selectFirst(selector) != null
        } ?: (elements.size >= 20)
        return AnimesPage(finalAnimes, hasNextPage)
    }

    public override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val playlistItems = document.select(".playlist a, .series-episodes a")
        if (playlistItems.isNotEmpty()) {
            return playlistItems.mapIndexed { index, el ->
                val ep = SEpisode.create()
                val href = el.attr("href")
                ep.url = UrlUtils.resolve(baseUrl, href)
                ep.name = HtmlText.clean(el.text()).ifEmpty { "Episode ${index + 1}" }
                ep.episode_number = (index + 1).toFloat()
                ep
            }
        }

        // Single video release
        val ep = SEpisode.create()
        ep.url = response.request.url.toString()
        ep.name = "Full Episode"
        ep.episode_number = 1.0f

        val dateEl = document.selectFirst(".released-date, time, .upload-date")
        if (dateEl != null) {
            ep.date_upload = DateParser.parse(dateEl.text())
        }

        return listOf(ep)
    }

    public override fun videoListParse(response: Response): List<Video> {
        val body = response.body?.string().orEmpty()
        val unescapedBody = body.replace("\\/", "/")
        val videoList = mutableListOf<Video>()

        // 1. Embedded HLS m3u8 streams
        val m3u8Matcher = M3U8_REGEX.matcher(unescapedBody)
        while (m3u8Matcher.find()) {
            val streamUrl = m3u8Matcher.group(1)
            videoList.add(
                Video(
                    url = streamUrl,
                    quality = "HLS 720p/1080p",
                    videoUrl = streamUrl,
                    headers = headers
                )
            )
        }

        // 2. Embedded MP4 streams
        val mp4Matcher = MP4_REGEX.matcher(unescapedBody)
        while (mp4Matcher.find()) {
            val streamUrl = mp4Matcher.group(1)
            videoList.add(
                Video(
                    url = streamUrl,
                    quality = "Direct MP4",
                    videoUrl = streamUrl,
                    headers = headers
                )
            )
        }

        // 3. Extractor registry iframes
        val doc = org.jsoup.Jsoup.parse(unescapedBody, baseUrl)
        val iframes = doc.select("iframe[src], iframe[data-src]").mapNotNull {
            val src = it.attr("data-src").ifEmpty { it.attr("src") }
            if (src.isNotBlank()) UrlUtils.resolve(baseUrl, src) else null
        }
        videoList.addAll(ExtractorRegistry.extractAll(iframes, client))

        // 4. Primary Hanime API v11 cryptographic handshake
        if (videoList.isEmpty()) {
            val slug = response.request.url.toString().trimEnd('/').substringAfterLast('/')
            if (slug.isNotBlank() && slug != "video" && slug != "hentai") {
                videoList.addAll(fetchStreamSources(slug))
            }
        }

        val deduped = videoList.distinctBy { it.videoUrl }
        if (deduped.isEmpty()) {
            val epUrl = response.request.url.toString()
            return listOf(
                Video(
                    url = epUrl,
                    quality = "Open in WebView (Cloudflare Turnstile)",
                    videoUrl = epUrl,
                    headers = headers
                )
            )
        }
        return deduped
    }

    /**
     * Resolves playable HLS streams via Hanime's AES-256-GCM authenticated handshake.
     */
    fun fetchStreamSources(slug: String): List<Video> {
        val cleanSlug = slug.trim().trimEnd('/')
        if (cleanSlug.isEmpty()) return emptyList()

        return try {
            // 1. Fetch CSRF token
            val csrfReq = Request.Builder()
                .url("https://ct.hanime.tv/csrf-token")
                .header("Referer", "https://hanime.tv/")
                .header("Origin", "https://hanime.tv")
                .header("Accept", "application/json")
                .build()
            val csrfRes = client.newCall(csrfReq).execute()
            val csrfBody = csrfRes.body?.string().orEmpty()
            val csrfToken = csrfBody.substringAfter("\"csrf_token\":\"", "").substringBefore("\"", "")
            if (csrfToken.isBlank()) return emptyList()

            // 2. Generate Signature & Timestamp
            val time = System.currentTimeMillis() / 1000
            val sig = sha256Hex("$time,Xkdi29,https://hanime.tv,mn2,$time")

            // 3. Encrypt Handshake Payload
            val secret = HANDSHAKE_SECRET.toByteArray(StandardCharsets.UTF_8)
            val aad = HANDSHAKE_AAD.toByteArray(StandardCharsets.UTF_8)
            val md = MessageDigest.getInstance("SHA-256")
            val keySpec = SecretKeySpec(md.digest(secret), "AES")

            val payload = """{"timestamp_unix":$time,"directive":"htv_player_handshake","slug":"$cleanSlug","csrf_token":"$csrfToken"}"""
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            val encrypted = cipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))

            val ciphertext = encrypted.copyOfRange(0, encrypted.size - 16)
            val tag = encrypted.copyOfRange(encrypted.size - 16, encrypted.size)

            val ivB64 = Base64Url.encode(iv)
            val tagB64 = Base64Url.encode(tag)
            val dataB64 = Base64Url.encode(ciphertext)

            val envelope = """{"v":1,"alg":"AES-256-GCM","iv":"$ivB64","tag":"$tagB64","data":"$dataB64"}"""
            val token = Base64Url.encode(envelope.toByteArray(StandardCharsets.UTF_8))

            // 4. POST Handshake to auth.hanime.tv
            val reqJson = """{"token":"$token","csrf_token":"$csrfToken"}"""
            val req = Request.Builder()
                .url("https://auth.hanime.tv/api/v11/handshake")
                .header("Referer", "https://hanime.tv/videos/hentai/$cleanSlug")
                .header("Origin", "https://hanime.tv")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-signature-version", "web2")
                .header("x-signature", sig)
                .header("x-time", time.toString())
                .header("x-csrf-token", csrfToken)
                .post(reqJson.toRequestBody("application/json".toMediaType()))
                .build()

            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return emptyList()
            val xToken = res.header("x-token") ?: return emptyList()

            // 5. Decrypt x-token
            val envJsonStr = String(Base64Url.decode(xToken), StandardCharsets.UTF_8)
            val ivResp = Base64Url.decode(envJsonStr.substringAfter("\"iv\":\"", "").substringBefore("\"", ""))
            val tagResp = Base64Url.decode(envJsonStr.substringAfter("\"tag\":\"", "").substringBefore("\"", ""))
            val dataResp = Base64Url.decode(envJsonStr.substringAfter("\"data\":\"", "").substringBefore("\"", ""))

            val combined = ByteArray(dataResp.size + tagResp.size)
            System.arraycopy(dataResp, 0, combined, 0, dataResp.size)
            System.arraycopy(tagResp, 0, combined, dataResp.size, tagResp.size)

            val decCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decCipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, ivResp))
            decCipher.updateAAD(aad)
            val decryptedJson = String(decCipher.doFinal(combined), StandardCharsets.UTF_8)

            parseHandshakeSources(decryptedJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseHandshakeSources(jsonStr: String): List<Video> {
        return try {
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val sources = root["sources"]?.jsonArray ?: return emptyList()
            val list = mutableListOf<Video>()
            for (item in sources) {
                val obj = item.jsonObject
                val src = obj["src"]?.jsonPrimitive?.content.orEmpty()
                if (src.isBlank()) continue
                val label = obj["label"]?.jsonPrimitive?.content ?: "720p"
                val streamUrl = UrlUtils.resolve(baseUrl, src)
                list.add(
                    Video(
                        url = streamUrl,
                        quality = "Hanime $label",
                        videoUrl = streamUrl,
                        headers = headers
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Pure Kotlin URL-safe Base64 encoder/decoder (API 21-35 compliant, zero Android/JVM dependencies).
     */
    object Base64Url {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private val INVERSE = IntArray(128) { -1 }.apply {
            for (i in ALPHABET.indices) {
                this[ALPHABET[i].code] = i
            }
            this['+'.code] = 62
            this['/'.code] = 63
        }

        fun encode(data: ByteArray): String {
            val sb = StringBuilder((data.size * 4 + 2) / 3)
            var i = 0
            while (i < data.size) {
                val b0 = data[i++].toInt() and 0xFF
                val b1 = if (i < data.size) data[i++].toInt() and 0xFF else -1
                val b2 = if (i < data.size) data[i++].toInt() and 0xFF else -1

                sb.append(ALPHABET[b0 ushr 2])
                if (b1 != -1) {
                    sb.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
                    if (b2 != -1) {
                        sb.append(ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
                        sb.append(ALPHABET[b2 and 0x3F])
                    } else {
                        sb.append(ALPHABET[(b1 and 0x0F) shl 2])
                    }
                } else {
                    sb.append(ALPHABET[(b0 and 0x03) shl 4])
                }
            }
            return sb.toString()
        }

        fun decode(s: String): ByteArray {
            val clean = s.trim().trimEnd('=')
            val len = clean.length
            val out = ByteArrayOutputStream((len * 3) / 4)
            var bits = 0
            var bitCount = 0
            for (ch in clean) {
                val code = ch.code
                val v = if (code in 0 until 128) INVERSE[code] else -1
                if (v == -1) continue
                bits = (bits shl 6) or v
                bitCount += 6
                if (bitCount >= 8) {
                    bitCount -= 8
                    out.write((bits ushr bitCount) and 0xFF)
                }
            }
            return out.toByteArray()
        }
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Hanime Filters"),
            HanimeSortFilter(),
            HanimeGenreFilter()
        )
    }

    class HanimeSortFilter : AnimeFilter.Select<String>(
        "Sort By",
        arrayOf("Trending", "Latest Releases"),
        0
    )

    class HanimeGenreFilter : AnimeFilter.Select<String>(
        "Genre",
        GENRES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = GENRES[state].slug
    }
}
