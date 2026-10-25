package app.kaziyami.multisrc.generic

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SourceContractTest {

    companion object {
        @JvmStatic
        fun sourceProvider(): Stream<GenericThemeSource> {
            val generic = GenericThemeSource(
                SourceConfig(
                    name = "TestGeneric",
                    lang = "en",
                    baseUrl = "https://example.com",
                    mirrors = listOf("https://m1.example.com", "https://m2.example.com"),
                    selectors = SelectorsConfig(
                        item = "article.card",
                        itemTitle = ".title",
                        itemUrl = "a[href]",
                        itemThumbnail = "img",
                        episodeList = "ul.eps li",
                        episodeUrl = "a[href]",
                        episodeName = ".num"
                    )
                )
            )

            val custom = GenericThemeSource(
                SourceConfig(
                    name = "TestCustom",
                    lang = "id",
                    baseUrl = "https://custom.org",
                    selectors = SelectorsConfig(
                        item = "div.box",
                        itemTitle = "h3",
                        itemUrl = "a",
                        itemThumbnail = "img",
                        episodeList = "div.ep-row",
                        episodeUrl = "a",
                        episodeName = "span.ep-name"
                    )
                )
            )

            return Stream.of(generic, custom)
        }
    }

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

    @ParameterizedTest
    @MethodSource("sourceProvider")
    fun contractNoBlankTitlesAndAbsoluteUrls(source: GenericThemeSource) {
        val html = """
            <html>
            <body>
                <article class="card"><a href="/anime/one"><span class="title">Anime One</span><img data-src="/img/one.jpg" /></a></article>
                <article class="card"><a href="/anime/two"><span class="title">Anime Two &amp; Special</span><img src="https://cdn.example.com/two.jpg" /></a></article>
                <div class="box"><a href="/anime/one"><h3>Anime One</h3><img data-lazy-src="/img/one.jpg" /></a></div>
                <div class="box"><a href="/anime/two"><h3>Anime Two</h3><img src="/img/two.jpg" /></a></div>
            </body>
            </html>
        """.trimIndent()

        val response = mockResponse(source.baseUrl, html)
        val page = source.popularAnimeParse(response)

        assertTrue(page.animes.isNotEmpty(), "Catalog should not be empty")

        val seenUrls = mutableSetOf<String>()
        for (anime in page.animes) {
            assertTrue(anime.title.isNotBlank(), "Title must not be blank")
            assertFalse(anime.title.contains("&amp;"), "HTML entities should be decoded")
            assertTrue(anime.url.startsWith("/") || !anime.url.contains("://"), "URL must be stored relative for library safety: ${anime.url}")
            assertTrue(anime.thumbnail_url?.startsWith("http://") == true || anime.thumbnail_url?.startsWith("https://") == true, "Thumbnail must be absolute")
            assertFalse(seenUrls.contains(anime.url), "Must not produce duplicate anime URLs: ${anime.url}")
            seenUrls.add(anime.url)
        }
    }

    @ParameterizedTest
    @MethodSource("sourceProvider")
    fun contractEpisodesMonotonicAndAbsolute(source: GenericThemeSource) {
        val html = """
            <html>
            <body>
                <ul class="eps">
                    <li><a href="/ep-3"><span class="num">Episode 3</span></a></li>
                    <li><a href="/ep-2"><span class="num">Episode 2</span></a></li>
                    <li><a href="/ep-1"><span class="num">Episode 1</span></a></li>
                </ul>
                <div class="ep-row"><a href="/ep-3"><span class="ep-name">Episode 3</span></a></div>
                <div class="ep-row"><a href="/ep-2"><span class="ep-name">Episode 2</span></a></div>
                <div class="ep-row"><a href="/ep-1"><span class="ep-name">Episode 1</span></a></div>
            </body>
            </html>
        """.trimIndent()

        val response = mockResponse(source.baseUrl, html)
        val episodes = source.episodeListParse(response)

        assertTrue(episodes.isNotEmpty(), "Episode list should not be empty")

        for (ep in episodes) {
            assertTrue(ep.name.isNotBlank(), "Episode name must not be blank")
            assertTrue(ep.url.startsWith("/") || !ep.url.contains("://"), "Episode URL must be stored relative for library safety: ${ep.url}")
            assertTrue(ep.episode_number >= 0f, "Episode number must be non-negative")
        }

        // Validate newest first order
        for (i in 0 until episodes.size - 1) {
            assertTrue(
                episodes[i].episode_number >= episodes[i + 1].episode_number,
                "Episodes must be ordered newest-first: ${episodes[i].episode_number} vs ${episodes[i + 1].episode_number}"
            )
        }
    }
}
