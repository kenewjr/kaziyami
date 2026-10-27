package org.skepsun.kototoro.parsers

import app.kaziyami.adapter.kototoro.KototoroParserRegistry
import org.skepsun.kototoro.parsers.model.ContentParserSource

/**
 * Entry point that Kototoro's ParserPluginInspector and ParserPluginRegistry detect
 * (org/skepsun/kototoro/parsers/ContentParserFactoryKt.class).
 */
fun newParser(source: ContentParserSource, context: ContentLoaderContext): ContentParser {
    return KototoroParserRegistry.createParser(source, context)
}

