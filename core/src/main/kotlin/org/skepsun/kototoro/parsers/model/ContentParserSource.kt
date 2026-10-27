package org.skepsun.kototoro.parsers.model

/**
 * Kototoro source enum. Every JAR defines its own source enum;
 * Kototoro's host reads the constants reflectively.
 */
enum class ContentParserSource(
    override val locale: String,
    override val contentType: ContentType,
    val title: String,
) : ContentSource {
    NEKOPOI("id", ContentType.HENTAI_VIDEO, "Nekopoi"),
    INDO_HENTAI("id", ContentType.HENTAI_VIDEO, "IndoHentai"),
    NONTON_HENTAI("id", ContentType.HENTAI_VIDEO, "NontonHentai"),
    HENTAIMAMA("en", ContentType.HENTAI_VIDEO, "Hentaimama"),
    JAVSUBBED("en", ContentType.HENTAI_VIDEO, "JAVSubbed"),
    HANIME("en", ContentType.HENTAI_VIDEO, "Hanime"),
}

