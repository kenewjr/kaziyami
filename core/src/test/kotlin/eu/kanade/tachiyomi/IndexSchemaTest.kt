package eu.kanade.tachiyomi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class IndexSchemaTest {

    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun validateIndexJsonStructure() {
        val indexFile = requireNotNull(listOf(File("repo/index.json"), File("../repo/index.json")).firstOrNull { it.exists() }) {
            "repo/index.json must exist"
        }

        val content = indexFile.readText()
        val array = json.parseToJsonElement(content).jsonArray
        assertEquals(6, array.size, "Must contain all 6 sources")

        val expectedPkgs = setOf(
            "eu.kanade.tachiyomi.animeextension.id.nekopoi",
            "eu.kanade.tachiyomi.animeextension.id.indohentai",
            "eu.kanade.tachiyomi.animeextension.id.kuronime",
            "eu.kanade.tachiyomi.animeextension.en.hentaimama",
            "eu.kanade.tachiyomi.animeextension.en.javsubbed",
            "eu.kanade.tachiyomi.animeextension.en.hanime"
        )

        val seenPkgs = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()

        for (el in array) {
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content
            val pkg = obj["pkg"]?.jsonPrimitive?.content
            val apk = obj["apk"]?.jsonPrimitive?.content
            val lang = obj["lang"]?.jsonPrimitive?.content
            val code = obj["code"]?.jsonPrimitive?.content?.toIntOrNull()
            val version = obj["version"]?.jsonPrimitive?.content
            val nsfw = obj["nsfw"]?.jsonPrimitive?.content?.toIntOrNull()
            val sources = obj["sources"]?.jsonArray

            assertNotNull(name)
            assertNotNull(pkg)
            assertNotNull(apk)
            assertNotNull(lang)
            assertNotNull(code)
            assertNotNull(version)
            assertEquals(1, nsfw, "nsfw flag must be 1 for adult extensions")
            assertNotNull(sources)
            assertFalse(sources!!.isEmpty())

            assertTrue(pkg!!.matches("^eu\\.kanade\\.tachiyomi\\.animeextension\\.[a-z]{2}\\.[a-z0-9]+$".toRegex()))
            seenPkgs.add(pkg)

            val sourceObj = sources[0].jsonObject
            val id = sourceObj["id"]?.jsonPrimitive?.content
            val baseUrl = sourceObj["baseUrl"]?.jsonPrimitive?.content

            assertNotNull(id)
            assertNotNull(baseUrl)
            assertTrue(id!!.isNotBlank())
            assertTrue(baseUrl!!.startsWith("http://") || baseUrl.startsWith("https://"))
            assertFalse(seenIds.contains(id), "Duplicate source ID: $id")
            seenIds.add(id)
        }

        assertEquals(expectedPkgs, seenPkgs)
    }

    @Test
    fun validateIndexMinJsonMatches() {
        val minFile = requireNotNull(listOf(File("repo/index.min.json"), File("../repo/index.min.json")).firstOrNull { it.exists() }) {
            "repo/index.min.json must exist"
        }

        val content = minFile.readText()
        assertFalse(content.contains("\n"), "index.min.json must be minified")
        val array = json.parseToJsonElement(content).jsonArray
        assertEquals(6, array.size)
    }
}
