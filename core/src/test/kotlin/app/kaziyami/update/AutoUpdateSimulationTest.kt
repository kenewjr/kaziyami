package app.kaziyami.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Validates the auto-update policy invariants documented from Kototoro and Aniyomi upstream research.
 *
 * Invariants:
 * 1. Update available rule: strictly remote.versionCode > installed.versionCode.
 * 2. Unchanged packages remain INSTALLED (never flagged for update).
 * 3. Same-version re-publish or downgrade is never flagged.
 * 4. Package names and source IDs remain stable across updates.
 * 5. Immutable artifact naming: filenames contain version codes.
 */
class AutoUpdateSimulationTest {

    enum class PackageState {
        AVAILABLE,
        UPDATE_AVAILABLE,
        INSTALLED,
        UNTRUSTED,
        INCOMPATIBLE,
    }

    data class InstalledPackage(
        val packageName: String,
        val versionCode: Long,
        val versionName: String,
    )

    data class RepoPackage(
        val packageName: String,
        val versionCode: Long,
        val versionName: String,
        val jarUrl: String,
        val apkUrl: String,
    )

    // Upstream Kototoro: ExtensionUpdatePolicy.kt:5
    private fun isNewerThanInstalled(installedVersionCode: Long?, remoteVersionCode: Long): Boolean {
        return installedVersionCode != null && remoteVersionCode > installedVersionCode
    }

    // Upstream Kototoro: UnifiedSourcesViewModel.kt:1907
    private fun computeKototoroState(
        installed: InstalledPackage?,
        remote: RepoPackage,
        isTrusted: Boolean = true,
        isCompatible: Boolean = true,
    ): PackageState {
        return when {
            installed != null && !isTrusted -> PackageState.UNTRUSTED
            !isCompatible -> PackageState.INCOMPATIBLE
            installed == null -> PackageState.AVAILABLE
            isNewerThanInstalled(installed.versionCode, remote.versionCode) -> PackageState.UPDATE_AVAILABLE
            else -> PackageState.INSTALLED
        }
    }

