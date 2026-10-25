package eu.kanade.tachiyomi.multisrc.generic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SourceConfig(
    val name: String,
    val lang: String,
    val baseUrl: String,
    val mirrors: List<String> = emptyList(),
    val isNsfw: Boolean = true,
    val popularPath: String = "/popular/page/%d/",
    val latestPath: String = "/latest/page/%d/",
    val searchPath: String = "/?s=%s&page=%d",
    val selectors: SelectorsConfig = SelectorsConfig(),
    val quirks: QuirksConfig = QuirksConfig()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
            encodeDefaults = true
        }

        fun fromJson(jsonString: String): SourceConfig {
            return json.decodeFromString(serializer(), jsonString)
        }
    }

    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
}

@Serializable
data class SelectorsConfig(
    val item: String = "article",
    val itemTitle: String = "h2, .title, a[title]",
    val itemUrl: String = "a[href]",
    val itemThumbnail: String = "img",
    val nextPage: String = ".pagination .next, a.next, .nav-links .next",
    val detailsTitle: String = "h1.entry-title, .data h1, .infox h1, h1",
    val detailsThumbnail: String = ".poster img, .thumb img, .entry-content img, .cover img",
    val detailsDescription: String = ".entry-content p, .synopsis, .desc, .entry-content",
    val detailsGenre: String = ".genres a, .genre a, a[rel=category], .tag-links a",
    val detailsStatus: String = ".status, span:contains(Status)",
    val detailsAuthor: String = ".author, .studio",
    val episodeList: String = ".episodelist ul li, ul.episodes li, .eplister ul li, .episode-list li",
    val episodeUrl: String = "a[href]",
    val episodeName: String = ".epl-num, .ep-title, a",
    val episodeDate: String = ".epl-date, .date, .time",
    val playerIframes: String = "iframe[src], .player-embed iframe, .player iframe"
)

@Serializable
data class QuirksConfig(
    val episodesReversed: Boolean = false,
    val usesAjaxPlayer: Boolean = false,
    val ajaxPlayerEndpoint: String = "/wp-admin/admin-ajax.php",
    val dateFormat: String = "yyyy-MM-dd",
    val customReferer: String? = null
)
