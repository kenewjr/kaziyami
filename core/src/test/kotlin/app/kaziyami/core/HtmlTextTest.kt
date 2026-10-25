package app.kaziyami.core

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

    @Test
    fun stripBoilerplateRemovesIndonesianAndEnglishSuffixes() {
        val title1 = "Overflow Episode 01 Subtitle Indonesia - Nonton Anime"
        assertEquals("Overflow Episode 01", HtmlText.stripBoilerplate(title1))

        val title2 = "Kanojo x Kanojo x Kanojo Sub Indo"
        assertEquals("Kanojo x Kanojo x Kanojo", HtmlText.stripBoilerplate(title2))

        val title3 = "Itadaki Seieki [English Sub] - Nonton"
        assertEquals("Itadaki Seieki", HtmlText.stripBoilerplate(title3))

        val title4 = "Normal Title Without Suffix"
        assertEquals("Normal Title Without Suffix", HtmlText.stripBoilerplate(title4))
    }
}
