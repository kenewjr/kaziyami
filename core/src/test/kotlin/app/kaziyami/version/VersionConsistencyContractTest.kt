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
        Triple("id", "nontonhentai", "NontonHentai"),
        Triple("en", "hentaimama", "Hentaimama"),
        Triple("en", "hanime", "Hanime"),
        Triple("en", "rulevid", "RuleVid"),
        Triple("en", "rule34video", "Rule34Video"),
        Triple("zh", "hanime1", "Hanime1"),
        Triple("zh", "3xchina", "3xchina")
    )

    @Test
    fun testVersionConsistencyAcrossManifestsIndexesAndFilenames() {
        val rootDir = listOf(File(".."), File(".")).firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: File(".")

        val aniyomiIndexFile = File(rootDir, "repo/index.json")

        if (!aniyomiIndexFile.exists()) {
            val script = File(rootDir, "tools/generate_repo_index.py")
            val pb = ProcessBuilder("python", script.absolutePath, "--mode", "aniyomi")
            pb.directory(rootDir)
            pb.redirectErrorStream(true)
            pb.start().waitFor()
        }

        assertTrue(aniyomiIndexFile.exists(), "repo/index.json must exist")

        val aniyomiArray = json.parseToJsonElement(aniyomiIndexFile.readText()).jsonArray
        val aniyomiMap = aniyomiArray.associateBy { it.jsonObject["pkg"]?.jsonPrimitive?.content }

        for ((lang, slug, name) in sources) {
            val buildFile = File(rootDir, "src/$lang/$slug/build.gradle.kts")
            assertTrue(buildFile.exists(), "Source build file must exist: ${buildFile.path}")

            val content = buildFile.readText()
            val extMatch = Regex("""val\s+extVersion\s*=\s*"([^"]+)"""").find(content)
            val codeMatch = Regex("""val\s+extVersionCode\s*=\s*(\d+)""").find(content)
            val libMatch = Regex("""val\s+libVersion\s*=\s*"([^"]+)"""").find(content)

            assertNotNull(codeMatch, "extVersionCode must be declared in $slug")
            assertNotNull(libMatch, "libVersion must be declared in $slug")

            val extVersionCode = codeMatch!!.groupValues[1].toInt()
            val rawLibVersion = libMatch!!.groupValues[1]

            // Variant A (Aniyomi APK) Invariants
            val pkgSuffix = if (slug == "3xchina") "threexchina" else slug
            val aniyomiPkg = "eu.kanade.tachiyomi.animeextension.$lang.$pkgSuffix"
            val aniyomiEntry = aniyomiMap[aniyomiPkg]?.jsonObject
            assertNotNull(aniyomiEntry, "Aniyomi index must contain entry for $aniyomiPkg")

            val expectedAniyomiVersion = extMatch?.groupValues?.get(1) ?: "$rawLibVersion.$extVersionCode"
            val actualAniyomiVersion = aniyomiEntry!!["version"]?.jsonPrimitive?.content
            assertEquals(expectedAniyomiVersion, actualAniyomiVersion, "Aniyomi index version mismatch for $slug")

            val expectedApkName = "kaziyami-$lang.$slug-v$expectedAniyomiVersion.apk"
            val actualApkName = aniyomiEntry["apk"]?.jsonPrimitive?.content
            assertEquals(expectedApkName, actualApkName, "Aniyomi APK filename mismatch for $slug")
        }
    }
}

