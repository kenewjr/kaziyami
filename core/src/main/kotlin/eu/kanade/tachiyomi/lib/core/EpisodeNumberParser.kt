package eu.kanade.tachiyomi.lib.core

import java.util.regex.Pattern

object EpisodeNumberParser {
    private val EP_PATTERNS = listOf(
        // Episode 3 or Ep. 3 or Ep 03.5
        Pattern.compile("(?:episode|ep\\.?)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE),
        // Range 1-12 or 1 - 12 (takes first number or end? Usually 1.0 or entire)
        Pattern.compile("(?:episode|ep\\.?)\\s*(\\d+)\\s*-\\s*\\d+", Pattern.CASE_INSENSITIVE),
        // Standalone numbers at end of title e.g. "Pure x Holic - 02"
        Pattern.compile("(?:[-_\\s#]|^)(\\d+(?:\\.\\d+)?)\\s*(?:(?:sub|vostfr|raw|end|\\[|\\().*|\$)", Pattern.CASE_INSENSITIVE),
        // Any leading/trailing digit
        Pattern.compile("(\\d+(?:\\.\\d+)?)")
    )

    fun parse(episodeName: String): Float {
        val lower = episodeName.lowercase().trim()

        if (lower.contains("ova") || lower.contains("special") || lower.contains("sp")) {
            // Check if there is a number after OVA / SP
            val spMatch = Pattern.compile("(?:ova|special|sp)\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(lower)
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

        return 0.0f
    }
}
