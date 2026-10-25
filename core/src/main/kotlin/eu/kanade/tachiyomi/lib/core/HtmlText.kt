package eu.kanade.tachiyomi.lib.core

import org.jsoup.parser.Parser

object HtmlText {
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

    fun truncate(text: String?, maxLength: Int): String {
        if (text == null) return ""
        if (text.length <= maxLength) return text
        return text.take(maxLength - 3) + "..."
    }
}
