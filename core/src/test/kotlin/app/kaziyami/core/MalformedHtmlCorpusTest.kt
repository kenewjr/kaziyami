package app.kaziyami.core

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MalformedHtmlCorpusTest {

    @Test
    fun testTruncatedHtmlSurvives() {
        val truncated = """
            <!DOCTYPE html>
            <html>
              <head><title>Truncated
              <body>
                <div class="container">
                  <article class="post">
                    <h2><a href="/anime/ep-1
        """.trimIndent()

        assertDoesNotThrow {
            val doc = Jsoup.parse(truncated)
            assertNotNull(doc.body())
        }
    }

    @Test
    fun testBomAndMixedEncodings() {
        // UTF-8 with BOM character + ISO-8859 high-bytes
        val bomHtml = "\uFEFF<!DOCTYPE html><html><body><h1>T\u00E9l\u00E9vision \uD83D\uDD25</h1></body></html>"
        val doc = Jsoup.parse(bomHtml)
        val text = doc.selectFirst("h1")?.text()
        assertNotNull(text)
    }

    @Test
    fun testUnclosedTagsAndBrokenAttributes() {
        val broken = """
            <table>
              <tr><td><div><span>Unclosed everything
              <a href="https://example.com/test" title='mismatched quotes" <img src=unquoted>
              <script>var x = "<div unclosed in js>";
        """.trimIndent()

        assertDoesNotThrow {
            val doc = Jsoup.parse(broken)
            assertNotNull(doc.body())
        }
    }

    @Test
    fun testDeeplyNestedDomElements() {
        val depth = 1000
        val sb = StringBuilder()
        for (i in 0 until depth) sb.append("<div>")
        sb.append("<p id='deep'>Deep Content</p>")
        for (i in 0 until depth) sb.append("</div>")

        assertDoesNotThrow {
            val doc = Jsoup.parse(sb.toString())
            val deep = doc.selectFirst("#deep")
            assertNotNull(deep)
        }
    }

    @Test
    fun testSimulatedCompressedBinaryGarbage() {
        // Pseudo-gzip or brotli raw bytes fed directly as string
        val garbage = (0..500).map { (it % 256).toChar() }.joinToString("")
        assertDoesNotThrow {
            val doc = Jsoup.parse(garbage)
            assertNotNull(doc)
        }
    }
}
