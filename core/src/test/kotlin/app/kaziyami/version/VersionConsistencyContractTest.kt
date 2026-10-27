package app.kaziyami.version

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class VersionConsistencyContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val sources = listOf(
        Triple("id", "nekopoi", "Nekopoi"),
        Triple("id", "indohentai", "IndoHentai"),
        Triple("id", "nontonhentai", "NontonHentai"),
        Triple("en", "hentaimama", "Hentaimama"),
        Triple("en", "javsubbed", "JAVSubbed"),
        Triple("en", "hanime", "Hanime")
    )

    @Test
    fun testVersionConsistencyAcrossManifestsIndexesAndFilenames() {
        val rootDir = listOf(File(".."), File(".")).firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: File(".")

        val aniyomiIndexFile = File(rootDir, "repo/index.json")
        val kototoroIndexFile = File(rootDir, "repo/jar/index.json")

        if (!aniyomiIndexFile.exists() || !kototoroIndexFile.exists()) {
            val script = File(rootDir, "tools/generate_repo_index.py")
            val pb = ProcessBuilder("python", script.absolutePath, "--mode", "all")
            pb.directory(rootDir)
            pb.redirectErrorStream(true)
            pb.start().waitFor()
        }

        assertTrue(aniyomiIndexFile.exists(), "repo/index.json must exist")
        assertTrue(kototoroIndexFile.exists(), "repo/jar/index.json must exist")

        val aniyomiArray = json.parseToJsonElement(aniyomiIndexFile.readText()).jsonArray
        val aniyomiMap = aniyomiArray.associateBy { it.jsonObject["pkg"]?.jsonPrimitive?.content }

        val kototoroTree = json.parseToJsonElement(kototoroIndexFile.readText()).jsonObject
        val kototoroExts = kototoroTree["extensionList"]?.jsonObject?.get("extensions")?.jsonArray
        assertNotNull(kototoroExts, "Kototoro extensions list must not be null")
        val kototoroMap = kototoroExts!!.associateBy { it.jsonObject["packageName"]?.jsonPrimitive?.content }

        for ((lang, slug, _) in sources) {
            val buildFile = File(rootDir, "src/$lang/$slug/build.gradle.kts")
            assertTrue(buildFile.exists(), "Source build file must exist: ${buildFile.path}")

            val content = buildFile.readText()
            val codeMatch = Regex("""val\s+extVersionCode\s*=\s*(\d+)""").find(content)
            val libMatch = Regex("""val\s+libVersion\s*=\s*"([^"]+)"""").find(content)

            assertNotNull(codeMatch, "extVersionCode must be declared in $slug")
            assertNotNull(libMatch, "libVersion must be declared in $slug")

            val extVersionCode = codeMatch!!.groupValues[1].toInt()
            val rawLibVersion = libMatch!!.groupValues[1]

            // 1. Variant A (Aniyomi APK) Invariants
            val aniyomiPkg = "eu.kanade.tachiyomi.animeextension.$lang.$slug"
            val aniyomiEntry = aniyomiMap[aniyomiPkg]?.jsonObject
            assertNotNull(aniyomiEntry, "Aniyomi index must contain entry for $aniyomiPkg")

            val expectedAniyomiVersion = "$rawLibVersion.$extVersionCode"
            val actualAniyomiVersion = aniyomiEntry!!["version"]?.jsonPrimitive?.content
            assertEquals(expectedAniyomiVersion, actualAniyomiVersion, "Aniyomi index version mismatch for $slug")

            val expectedApkName = "kaziyami-$lang.$slug-v$expectedAniyomiVersion.apk"
            val actualApkName = aniyomiEntry["apk"]?.jsonPrimitive?.content
            assertEquals(expectedApkName, actualApkName, "Aniyomi APK filename mismatch for $slug")

            // 2. Variant B (Kototoro JAR) Invariants
            val kototoroEntry = kototoroMap[aniyomiPkg]?.jsonObject
            assertNotNull(kototoroEntry, "Kototoro index must contain entry for $aniyomiPkg")

            val expectedKototoroVersion = "1.4.$extVersionCode"
            val actualKototoroVersion = kototoroEntry!!["versionName"]?.jsonPrimitive?.content
            val actualKototoroCode = kototoroEntry["versionCode"]?.jsonPrimitive?.content?.toInt()
            assertEquals(expectedKototoroVersion, actualKototoroVersion, "Kototoro versionName mismatch for $slug")
            assertEquals(extVersionCode, actualKototoroCode, "Kototoro versionCode mismatch for $slug")

            val expectedJarUrl = "jar/kaziyami-$slug-v$expectedKototoroVersion.jar"
            val actualJarUrl = kototoroEntry["resources"]?.jsonObject?.get("jarUrl")?.jsonPrimitive?.content
            assertEquals(expectedJarUrl, actualJarUrl, "Kototoro jarUrl mismatch for $slug")
        }
    }
}

