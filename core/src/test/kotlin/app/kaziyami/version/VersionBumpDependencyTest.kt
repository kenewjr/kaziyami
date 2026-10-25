package app.kaziyami.version

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionBumpDependencyTest {

    private val dependencyMap = mapOf(
        "src/id/nekopoi" to listOf("core", "lib-multisrc/generic", "src/id/nekopoi"),
        "src/id/indohentai" to listOf("core", "lib-multisrc/generic", "src/id/indohentai"),
        "src/id/nontonhentai" to listOf("core", "lib-multisrc/animestream", "lib-multisrc/generic", "src/id/nontonhentai"),
        "src/en/hentaimama" to listOf("core", "lib-multisrc/dooplay", "lib-multisrc/generic", "src/en/hentaimama"),
        "src/en/javsubbed" to listOf("core", "lib-multisrc/retrotube", "lib-multisrc/generic", "src/en/javsubbed"),
        "src/en/hanime" to listOf("core", "lib-multisrc/generic", "src/en/hanime")
    )

    private fun getAffected(changedFiles: List<String>): Set<String> {
        val affected = mutableSetOf<String>()
        for ((ext, deps) in dependencyMap) {
            for (f in changedFiles) {
                val normF = f.replace('\\', '/')
                for (dep in deps) {
                    if (normF.startsWith("$dep/") || normF == dep) {
                        affected.add(ext)
                        break
                    }
                }
            }
        }
        return affected
    }

    @Test
    fun coreChangesAffectAllExtensions() {
        val affected = getAffected(listOf("core/src/main/kotlin/app/kaziyami/network/NetworkFactory.kt"))
        assertEquals(6, affected.size)
        assertTrue(affected.contains("src/id/nekopoi"))
        assertTrue(affected.contains("src/en/hanime"))
    }

    @Test
    fun genericMultisrcChangesAffectAllExtensions() {
        val affected = getAffected(listOf("lib-multisrc/generic/src/main/kotlin/app/kaziyami/multisrc/generic/GenericThemeSource.kt"))
        assertEquals(6, affected.size)
    }

    @Test
    fun animestreamChangesAffectOnlyNontonHentai() {
        val affected = getAffected(listOf("lib-multisrc/animestream/src/main/kotlin/app/kaziyami/multisrc/animestream/AnimeStreamTheme.kt"))
        assertEquals(1, affected.size)
        assertTrue(affected.contains("src/id/nontonhentai"))
    }

    @Test
    fun dooplayChangesAffectOnlyHentaimama() {
        val affected = getAffected(listOf("lib-multisrc/dooplay/src/main/kotlin/app/kaziyami/multisrc/dooplay/DooPlayTheme.kt"))
        assertEquals(1, affected.size)
        assertTrue(affected.contains("src/en/hentaimama"))
    }

    @Test
    fun retrotubeChangesAffectOnlyJAVSubbed() {
        val affected = getAffected(listOf("lib-multisrc/retrotube/src/main/kotlin/app/kaziyami/multisrc/retrotube/RetroTubeTheme.kt"))
        assertEquals(1, affected.size)
        assertTrue(affected.contains("src/en/javsubbed"))
    }

    @Test
    fun isolatedExtensionChangesAffectOnlyThatExtension() {
        val affected = getAffected(listOf("src/id/nekopoi/src/main/kotlin/eu/kanade/tachiyomi/animeextension/id/nekopoi/Nekopoi.kt"))
        assertEquals(1, affected.size)
        assertTrue(affected.contains("src/id/nekopoi"))
    }
}
