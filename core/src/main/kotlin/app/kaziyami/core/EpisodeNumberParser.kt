package app.kaziyami.core

import java.util.regex.Pattern

/**
 * Universal episode number parser capable of extracting normalized numeric representations
 * from multilingual anime titles, including Indonesian formatting, abbreviations, specials, and ranges.
 */
object EpisodeNumberParser {

    private val EP_PATTERNS = listOf(
        // Indonesian forms: "Episode 03 Subtitle Indonesia", "Eps. 03 Sub Indo", "Ep 03"
        Pattern.compile("(?:episode|eps?\\.?|ep\\.?)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:subtitle\\s+indonesia|sub\\s*indo)?", Pattern.CASE_INSENSITIVE),
        // E03 or #03
        Pattern.compile("(?:\\b[eE]|#)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE),
        // Ranges: "Episode 1-2" or "Ep 1 - 2" or "1-2"
        Pattern.compile("(?:episode|ep\\.?|eps?\\.?)?\\s*(\\d+)\\s*-\\s*\\d+", Pattern.CASE_INSENSITIVE),
        // Standalone numbers before qualifiers e.g. "Title - 02 [1080p]", "Title - 03 (Sub Indo)"
        Pattern.compile("(?:[-_\\s#]|^)(\\d+(?:\\.\\d+)?)\\s*(?:(?:sub|vostfr|raw|end|subtitle|\\[|\\().*|\$)", Pattern.CASE_INSENSITIVE),
        // Decimal numbers anywhere: "3.5"
        Pattern.compile("(\\d+\\.\\d+)"),
        // Any leading/trailing digit
        Pattern.compile("(\\d+)")
    )

    /**
     * Parses the episode number from an episode name or title string.
     *
     * @param episodeName The title or text representing the episode.
     * @param fallbackPosition Deterministic fallback value (e.g. index position) if no pattern matches.
     * @return The parsed episode number as a Float.
     */
    fun parse(episodeName: String, fallbackPosition: Float = 0.0f): Float {
        val lower = episodeName.lowercase().trim()

        // Handle specials, OVA, ONA, Movies
        if (lower.contains("movie") || lower.contains("gekijouban")) {
            return 1.0f
        }

        if (lower.contains("ova") || lower.contains("special") || lower.contains("sp") || lower.contains("ona")) {
            val spMatch = Pattern.compile("(?:ova|special|sp|ona)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(lower)
            if (spMatch.find()) {
                val num = spMatch.group(1).toFloatOrNull()
                if (num != null) return num
            }
            return 1.0f
        }

        for (pattern in EP_PATTERNS) {
            val matcher = pattern.matcher(episodeName)
            if (matcher.find()) {
                val candidate = matcher.group(1).toFloatOrNull()
                if (candidate != null) {
                    return candidate
                }
            }
        }

        return fallbackPosition
    }
}
