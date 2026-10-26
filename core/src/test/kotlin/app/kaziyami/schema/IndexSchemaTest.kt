package app.kaziyami.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipFile

class IndexSchemaTest {

    private val json = Json { ignoreUnknownKeys = false }

    private val expectedSourceIds = mapOf(
        "Nekopoi" to "7073589375897212735",
        "IndoHentai" to "7369497329911250289",
        "NontonHentai" to "7400791378172011716",
        "Hentaimama" to "8058423919997530503",
        "JAVSubbed" to "9192959167101688940",
        "Hanime" to "2150907185602526076"
    )

    private fun getRepoDir(): File {
        return requireNotNull(listOf(File("repo"), File("../repo")).firstOrNull { it.exists() }) {
            "repo directory must exist"
        }
    }

    @Test
    fun validateIndexJsonStructure() {
        val repoDir = getRepoDir()
        val indexFile = File(repoDir, "index.json")
        if (!indexFile.exists()) {
            val script = listOf(File("tools/generate_repo_index.py"), File("../tools/generate_repo_index.py")).firstOrNull { it.exists() }
            if (script != null) {
                val pb = ProcessBuilder("python", script.absolutePath)
                pb.redirectErrorStream(true)
                pb.start().waitFor()
            }
        }
        assertTrue(indexFile.exists(), "repo/index.json must exist")

        val content = indexFile.readText()
        val array = json.parseToJsonElement(content).jsonArray
        assertEquals(6, array.size, "Must contain all 6 sources")

        val expectedPkgs = setOf(
            "eu.kanade.tachiyomi.animeextension.id.nekopoi",
            "eu.kanade.tachiyomi.animeextension.id.indohentai",
            "eu.kanade.tachiyomi.animeextension.id.nontonhentai",
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
            assertTrue(name!!.startsWith("Kaziyami: "), "Display name must follow Kaziyami family naming convention: $name")
            assertNotNull(pkg)
            assertNotNull(apk)
            assertTrue(apk!!.startsWith("kaziyami-"), "APK must follow kaziyami naming: $apk")
            assertNotNull(lang)
            assertNotNull(code)
            assertNotNull(version)
            assertEquals(1, nsfw, "nsfw flag must be 1 for adult extensions")
            assertNotNull(sources)
            assertFalse(sources!!.isEmpty())

            assertTrue(pkg!!.matches("^eu\\.kanade\\.tachiyomi\\.animeextension\\.[a-z]{2}\\.[a-z0-9]+$".toRegex()))
            assertFalse(seenPkgs.contains(pkg), "Duplicate package name detected in index: $pkg")
            seenPkgs.add(pkg)

            val sourceObj = sources[0].jsonObject
            val sourceName = sourceObj["name"]?.jsonPrimitive?.content
            val id = sourceObj["id"]?.jsonPrimitive?.content
            val baseUrl = sourceObj["baseUrl"]?.jsonPrimitive?.content

            assertNotNull(sourceName)
            assertFalse(sourceName!!.startsWith("Kaziyami:"), "Source name must remain unbranded in sources block: $sourceName")
            assertNotNull(id)
            assertNotNull(baseUrl)

            // Verify source ID matches SOURCE_IDS.md
            val expectedId = expectedSourceIds[sourceName]
            assertNotNull(expectedId, "Source $sourceName not found in expected source IDs")
            assertEquals(expectedId, id, "Source ID for $sourceName must match SOURCE_IDS.md permanent contract")

            assertFalse(seenIds.contains(id), "Duplicate source ID: $id")
            seenIds.add(id!!)

            // Verify APK exists in repo/apk/ if APKs are built
            val apkFile = File(repoDir, "apk/$apk")
            if (apkFile.exists()) {
                ZipFile(apkFile).use { zf ->
                    val manifestEntry = zf.getEntry("AndroidManifest.xml")
                    assertNotNull(manifestEntry, "AndroidManifest.xml missing from $apk")
                    val manifestText = zf.getInputStream(manifestEntry).bufferedReader().readText()
                    assertTrue(manifestText.contains("versionName=\"$version\""), "Version in index must equal version in APK manifest for $apk")
                    assertTrue(manifestText.contains("versionCode=\"$code\""), "VersionCode in index must equal versionCode in APK manifest for $apk")
                }
            }
        }

        assertEquals(expectedPkgs, seenPkgs)
    }

