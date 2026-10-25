package app.kaziyami.multisrc.generic

import app.kaziyami.core.DateParser
import app.kaziyami.core.EpisodeNumberParser
import app.kaziyami.core.HtmlText
import app.kaziyami.core.UrlUtils
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NegativeEdgeTest {

    private val source = GenericThemeSource(
        SourceConfig(
            name = "EdgeSource",
            lang = "en",
            baseUrl = "https://edge.example.com",
            selectors = SelectorsConfig(
                item = "article.entry",
                itemTitle = "h2",
                itemUrl = "a[href]",
                itemThumbnail = "img",
                detailsTitle = "h1",
                episodeList = "ul.eps li",
                episodeUrl = "a",
                episodeName = "span"
            )
        )
    )

    private fun mockResponse(html: String): Response {
        val request = Request.Builder().url(source.baseUrl).build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(html.toResponseBody())
            .build()
    }

    @Test
    fun malformedHtmlParsesSafely() {
        val brokenHtml = "<div><article class='entry'><h2>Broken Title<a href='/watch/1'><img src='/thumb.jpg'></div>"
        val response = mockResponse(brokenHtml)
        val page = source.popularAnimeParse(response)

        assertEquals(1, page.animes.size)
        assertEquals("Broken Title", page.animes[0].title)
        assertEquals("https://edge.example.com/watch/1", page.animes[0].url)
    }

    @Test
    fun completelyEmptyHtmlProducesEmptyResults() {
        val response = mockResponse("")
        val page = source.popularAnimeParse(response)

        assertTrue(page.animes.isEmpty())
        assertFalse(page.hasNextPage)

        val episodes = source.episodeListParse(response)
        assertTrue(episodes.isEmpty())
    }

    @Test
    fun unicodeAndHtmlEntitiesInTitles() {
        val raw = "異世界 &amp; 魔王 &#039;Super &quot;HD&quot;&#039;"
        val cleaned = HtmlText.clean(raw)
        assertEquals("異世界 & 魔王 'Super \"HD\"'", cleaned)
    }

    @Test
    fun urlUtilsHandlesTrailingSlashesAndQueries() {
        val base = "https://example.com/anime///"
        val resolved = UrlUtils.resolve(base, "/ep/1?src=test&utm_source=track")
        assertEquals("https://example.com/ep/1?src=test&utm_source=track", resolved)

        val stripped = UrlUtils.stripTrackingParams(resolved)
        assertEquals("https://example.com/ep/1?src=test", stripped)
    }

    @Test
    fun dateParserEdgeCases() {
        assertEquals(0L, DateParser.parse(""))
        assertEquals(0L, DateParser.parse("unknown date"))
        assertEquals(0L, DateParser.parse(null))
    }

    @Test
    fun episodeNumberEdgeCases() {
        assertEquals(0.0f, EpisodeNumberParser.parse("Non Episode Video"))
        assertEquals(1.0f, EpisodeNumberParser.parse("Special OVA"))
        assertEquals(2.5f, EpisodeNumberParser.parse("Episode 2.5"))
    }

    @Test
    fun sourceConfigJsonSerializationRoundTrip() {
        val config = SourceConfig(
            name = "JsonSource",
            lang = "id",
            baseUrl = "https://json.example.com",
            mirrors = listOf("https://m1.json.com"),
            selectors = SelectorsConfig(
                item = ".custom-item",
                itemTitle = ".custom-title"
            )
        )

        val jsonStr = config.toJson()
        val parsed = SourceConfig.fromJson(jsonStr)

        assertEquals(config.name, parsed.name)
        assertEquals(config.lang, parsed.lang)
        assertEquals(config.baseUrl, parsed.baseUrl)
        assertEquals(config.selectors.item, parsed.selectors.item)
        assertEquals(config.selectors.itemTitle, parsed.selectors.itemTitle)
    }
}
