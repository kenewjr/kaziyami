package eu.kanade.tachiyomi.lib.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeNumberParserTest {

    @Test
    fun parseStandardEpisode() {
        assertEquals(3.0f, EpisodeNumberParser.parse("Episode 3"))
        assertEquals(12.0f, EpisodeNumberParser.parse("Episode 12"))
        assertEquals(100.0f, EpisodeNumberParser.parse("Episode 100"))
    }

    @Test
    fun parseAbbreviatedEpisode() {
        assertEquals(3.0f, EpisodeNumberParser.parse("Ep. 3"))
        assertEquals(5.0f, EpisodeNumberParser.parse("Ep 05"))
        assertEquals(8.0f, EpisodeNumberParser.parse("E08"))
        assertEquals(2.0f, EpisodeNumberParser.parse("ep2"))
        assertEquals(3.0f, EpisodeNumberParser.parse("#3"))
        assertEquals(15.0f, EpisodeNumberParser.parse("#15"))
    }

    @Test
    fun parseIndonesianEpisodeFormatting() {
        assertEquals(3.0f, EpisodeNumberParser.parse("Episode 03 Subtitle Indonesia"))
        assertEquals(1.0f, EpisodeNumberParser.parse("Eps 01 Sub Indo"))
        assertEquals(7.0f, EpisodeNumberParser.parse("Ep. 07 Subtitle Indonesia"))
    }

    @Test
    fun parseDecimalAndSpecialEpisodes() {
        assertEquals(3.5f, EpisodeNumberParser.parse("Episode 3.5"))
        assertEquals(1.0f, EpisodeNumberParser.parse("OVA 1"))
        assertEquals(2.0f, EpisodeNumberParser.parse("Special 2"))
        assertEquals(1.0f, EpisodeNumberParser.parse("OVA"))
        assertEquals(1.0f, EpisodeNumberParser.parse("Special"))
        assertEquals(1.0f, EpisodeNumberParser.parse("The Movie"))
    }

    @Test
    fun parseRanges() {
        assertEquals(1.0f, EpisodeNumberParser.parse("Episode 01-02"))
        assertEquals(5.0f, EpisodeNumberParser.parse("Ep 05-06"))
        assertEquals(1.0f, EpisodeNumberParser.parse("1-2"))
    }

    @Test
    fun parseStandaloneNumbers() {
        assertEquals(4.0f, EpisodeNumberParser.parse("Show Title 04 1080p"))
        assertEquals(1.0f, EpisodeNumberParser.parse("01"))
    }

    @Test
    fun parseFallback() {
        assertEquals(0.0f, EpisodeNumberParser.parse("Preview Video"))
        assertEquals(0.0f, EpisodeNumberParser.parse(""))
        assertEquals(42.0f, EpisodeNumberParser.parse("Bonus Preview Video", fallbackPosition = 42.0f))
    }
}
