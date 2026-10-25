package app.kaziyami.multisrc.generic

import app.kaziyami.core.SourceParseException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelectorDriftTest {

    private val baseConfig = SourceConfig(
        name = "TestDriftSource",
        lang = "en",
        baseUrl = "https://example.com",
        selectors = SelectorsConfig(
            item = "article.entry",
            itemAlternates = listOf(".custom-card", ".anime-box"),
            episodeList = "ul.episodes li",
            episodeListAlternates = listOf(".ep-list a", ".item-episode")
        )
    )

    private val source = GenericThemeSource(baseConfig)

    private fun mockResponse(url: String, html: String): Response {
        val request = Request.Builder().url(url).build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(html.toResponseBody())
            .build()
    }

    @Test
    fun primarySelectorFailsAlternateSucceeds() {
        val html = """
            <html>
              <body>
                <div class="custom-card">
                  <a href="/anime/test" title="Alternate Anime">
                    <img src="/thumb.jpg"/>
                  </a>
                </div>
              </body>
            </html>
        """.trimIndent()

        val response = mockResponse("https://example.com/popular/page/1/", html)
        val page = source.popularAnimeParse(response)

        assertEquals(1, page.animes.size)
        assertEquals("Alternate Anime", page.animes[0].title)
        assertEquals("/anime/test", page.animes[0].url)
    }

    @Test
    fun allSelectorsFailThrowsStructuredSourceParseException() {
        val html = """
            <html>
              <body>
                <div class="completely-different-layout">
                  <p>Welcome to our new platform</p>
                </div>
              </body>
            </html>
        """.trimIndent()

        val response = mockResponse("https://example.com/popular/page/1/", html)
        val ex = assertThrows(SourceParseException::class.java) {
            source.popularAnimeParse(response)
        }

        assertEquals("TestDriftSource", ex.source)
        assertEquals("popular anime list", ex.pageType)
        assertEquals("article.entry", ex.selectorName)
        assertEquals("/popular/page/1/", ex.urlPath)
        assertTrue(ex.message!!.contains("Kaziyami: TestDriftSource could not read the popular anime list; the site layout may have changed"))
    }

    @Test
    fun emptyStatePageReturnsEmptyListWithoutError() {
        val html = """
            <html>
              <body>
                <div class="not-found">
                  <p>Tidak ada hasil ditemukan. Nothing found.</p>
                </div>
              </body>
            </html>
        """.trimIndent()

        val response = mockResponse("https://example.com/?s=unknown", html)
        val page = source.searchAnimeParse(response)

        assertTrue(page.animes.isEmpty())
        assertFalse(page.hasNextPage)
    }
}
