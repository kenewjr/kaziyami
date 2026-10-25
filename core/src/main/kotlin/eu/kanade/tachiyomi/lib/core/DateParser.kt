package eu.kanade.tachiyomi.lib.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Multilingual date parser supporting Indonesian locale dates, relative time expressions,
 * standard date formats, and ISO-8601 timestamp strings.
 * Guarantees safe parsing that returns 0L on unresolvable input without throwing exceptions.
 */
object DateParser {

    private val INDONESIAN_MONTHS = mapOf(
        "januari" to "01", "jan" to "01",
        "februari" to "02", "feb" to "02",
        "maret" to "03", "mar" to "03",
        "april" to "04", "apr" to "04",
        "mei" to "05", "may" to "05",
        "juni" to "06", "jun" to "06",
        "juli" to "07", "jul" to "07",
        "agustus" to "08", "agu" to "08", "agt" to "08", "aug" to "08",
        "september" to "09", "sep" to "09",
        "oktober" to "10", "okt" to "10", "oct" to "10",
        "november" to "11", "nov" to "11",
        "desember" to "12", "des" to "12", "dec" to "12"
    )

    private val RELATIVE_PATTERNS = listOf(
        Pattern.compile("(\\d+)\\s*(?:detik|seconds?|secs?)\\s*(?:yang\\s*)?(?:lalu|ago)", Pattern.CASE_INSENSITIVE) to Calendar.SECOND,
        Pattern.compile("(\\d+)\\s*(?:menit|minutes?|mins?)\\s*(?:yang\\s*)?(?:lalu|ago)", Pattern.CASE_INSENSITIVE) to Calendar.MINUTE,
        Pattern.compile("(\\d+)\\s*(?:jam|hours?|hrs?)\\s*(?:yang\\s*)?(?:lalu|ago)", Pattern.CASE_INSENSITIVE) to Calendar.HOUR_OF_DAY,
        Pattern.compile("(\\d+)\\s*(?:hari|days?)\\s*(?:yang\\s*)?(?:lalu|ago)", Pattern.CASE_INSENSITIVE) to Calendar.DAY_OF_YEAR,
        Pattern.compile("(\\d+)\\s*(?:minggu|weeks?)\\s*(?:yang\\s*)?(?:lalu|ago)", Pattern.CASE_INSENSITIVE) to Calendar.WEEK_OF_YEAR,
        Pattern.compile("(\\d+)\\s*(?:bulan|months?)\\s*(?:yang\\s*)?(?:lalu|ago)", Pattern.CASE_INSENSITIVE) to Calendar.MONTH,
        Pattern.compile("(\\d+)\\s*(?:tahun|years?)\\s*(?:yang\\s*)?(?:lalu|ago)", Pattern.CASE_INSENSITIVE) to Calendar.YEAR
    )

    private val STANDARD_FORMATS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "dd-MM-yyyy",
        "dd/MM/yyyy",
        "MMMM d, yyyy",
        "MMM d, yyyy",
        "d MMMM yyyy",
        "d MMM yyyy"
    )

    /**
     * Parses a date string into Unix epoch milliseconds.
     *
     * @param dateStr Raw date text from HTML or JSON.
     * @return Epoch millisecond timestamp, or 0L if parsing failed.
     */
    fun parse(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val clean = dateStr.trim().lowercase()

        // Relative shortcuts
        if (clean == "hari ini" || clean == "today") {
            return Calendar.getInstance().timeInMillis
        }
        if (clean == "kemarin" || clean == "yesterday") {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return cal.timeInMillis
        }

        // Relative delta expressions
        for ((pattern, field) in RELATIVE_PATTERNS) {
            val matcher = pattern.matcher(clean)
            if (matcher.find()) {
                val amount = matcher.group(1).toIntOrNull() ?: continue
                val cal = Calendar.getInstance()
                cal.add(field, -amount)
                return cal.timeInMillis
            }
        }

        // ISO & standard formats
        for (fmt in STANDARD_FORMATS) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.isLenient = false
                val parsed = sdf.parse(clean)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }

        // Indonesian natural date e.g. "Minggu, 25 Oktober 2026" or "12 Oktober 2026"
        val idRegex = Pattern.compile("(\\d{1,2})\\s+([a-zA-Z]+)\\s+(\\d{4})")
        val idMatch = idRegex.matcher(clean)
        if (idMatch.find()) {
            val day = idMatch.group(1).padStart(2, '0')
            val monthWord = idMatch.group(2).lowercase()
            val year = idMatch.group(3)
            val monthNum = INDONESIAN_MONTHS[monthWord]
            if (monthNum != null) {
                try {
                    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                    return sdf.parse("$day-$monthNum-$year")?.time ?: 0L
                } catch (_: Exception) {
                }
            }
        }

        return 0L
    }
}
