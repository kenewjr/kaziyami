package app.kaziyami.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

/**
 * Declarative configuration for an anime extension source.
 * Encapsulates metadata, URL routing templates, CSS selectors, and behavior quirks.
 */
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

        fun fromJson(jsonString: String): SourceConfig = SourceConfigLoader.loadFromString(jsonString)
    }

    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
}

/**
 * CSS selectors used across page parsing steps.
 */
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

/**
 * Behavioral quirk flags for edge-case engine handling.
 */
@Serializable
data class QuirksConfig(
    val episodesReversed: Boolean = false,
    val usesAjaxPlayer: Boolean = false,
    val ajaxPlayerEndpoint: String = "/wp-admin/admin-ajax.php",
    val dateFormat: String = "yyyy-MM-dd",
    val customReferer: String? = null,
    val dateLocale: String = "id",
    val stripTitleBoilerplate: Boolean = false,
    val needsJs: Boolean = false
)

/**
 * Loads and validates [SourceConfig] instances from JSON strings, streams, or files.
 */
object SourceConfigLoader {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Loads and validates a [SourceConfig] from a JSON string.
     */
    fun loadFromString(jsonString: String): SourceConfig {
        require(jsonString.isNotBlank()) { "Source configuration JSON cannot be empty" }
        val config = json.decodeFromString(SourceConfig.serializer(), jsonString)
        validate(config)
        return config
    }

    /**
     * Loads and validates a [SourceConfig] from an [InputStream].
     */
    fun loadFromStream(stream: InputStream): SourceConfig {
        val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return loadFromString(content)
    }

    /**
     * Loads and validates a [SourceConfig] from a [File].
     */
    fun loadFromFile(file: File): SourceConfig {
        require(file.exists()) { "Source configuration file does not exist: ${file.absolutePath}" }
        return loadFromString(file.readText(Charsets.UTF_8))
    }

    /**
     * Enforces schema constraints on [SourceConfig] instances.
     */
    fun validate(config: SourceConfig) {
        require(config.name.isNotBlank()) { "Validation failed: 'name' must not be blank" }
        require(config.lang.length in 2..5) { "Validation failed: 'lang' must be a valid language code (2-5 chars), got: '${config.lang}'" }
        require(config.baseUrl.startsWith("http://") || config.baseUrl.startsWith("https://")) {
            "Validation failed: 'baseUrl' must start with http:// or https://, got: '${config.baseUrl}'"
        }
        require(config.selectors.item.isNotBlank()) { "Validation failed: 'selectors.item' selector must not be blank" }
        require(config.selectors.itemTitle.isNotBlank()) { "Validation failed: 'selectors.itemTitle' selector must not be blank" }
        require(config.selectors.itemUrl.isNotBlank()) { "Validation failed: 'selectors.itemUrl' selector must not be blank" }
    }
}
