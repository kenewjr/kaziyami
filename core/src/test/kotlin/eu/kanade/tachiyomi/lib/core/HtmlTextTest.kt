package eu.kanade.tachiyomi.lib.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HtmlTextTest {

    @Test
    fun cleanHtmlEntities() {
        val input = "Episode &amp; Specials &quot;HD&quot; &#39;Sub&#39;"
        assertEquals("Episode & Specials \"HD\" 'Sub'", HtmlText.clean(input))
    }

    @Test
    fun stripHtmlTags() {
        val input = "<p>This is a <b>bold</b> description with <a href=\"#\">link</a>.</p>"
        assertEquals("This is a bold description with link.", HtmlText.clean(input))
    }

    @Test
    fun normalizeWhitespaceAndNbsp() {
        val input = "Line  with   multiple &nbsp; spaces \n\n and  newlines  "
        assertEquals("Line with multiple spaces and newlines", HtmlText.clean(input))
    }

    @Test
    fun truncateLongText() {
        val input = "Short text"
        assertEquals("Short text", HtmlText.truncate(input, 50))

        val longInput = "12345678901234567890"
        assertEquals("123456789...", HtmlText.truncate(longInput, 12))
    }

    @Test
    fun cleanNullOrEmpty() {
        assertEquals("", HtmlText.clean(null))
        assertEquals("", HtmlText.clean("   "))
    }
}