    @Test
    fun `simulate release N to N plus 1 only flags bumped packages`() {
        val installedPackages = mapOf(
            "eu.kanade.tachiyomi.animeextension.id.nekopoi" to InstalledPackage("eu.kanade.tachiyomi.animeextension.id.nekopoi", 2L, "1.4.2"),
            "eu.kanade.tachiyomi.animeextension.id.indohentai" to InstalledPackage("eu.kanade.tachiyomi.animeextension.id.indohentai", 2L, "1.4.2"),
            "eu.kanade.tachiyomi.animeextension.id.nontonhentai" to InstalledPackage("eu.kanade.tachiyomi.animeextension.id.nontonhentai", 2L, "1.4.2"),
            "eu.kanade.tachiyomi.animeextension.en.hentaimama" to InstalledPackage("eu.kanade.tachiyomi.animeextension.en.hentaimama", 2L, "1.4.2"),
            "eu.kanade.tachiyomi.animeextension.en.javsubbed" to InstalledPackage("eu.kanade.tachiyomi.animeextension.en.javsubbed", 2L, "1.4.2"),
            "eu.kanade.tachiyomi.animeextension.en.hanime" to InstalledPackage("eu.kanade.tachiyomi.animeextension.en.hanime", 2L, "1.4.2"),
        )

        // Release N+1: only Nekopoi is bumped from 2 to 3
        val repoPackages = listOf(
            RepoPackage("eu.kanade.tachiyomi.animeextension.id.nekopoi", 3L, "1.4.3", "jar/kaziyami-nekopoi-v1.4.3.jar", "apk/kaziyami-id.nekopoi-v14.3.apk"),
            RepoPackage("eu.kanade.tachiyomi.animeextension.id.indohentai", 2L, "1.4.2", "jar/kaziyami-indohentai-v1.4.2.jar", "apk/kaziyami-id.indohentai-v14.2.apk"),
            RepoPackage("eu.kanade.tachiyomi.animeextension.id.nontonhentai", 2L, "1.4.2", "jar/kaziyami-nontonhentai-v1.4.2.jar", "apk/kaziyami-id.nontonhentai-v14.2.apk"),
            RepoPackage("eu.kanade.tachiyomi.animeextension.en.hentaimama", 2L, "1.4.2", "jar/kaziyami-hentaimama-v1.4.2.jar", "apk/kaziyami-en.hentaimama-v14.2.apk"),
            RepoPackage("eu.kanade.tachiyomi.animeextension.en.javsubbed", 2L, "1.4.2", "jar/kaziyami-javsubbed-v1.4.2.jar", "apk/kaziyami-en.javsubbed-v14.2.apk"),
            RepoPackage("eu.kanade.tachiyomi.animeextension.en.hanime", 2L, "1.4.2", "jar/kaziyami-hanime-v1.4.2.jar", "apk/kaziyami-en.hanime-v14.2.apk"),
        )

        val states = repoPackages.associate { repoPkg ->
            val installed = installedPackages[repoPkg.packageName]
            repoPkg.packageName to computeKototoroState(installed, repoPkg)
        }

        // Nekopoi is UPDATE_AVAILABLE
        assertEquals(PackageState.UPDATE_AVAILABLE, states["eu.kanade.tachiyomi.animeextension.id.nekopoi"])

        // All other 5 sources remain INSTALLED
        assertEquals(PackageState.INSTALLED, states["eu.kanade.tachiyomi.animeextension.id.indohentai"])
        assertEquals(PackageState.INSTALLED, states["eu.kanade.tachiyomi.animeextension.id.nontonhentai"])
        assertEquals(PackageState.INSTALLED, states["eu.kanade.tachiyomi.animeextension.en.hentaimama"])
        assertEquals(PackageState.INSTALLED, states["eu.kanade.tachiyomi.animeextension.en.javsubbed"])
        assertEquals(PackageState.INSTALLED, states["eu.kanade.tachiyomi.animeextension.en.hanime"])
    }

    @Test
    fun `same-version republish and downgrade are never flagged as update`() {
        val installed = InstalledPackage("eu.kanade.tachiyomi.animeextension.id.nekopoi", 2L, "1.4.2")

        // 1. Same version
        val sameVersion = RepoPackage("eu.kanade.tachiyomi.animeextension.id.nekopoi", 2L, "1.4.2", "jar/kaziyami-nekopoi-v1.4.2.jar", "apk/kaziyami-id.nekopoi-v14.2.apk")
        assertFalse(isNewerThanInstalled(installed.versionCode, sameVersion.versionCode))
        assertEquals(PackageState.INSTALLED, computeKototoroState(installed, sameVersion))

        // 2. Downgrade (remote has lower code)
        val downgrade = RepoPackage("eu.kanade.tachiyomi.animeextension.id.nekopoi", 1L, "1.4.1", "jar/kaziyami-nekopoi-v1.4.1.jar", "apk/kaziyami-id.nekopoi-v14.1.apk")
        assertFalse(isNewerThanInstalled(installed.versionCode, downgrade.versionCode))
        assertEquals(PackageState.INSTALLED, computeKototoroState(installed, downgrade))
    }

    @Test
    fun `immutable artifact names differ across releases`() {
        val relN = RepoPackage("eu.kanade.tachiyomi.animeextension.id.nekopoi", 2L, "1.4.2", "jar/kaziyami-nekopoi-v1.4.2.jar", "apk/kaziyami-id.nekopoi-v14.2.apk")
        val relNPlus1 = RepoPackage("eu.kanade.tachiyomi.animeextension.id.nekopoi", 3L, "1.4.3", "jar/kaziyami-nekopoi-v1.4.3.jar", "apk/kaziyami-id.nekopoi-v14.3.apk")

        // Artifact paths are strictly distinct
        assertTrue(relN.jarUrl != relNPlus1.jarUrl)
        assertTrue(relN.apkUrl != relNPlus1.apkUrl)
    }
}

