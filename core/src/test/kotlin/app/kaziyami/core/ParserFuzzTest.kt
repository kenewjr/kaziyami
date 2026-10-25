package app.kaziyami.core

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class ParserFuzzTest {

    private val random = Random(42)

    private fun generateRandomString(maxLen: Int): String {
        val len = random.nextInt(maxLen)
        val sb = StringBuilder(len)
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 \t\r\n!@#$%^&*()_+-=[]{}|;':\",./<>?~`\u0000\u0007\u001F\uFEFF\uD83D\uDE00\u4E16\u754C\u0627\u0644\u0639\u0631\u0628\u064A\u0629"
        for (i in 0 until len) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    @Test
    fun fuzzEpisodeNumberParser() {
        for (i in 0 until 500) {
            val input = generateRandomString(200)
            val result1 = EpisodeNumberParser.parse(input)
            val result2 = EpisodeNumberParser.parse(input)
            // Deterministic
            assertEquals(result1, result2)
            assertTrue(result1 >= -1f)
        }
    }

    @Test
    fun fuzzDateParser() {
        for (i in 0 until 500) {
            val input = generateRandomString(200)
            val result1 = DateParser.parse(input)
            val result2 = DateParser.parse(input)
            // Deterministic and no crash
            assertEquals(result1, result2)
            assertTrue(result1 >= 0L)
        }
    }

    @Test
    fun fuzzHtmlTextIdempotence() {
        for (i in 0 until 500) {
            val input = generateRandomString(300)
            val pass1 = HtmlText.clean(input)
            val pass2 = HtmlText.clean(pass1)
            // Idempotent: clean(clean(x)) == clean(x)
            assertEquals(pass1, pass2)
        }
    }

    @Test
    fun fuzzUrlUtilsNormalization() {
        for (i in 0 until 500) {
            val input = generateRandomString(250)
            val path = UrlUtils.getPath(input)
            assertNotNull(path)
            assertTrue(path.startsWith("/"))

            val normalized1 = UrlUtils.normalizeStoredPath(input)
            val normalized2 = UrlUtils.normalizeStoredPath(normalized1)
            // Idempotent
            assertEquals(normalized1, normalized2)
        }
    }

    @Test
    fun fuzzLazyImageExtractor() {
        val baseUrl = "https://nekopoi.care"
        for (i in 0 until 300) {
            val src = generateRandomString(150)
            val dataSrc = generateRandomString(150)
            val html = "<img src=\"$src\" data-src=\"$dataSrc\" alt=\"Fuzz\">"
            val el = Jsoup.parseBodyFragment(html).selectFirst("img")
            val url = LazyImage.extractUrl(el, baseUrl)
            assertNotNull(url)
        }
    }

    @Test
    fun hugeInputDoesNotOom() {
        val hugeString = "Episode 42 ".repeat(50_000) // ~550KB
        val ep = EpisodeNumberParser.parse(hugeString)
        assertEquals(42f, ep)

        val cleaned = HtmlText.clean(hugeString)
        assertTrue(cleaned.isNotEmpty())
    }
}
