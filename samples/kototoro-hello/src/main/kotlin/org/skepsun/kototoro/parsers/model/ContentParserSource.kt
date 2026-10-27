package org.skepsun.kototoro.parsers.model

/**
 * Sample plugin source enum for Kototoro Hello.
 * Reflectively read by Kototoro's JarExtensionLoader and ParserPluginRegistry.
 */
enum class ContentParserSource(
    override val locale: String,
    override val contentType: ContentType,
    val title: String,
) : ContentSource {
    KAZIYAMI_DEMO("all", ContentType.HENTAI_VIDEO, "Kaziyami Demo");
}