    @Test
    fun validateIndexMinJsonMatches() {
        val repoDir = getRepoDir()
        val minFile = File(repoDir, "index.min.json")
        assertTrue(minFile.exists(), "repo/index.min.json must exist")

        val content = minFile.readText()
        assertFalse(content.contains("\n"), "index.min.json must be minified")
        val array = json.parseToJsonElement(content).jsonArray
        assertEquals(6, array.size)
    }

    @Test
    fun validateRepoJsonMetaBlock() {
        val repoDir = getRepoDir()
        val repoFile = File(repoDir, "repo.json")
        assertTrue(repoFile.exists(), "repo/repo.json must exist")

        val obj = json.parseToJsonElement(repoFile.readText()).jsonObject
        val meta = obj["meta"]?.jsonObject
        assertNotNull(meta)
        assertEquals("Kaziyami", meta!!["name"]?.jsonPrimitive?.content)
        assertEquals("kaziyami", meta["shortName"]?.jsonPrimitive?.content)
        assertTrue(meta["website"]?.jsonPrimitive?.content?.contains("kaziyami") == true)
        val fp = meta["signingKeyFingerprint"]?.jsonPrimitive?.content
        assertNotNull(fp)
        assertEquals(64, fp!!.length, "Signing fingerprint must be 64-char hex string: $fp")

        // Kototoro index_v2 pointer contract
        assertEquals("index.min.json", obj["index_v2"]?.jsonPrimitive?.content, "Kototoro repo.json must declare index_v2 pointing to index.min.json")
    }

    @Test
    fun validateKototoroContractInvariants() {
        val repoDir = getRepoDir()
        val indexFile = File(repoDir, "index.json")
        assertTrue(indexFile.exists())

        val array = json.parseToJsonElement(indexFile.readText()).jsonArray
        for (el in array) {
            val obj = el.jsonObject
            val pkg = obj["pkg"]!!.jsonPrimitive.content
            val ver = obj["version"]!!.jsonPrimitive.content
            val nsfw = obj["nsfw"]!!.jsonPrimitive.content

            // 1. Kototoro Aniyomi libVersion contract (12.0 - 16.0)
            val libVersionNum = ver.substringBefore('.').toDoubleOrNull()
            assertNotNull(libVersionNum, "libVersion must be numeric for $pkg")
            assertTrue(libVersionNum!! in 12.0..16.0, "Kototoro requires Aniyomi libVersion between 12.0 and 16.0, got $libVersionNum for $pkg")

            // 2. Kototoro icon path resolution: icon/$packageName.png
            val iconFile = File(repoDir, "icon/$pkg.png")
            assertTrue(iconFile.exists(), "Kototoro resolves icons by package name icon/$pkg.png; missing: ${iconFile.path}")

            // 3. Adult NSFW flag
            assertEquals("1", nsfw, "Adult content must flag nsfw=1")

            // 4. Manifest checks on built APKs if present
            val apk = obj["apk"]?.jsonPrimitive?.content
            val apkFile = File(repoDir, "apk/$apk")
            if (apkFile.exists()) {
                ZipFile(apkFile).use { zf ->
                    val manifest = zf.getInputStream(zf.getEntry("AndroidManifest.xml")!!).bufferedReader().readText()
                    assertTrue(manifest.contains("android:name=\"tachiyomi.animeextension\""), "Missing uses-feature tachiyomi.animeextension in $apk")
                    assertTrue(manifest.contains("android:name=\"tachiyomi.animeextension.class\""), "Missing tachiyomi.animeextension.class in $apk")
                    assertTrue(manifest.contains("android:name=\"tachiyomi.animeextension.version\"") && manifest.contains("android:value=\"14\""), "Missing tachiyomi.animeextension.version=14 in $apk")
                    assertTrue(manifest.contains("android:name=\"tachiyomix.extensionLib\""), "Missing tachiyomix.extensionLib helper in $apk")
                    assertTrue(manifest.contains("android:name=\"tachiyomi.animeextension.nsfw\"") && manifest.contains("android:value=\"1\""), "Missing tachiyomi.animeextension.nsfw=1 in $apk")
                }
            }
        }
    }
}
