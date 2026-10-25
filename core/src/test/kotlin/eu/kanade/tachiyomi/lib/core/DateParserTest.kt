package eu.kanade.tachiyomi.lib.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

class DateParserTest {

    @Test
    fun parseEnglishDates() {
        val parsed = DateParser.parse("January 15, 2024")
        val cal = Calendar.getInstance().apply { timeInMillis = parsed }
        assertEquals(2024, cal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parseIndonesianFullDates() {
        val parsed = DateParser.parse("15 Januari 2024")
        val cal = Calendar.getInstance().apply { timeInMillis = parsed }
        assertEquals(2024, cal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parseIndonesianAgustusAndDesember() {
        val parsedAgustus = DateParser.parse("20 Agustus 2023")
        val calAgustus = Calendar.getInstance().apply { timeInMillis = parsedAgustus }
        assertEquals(2023, calAgustus.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, calAgustus.get(Calendar.MONTH))

        val parsedDesember = DateParser.parse("25 Desember 2022")
        val calDesember = Calendar.getInstance().apply { timeInMillis = parsedDesember }
        assertEquals(2022, calDesember.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, calDesember.get(Calendar.MONTH))
    }

    @Test
    fun parseRelativeEnglishDates() {
        val parsedDays = DateParser.parse("2 days ago")
        val parsedHours = DateParser.parse("5 hours ago")
        val parsedMinutes = DateParser.parse("30 minutes ago")
        val now = System.currentTimeMillis()

        assertTrue(parsedDays in (now - 3 * 86400_000L)..(now - 1 * 86400_000L))
        assertTrue(parsedHours in (now - 6 * 3600_000L)..(now - 4 * 3600_000L))
        assertTrue(parsedMinutes in (now - 35 * 60_000L)..(now - 25 * 60_000L))
    }

    @Test
    fun parseRelativeIndonesianDates() {
        val parsedHari = DateParser.parse("2 hari lalu")
        val parsedJam = DateParser.parse("3 jam lalu")
        val parsedMenit = DateParser.parse("10 menit lalu")
        val now = System.currentTimeMillis()

        assertTrue(parsedHari in (now - 3 * 86400_000L)..(now - 1 * 86400_000L))
        assertTrue(parsedJam in (now - 4 * 3600_000L)..(now - 2 * 3600_000L))
        assertTrue(parsedMenit in (now - 15 * 60_000L)..(now - 5 * 60_000L))
    }

    @Test
    fun parseInvalidDateReturnsZero() {
        assertEquals(0L, DateParser.parse(""))
        assertEquals(0L, DateParser.parse("not a valid date string"))
    }
}
