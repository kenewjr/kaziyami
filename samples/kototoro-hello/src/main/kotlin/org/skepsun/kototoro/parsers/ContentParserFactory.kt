package org.skepsun.kototoro.parsers

import app.kaziyami.hello.KaziyamiDemoParser
import org.skepsun.kototoro.parsers.model.ContentParserSource

/**
 * Entry point reflectively detected by Kototoro's JarExtensionLoader and ParserPluginRegistry.
 */
fun newParser(source: ContentParserSource, context: ContentLoaderContext): ContentParser {
    return KaziyamiDemoParser(source, context)
}
