package eu.kanade.tachiyomi.lib.core

import org.jsoup.parser.Parser

/**
 * HTML text sanitization, entity decoding, and title boilerplate cleanup utilities.
 */
object HtmlText {

    val DEFAULT_TITLE_BOILERPLATE = listOf(
        "(?i)\\s*-\\s*Nonton\\s+Anime.*$".toRegex(),
        "(?i)\\s*-\\s*Nonton.*$".toRegex(),
        "(?i)\\s*-\\s*Subtitle\\s+Indonesia.*$".toRegex(),
        "(?i)\\s*Subtitle\\s+Indonesia.*$".toRegex(),
        "(?i)\\s*Sub\\s*Indo.*$".toRegex(),
        "(?i)\\s*-\\s*English\\s+Subbed.*$".toRegex(),
        "(?i)\\s*English\\s+Subbed.*$".toRegex(),
        "(?i)\\s*Eng\\s*Sub.*$".toRegex(),
        "(?i)\\s*\\[English\\s+Sub\\]".toRegex(),
        "(?i)\\s*\\[Subbed\\]".toRegex()
    )

    /**
     * Unescapes HTML entities, strips tags, collapses consecutive whitespaces and non-breaking spaces.
     */
    fun clean(text: String?): String {
        if (text == null) return ""
        val unescaped = Parser.unescapeEntities(text, false)
        return unescaped
            .replace('\u00A0', ' ')
            .replace("&nbsp;", " ")
            .replace("<[^>]*>".toRegex(), "")
            .replace("[\\s\\h]+".toRegex(), " ")
            .trim()
    }

    /**
     * Strips promotional boilerplate suffixes and site watermarks from anime and episode titles.
     */
    fun stripBoilerplate(title: String?, patterns: List<Regex> = DEFAULT_TITLE_BOILERPLATE): String {
        if (title.isNullOrBlank()) return ""
        var cleaned = clean(title)
        for (pattern in patterns) {
            cleaned = cleaned.replace(pattern, "").trim()
        }
        return cleaned
    }

    /**
     * Truncates a string to maxLength, appending an ellipsis if truncated.
     */
    fun truncate(text: String?, maxLength: Int): String {
        if (text == null) return ""
        if (text.length <= maxLength) return text
        return text.take(maxLength - 3) + "..."
    }
}
