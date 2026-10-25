package eu.kanade.tachiyomi.schema

import eu.kanade.tachiyomi.lib.core.SourceConfig
import eu.kanade.tachiyomi.lib.core.SourceConfigLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SourceConfigSchemaTest {

    @Test
    fun loadValidSourceConfig() {
        val json = """
            {
              "name": "TestSource",
              "lang": "en",
              "baseUrl": "https://test.example.com",
              "mirrors": ["https://mirror.example.com"],
              "isNsfw": true,
              "selectors": {
                "item": "article.card",
                "itemTitle": "h2.title",
                "itemUrl": "a.link"
              }
            }
        """.trimIndent()

        val config = SourceConfigLoader.loadFromString(json)
        assertEquals("TestSource", config.name)
        assertEquals("en", config.lang)
        assertEquals("https://test.example.com", config.baseUrl)
        assertEquals(1, config.mirrors.size)
        assertEquals("article.card", config.selectors.item)
    }

    @Test
    fun loadDemoSourceFile() {
        val fileCandidates = listOf(
            File("sources/demo_source.json"),
            File("../sources/demo_source.json")
        )
        val file = fileCandidates.firstOrNull { it.exists() }
        assertNotNull(file, "sources/demo_source.json must exist")

        val config = SourceConfigLoader.loadFromFile(file!!)
        assertEquals("DemoAnime", config.name)
        assertEquals("en", config.lang)
        assertTrue(config.baseUrl.startsWith("https://"))
    }

    @Test
    fun invalidConfigThrowsReadableError() {
        // Missing name
        val noNameJson = """
            {
              "name": "",
              "lang": "en",
              "baseUrl": "https://example.com"
            }
        """.trimIndent()
        val ex1 = assertThrows(IllegalArgumentException::class.java) {
            SourceConfigLoader.loadFromString(noNameJson)
        }
        assertTrue(ex1.message!!.contains("'name' must not be blank"))

        // Invalid lang
        val invalidLangJson = """
            {
              "name": "ValidName",
              "lang": "x",
              "baseUrl": "https://example.com"
            }
        """.trimIndent()
        val ex2 = assertThrows(IllegalArgumentException::class.java) {
            SourceConfigLoader.loadFromString(invalidLangJson)
        }
        assertTrue(ex2.message!!.contains("'lang' must be a valid language code"))

        // Invalid baseUrl scheme
        val invalidUrlJson = """
            {
              "name": "ValidName",
              "lang": "en",
              "baseUrl": "ftp://example.com"
            }
        """.trimIndent()
        val ex3 = assertThrows(IllegalArgumentException::class.java) {
            SourceConfigLoader.loadFromString(invalidUrlJson)
        }
        assertTrue(ex3.message!!.contains("'baseUrl' must start with http:// or https://"))
    }
}
