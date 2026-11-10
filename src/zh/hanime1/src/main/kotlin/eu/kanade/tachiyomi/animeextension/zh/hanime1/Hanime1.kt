package eu.kanade.tachiyomi.animeextension.zh.hanime1

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
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
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import java.util.regex.Pattern

class Hanime1 : GenericThemeSource(
    config = SourceConfig(
        name = "Hanime1",
        lang = "zh",
        baseUrl = "https://hanime1.me",
        mirrors = listOf("https://hanime1.me"),
        popularPath = "/search?sort=本日排行&page=%d",
        latestPath = "/search?sort=最新上市&page=%d",
        searchPath = "/search?query=%s&page=%d",
        selectors = SelectorsConfig(
            item = ".search-result-items > div, div[class^=video-item-container], div.home-rows-videos-div, .card",
            itemTitle = ".home-rows-videos-title, .video-item-title, h4, h5, .caption, a[title]",
            itemUrl = "a[href*='watch?v=']",
            itemThumbnail = "img[src], img[data-src]",
            nextPage = ".pagination a.next, a.next, a[rel=next]",
            detailsTitle = "#shareBtn-title, h1, .video-details-wrapper h4",
            detailsThumbnail = "meta[property='og:image'], img.video-cover",
            detailsDescription = "div[class=video-details-wrapper] .video-caption-text, meta[name=description], .description",
            detailsGenre = "div.video-tags-wrapper a, a[href*='search?genre='], a[href*='search?tag=']",
            detailsAuthor = "div.video-details-wrapper a[href*='search?brand='], #video-artist-name",
            episodeList = ".related-watch-wrap a[href*='watch?v='], .video-playlist-wrapper a[href*='watch?v=']",
            playerIframes = "video#player source[src], video#player, iframe[src]"
        ),
        quirks = QuirksConfig(
            customReferer = "https://hanime1.me/"
        )
    )
) {

    public override fun popularAnimeRequest(page: Int): Request {
        checkAgeConfirmed()
        val path = if (page > 1) "/search?sort=本日排行&page=$page" else "/search?sort=本日排行"
        return GET(UrlUtils.resolve(baseUrl, path), headers)
    }

    public override fun latestUpdatesRequest(page: Int): Request {
        checkAgeConfirmed()
        val path = if (page > 1) "/search?sort=最新上市&page=$page" else "/search?sort=最新上市"
        return GET(UrlUtils.resolve(baseUrl, path), headers)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        checkAgeConfirmed()
        val cleanQuery = query.trim()
        val sort = filters.filterIsInstance<Hanime1SortFilter>().firstOrNull()?.toSlug().orEmpty()
        val genre = filters.filterIsInstance<Hanime1GenreFilter>().firstOrNull()?.toSlug().orEmpty()
        val duration = filters.filterIsInstance<Hanime1DurationFilter>().firstOrNull()?.toSlug().orEmpty()
        val tag = filters.filterIsInstance<Hanime1TagFilter>().firstOrNull()?.toSlug().orEmpty()
        val brand = filters.filterIsInstance<Hanime1BrandFilter>().firstOrNull()?.toSlug().orEmpty()

        val params = mutableListOf<String>()
        if (cleanQuery.isNotEmpty()) {
            params.add("query=" + URLEncoder.encode(cleanQuery, "UTF-8"))
        }
        if (genre.isNotEmpty()) {
            params.add("genre=" + URLEncoder.encode(genre, "UTF-8"))
        }
        if (sort.isNotEmpty()) {
            params.add("sort=" + URLEncoder.encode(sort, "UTF-8"))
        }
        if (duration.isNotEmpty()) {
            params.add("duration=" + URLEncoder.encode(duration, "UTF-8"))
        }
        if (tag.isNotEmpty()) {
            params.add("tag=" + URLEncoder.encode(tag, "UTF-8"))
        }
        if (brand.isNotEmpty()) {
            params.add("brand=" + URLEncoder.encode(brand, "UTF-8"))
        }
        if (page > 1) {
            params.add("page=$page")
        }

        val path = if (params.isNotEmpty()) {
            "/search?" + params.joinToString("&")
        } else {
            if (page > 1) "/search?sort=最新上市&page=$page" else "/search?sort=最新上市"
        }

        val url = UrlUtils.resolve(baseUrl, path)
        return GET(url, headers)
    }

    public override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val relatedLinks = document.select(".related-watch-wrap a[href*='watch?v='], .video-playlist-wrapper a[href*='watch?v=']")
        if (relatedLinks.isNotEmpty()) {
            return relatedLinks.mapIndexed { index, el ->
                val ep = SEpisode.create()
                val href = el.attr("href")
                ep.url = UrlUtils.resolve(baseUrl, href)
                ep.name = HtmlText.clean(el.text()).ifEmpty { "Episode ${index + 1}" }
                ep.episode_number = (index + 1).toFloat()
                ep
            }
        }

        // Single video fallback
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

        // 1. Direct <video id="player"> or <source> elements
        val doc = org.jsoup.Jsoup.parse(unescapedBody, baseUrl)
        val sources = doc.select("video#player source[src], video[src] source[src], video#player, video[src]")
        for (srcEl in sources) {
            val src = srcEl.attr("src").trim()
            if (src.isNotBlank()) {
                val fullUrl = UrlUtils.resolve(baseUrl, src)
                val quality = srcEl.attr("size").takeIf { it.isNotBlank() }?.let { "${it}p" } ?: "Hanime1 720p/1080p"
                videoList.add(Video(fullUrl, quality, fullUrl, headers))
            }
        }

        // 2. JavaScript `const source = '...'` or `var source = '...'`
        val jsSourceMatcher = Pattern.compile("""(?:const|var|let)?\s*source\s*=\s*['"](https?://[^'"]+)['"]""", Pattern.CASE_INSENSITIVE).matcher(unescapedBody)
        while (jsSourceMatcher.find()) {
            val streamUrl = jsSourceMatcher.group(1) ?: continue
            val label = if (streamUrl.contains(".m3u8")) "Hanime1 HLS (1080p)" else "Hanime1 Direct MP4"
            videoList.add(Video(streamUrl, label, streamUrl, headers))
        }

        // 3. Embedded M3U8 regex
        val m3u8Matcher = Pattern.compile("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE).matcher(unescapedBody)
        while (m3u8Matcher.find()) {
            val streamUrl = m3u8Matcher.group(1) ?: continue
            videoList.add(Video(streamUrl, "Hanime1 HLS (1080p)", streamUrl, headers))
        }

        // 4. Embedded MP4 regex
        val mp4Matcher = Pattern.compile("""(https?://[^\s"'<>]+\.mp4[^\s"'<>]*)""", Pattern.CASE_INSENSITIVE).matcher(unescapedBody)
        while (mp4Matcher.find()) {
            val streamUrl = mp4Matcher.group(1) ?: continue
            videoList.add(Video(streamUrl, "Hanime1 Direct MP4", streamUrl, headers))
        }

        // 5. Extractor registry iframes
        val iframes = doc.select("iframe[src]").mapNotNull {
            val src = it.attr("src")
            if (src.isNotBlank()) UrlUtils.resolve(baseUrl, src) else null
        }
        videoList.addAll(ExtractorRegistry.extractAll(iframes, client))

        val deduped = videoList.distinctBy { it.videoUrl }
        if (deduped.isEmpty()) {
            val epUrl = response.request.url.toString()
            return listOf(
                Video(
                    url = epUrl,
                    quality = "Stream (Open in WebView if blocked)",
                    videoUrl = epUrl,
                    headers = headers
                )
            )
        }
        return deduped
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Hanime1 繁體中文過濾器"),
            Hanime1SortFilter(),
            Hanime1GenreFilter(),
            Hanime1DurationFilter(),
            Hanime1TagFilter(),
            Hanime1BrandFilter()
        )
    }

    class Hanime1SortFilter : AnimeFilter.Select<String>(
        "排序 (Sort By)",
        SORTS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = SORTS[state].slug
    }

    class Hanime1GenreFilter : AnimeFilter.Select<String>(
        "類型 (Genre)",
        GENRES.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = GENRES[state].slug
    }

    class Hanime1DurationFilter : AnimeFilter.Select<String>(
        "時長 (Duration)",
        DURATIONS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = DURATIONS[state].slug
    }

    class Hanime1TagFilter : AnimeFilter.Select<String>(
        "標籤 (Tag)",
        TAGS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = TAGS[state].slug
    }

    class Hanime1BrandFilter : AnimeFilter.Select<String>(
        "製作廠商 (Studio / Brand)",
        BRANDS.map { it.name }.toTypedArray(),
        0
    ) {
        fun toSlug(): String = BRANDS[state].slug
    }

    companion object {
        data class Option(val name: String, val slug: String)

        val SORTS = arrayOf(
            Option("最新上市 (New Arrival)", "最新上市"),
            Option("最新上傳 (New Upload)", "最新上傳"),
            Option("本日排行 (Daily Ranking)", "本日排行"),
            Option("本週排行 (Weekly Ranking)", "本週排行"),
            Option("本月排行 (Monthly Ranking)", "本月排行"),
            Option("觀看次數 (View Count)", "觀看次數"),
            Option("讚好比例 (Like Ratio)", "讚好比例"),
            Option("他們在看 (Trending)", "他們在看"),
            Option("時長最長 (Longest)", "時長最長")
        )

        val GENRES = arrayOf(
            Option("全部類型 (All Genres)", ""),
            Option("裏番 (Hentai)", "裏番"),
            Option("泡麵番 (Short Anime)", "泡麵番"),
            Option("Motion Anime", "Motion Anime"),
            Option("3D動畫 (3D Animation / 3DCG)", "3D動畫"),
            Option("同人作品 (Doujin Work)", "同人作品"),
            Option("2.5D", "2.5D"),
            Option("2D動畫", "2D動畫"),
            Option("AI生成 (AI Generated)", "AI生成"),
            Option("MMD", "MMD"),
            Option("Cosplay", "Cosplay")
        )

        val DURATIONS = arrayOf(
            Option("全部時長 (All Durations)", ""),
            Option("短片 (Short <= 4 min)", "短片"),
            Option("中長片 (Medium 4-20 min)", "中長片"),
            Option("長片 (Feature > 20 min)", "長片")
        )

        val TAGS = arrayOf(
            Option("全部標籤 (All Tags)", ""),
            // 規格 (Specs)
            Option("無碼 (Uncensored)", "無碼"),
            Option("AI解碼 (AI Decoded)", "AI解碼"),
            Option("中文字幕 (Chinese Sub)", "中文字幕"),
            Option("中文配音 (Chinese Dub)", "中文配音"),
            Option("1080P", "1080P"),
            Option("60FPS", "60FPS"),
            // 劇情主題 (Themes)
            Option("純愛 (Pure Love)", "純愛"),
            Option("戀愛喜劇 (Romcom)", "戀愛喜劇"),
            Option("後宮 (Harem)", "後宮"),
            Option("異世界 (Isekai)", "異世界"),
            Option("催眠 / 精神控制 (Hypnosis)", "催眠"),
            Option("NTR (Netorare)", "NTR"),
            Option("出軌 (Affair)", "出軌"),
            Option("公眾場合 (Public)", "公眾場合"),
            Option("百合 (Yuri)", "百合"),
            Option("耽美 (Yaoi)", "耽美"),
            Option("性轉換 (Gender Bender)", "性轉換"),
            // 角色關係 (Relationships)
            Option("近親 (Incest)", "近親"),
            Option("姐 (Elder Sister)", "姐"),
            Option("妹 (Younger Sister)", "妹"),
            Option("母 (Mother)", "母"),
            Option("女兒 (Daughter)", "女兒"),
            Option("師生 (Teacher Student)", "師生"),
            Option("情侶 (Couple)", "情侶"),
            Option("青梅竹馬 (Childhood Friend)", "青梅竹馬"),
            // 角色身分 (Roles)
            Option("處女 (Virgin)", "處女"),
            Option("御姐 (Older Sister)", "御姐"),
            Option("熟女 (MILF)", "熟女"),
            Option("人妻 (Married Woman)", "人妻"),
            Option("老師 (Teacher)", "老師"),
            Option("女醫護士 (Nurse / Doctor)", "女醫護士"),
            Option("OL (Office Lady)", "OL"),
            Option("大小姐 (Rich Lady)", "大小姐"),
            Option("偶像 (Idol)", "偶像"),
            Option("女僕 (Maid)", "女僕"),
            Option("巫女 (Shrine Maiden)", "巫女"),
            Option("修女 (Nun)", "修女"),
            Option("風俗娘 (Hostess)", "風俗娘"),
            Option("公主 (Princess)", "公主"),
            Option("女戰士 (Female Warrior)", "女戰士"),
            Option("魔法少女 (Magical Girl)", "魔法少女"),
            Option("異種族 (Other Race)", "異種族"),
            Option("妖精 (Elf)", "妖精"),
            Option("魔物娘 (Monster Girl)", "魔物娘"),
            Option("獸娘 (Beast Girl)", "獸娘"),
            Option("碧池 (Bitch)", "碧池"),
            Option("痴女 (Perverted Woman)", "痴女"),
            Option("不良少女 (Delinquent Girl)", "不良少女"),
            Option("傲嬌 (Tsundere)", "傲嬌"),
            Option("病嬌 (Yandere)", "病嬌"),
            Option("無口 (Dandere)", "無口"),
            Option("偽娘 (Trap / Crossdresser)", "偽娘"),
            Option("扶他 (Futanari)", "扶他"),
            // 外貌與服裝 (Appearance & Costumes)
            Option("巨乳 (Big Breasts)", "巨乳"),
            Option("貧乳 (Flat / Small Breasts)", "貧乳"),
            Option("黑皮膚 (Dark Skin)", "黑皮膚"),
            Option("眼鏡娘 (Glasses)", "眼鏡娘"),
            Option("獸耳 (Animal Ears)", "獸耳"),
            Option("短髮 (Short Hair)", "短髮"),
            Option("馬尾 (Ponytail)", "馬尾"),
            Option("雙馬尾 (Twin Tail)", "雙馬尾"),
            Option("肌肉女 (Muscular Woman)", "肌肉女"),
            Option("水手服 (Sailor Suit)", "水手服"),
            Option("體操服 (Gym Clothes)", "體操服"),
            Option("泳裝 (Swimsuit)", "泳裝"),
            Option("比基尼 (Bikini)", "比基尼"),
            Option("和服 (Kimono)", "和服"),
            Option("兔女郎 (Bunny Girl)", "兔女郎"),
            Option("圍裙 (Apron)", "圍裙"),
            Option("啦啦隊 (Cheerleader)", "啦啦隊"),
            Option("旗袍 (Cheongsam)", "旗袍"),
            Option("絲襪 (Stockings)", "絲襪"),
            Option("黑絲 (Black Stockings)", "黑絲"),
            Option("吊襪帶 (Garter Belt)", "吊襪帶"),
            Option("熱褲 (Hot Pants)", "熱褲"),
            Option("迷你裙 (Mini Skirt)", "迷你裙"),
            Option("性感內衣 (Lingerie)", "性感內衣"),
            Option("丁字褲 (Thong)", "丁字褲"),
            Option("高跟鞋 (High Heels)", "高跟鞋"),
            Option("淫紋 (Crest Tattoo)", "淫紋"),
            // 體位與情節 (Acts & Positions)
            Option("內射 (Creampie)", "內射"),
            Option("口交 (Blowjob)", "口交"),
            Option("口爆 (Facial)", "口爆"),
            Option("顏射 (Facial)", "顏射"),
            Option("乳交 (Titfuck)", "乳交"),
            Option("手交 (Handjob)", "手交"),
            Option("腳交 (Footjob)", "腳交"),
            Option("指交 (Fingering)", "指交"),
            Option("肛交 (Anal)", "肛交"),
            Option("3P (Threesome)", "3P"),
            Option("群交 (Orgy)", "群交"),
            Option("雙洞齊下 (Double Penetration)", "雙洞齊下"),
            Option("懷孕 (Pregnancy)", "懷孕"),
            Option("噴奶 (Lactation)", "噴奶"),
            Option("自慰 (Masturbation)", "自慰"),
            Option("顏面騎乘 (Facesitting)", "顏面騎乘"),
            Option("車震 (Car Sex)", "車震"),
            Option("後背位 (Doggystyle)", "後背位"),
            Option("性玩具 (Sex Toys)", "性玩具"),
            Option("觸手 (Tentacles)", "觸手"),
            Option("BDSM", "BDSM"),
            Option("綑綁 (Bondage)", "綑綁"),
            Option("調教 (Training)", "調教"),
            Option("女王樣 (Femdom)", "女王樣")
        )

        val BRANDS = arrayOf(
            Option("全部廠商 (All Studios)", ""),
            Option("PoRO", "PoRO"),
            Option("Queen Bee", "Queen Bee"),
            Option("メリー・ジェーン (Mary Jane)", "メリー・ジェーン"),
            Option("ピンクパイナップル (Pink Pineapple)", "ピンクパイナップル"),
            Option("ばにぃうぉ～か～ (Bunnywalker)", "ばにぃうぉ～か～"),
            Option("魔人 (Majin)", "魔人"),
            Option("milky (ミルキー)", "milky"),
            Option("Seven (セブン)", "Seven"),
            Option("T-Rex", "T-Rex"),
            Option("ZIZ", "ZIZ"),
            Option("EDGE", "EDGE"),
            Option("ショーテン (Showten)", "ショーテン"),
            Option("妄想実現めでぃあ", "妄想実現めでぃあ"),
            Option("せるふぃっしゅ (Selfish)", "せるふぃっしゅ"),
            Option("Collaboration Works", "Collaboration Works"),
            Option("BOOTLEG", "BOOTLEG"),
            Option("BOMB!CUTE!BOMB!", "BOMB!CUTE!BOMB!"),
            Option("GOLD BEAR", "GOLD BEAR"),
            Option("鈴木みら乃 (Suzuki Mirano)", "鈴木みら乃")
        )
    }
}
