package app.kaziyami.adapter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipFile

class KototoroPackagingTest {

    @Test
    fun testKototoroJarPackaging() {
        val rootDir = listOf(File("."), File("..")).firstOrNull { File(it, "tools/package_kototoro_plugin.py").exists() }
            ?: File(".")

        val jarFile = File(rootDir, "build/libs/kaziyami-kototoro-all-v1.4.1.jar")
        if (!jarFile.exists()) {
            val script = File(rootDir, "tools/package_kototoro_plugin.py")
            val pb = ProcessBuilder("python", script.absolutePath)
            pb.directory(rootDir)
            pb.redirectErrorStream(true)
            pb.start().waitFor()
        }

        assertTrue(jarFile.exists(), "Kototoro plugin JAR must be generated at build/libs/kaziyami-kototoro-all-v1.4.1.jar")

        ZipFile(jarFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()

            // 1. Must contain AndroidManifest.xml
            assertTrue(entries.contains("AndroidManifest.xml"), "Must contain text AndroidManifest.xml for MihonJarInspector")
            val manifestText = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).reader().readText()
            assertTrue(manifestText.contains("tachiyomi.animeextension.class"), "Manifest must declare tachiyomi.animeextension.class")
            assertTrue(manifestText.contains("app.kaziyami.adapter.kototoro.KototoroPlugin"), "Manifest must point to KototoroPlugin")

            // 2. Must contain core engine and adapter classes
            assertTrue(entries.any { it.contains("KototoroPlugin.class") }, "Must package KototoroPlugin.class")
            assertTrue(entries.any { it.contains("AniyomiSourceAdapter.class") }, "Must package AniyomiSourceAdapter.class")
            assertTrue(entries.any { it.contains("BrowserProfile.class") }, "Must package BrowserProfile.class")

            // 3. Must NOT contain test fixtures or test classes
            assertFalse(entries.any { it.endsWith(".html") }, "Must NOT contain HTML test fixtures")
            assertFalse(entries.any { it.contains("Test.class") || it.contains("Tests.class") }, "Must NOT contain compiled test classes")

            // 4. Must NOT contain keystores or credentials
            assertFalse(entries.any { it.endsWith(".keystore") || it.endsWith(".jks") }, "Must NOT contain keystore files")

            // 5. Must NOT duplicate host classes
            assertFalse(entries.any { it.startsWith("android/") }, "Must NOT bundle android.* host classes")
            assertFalse(entries.any { it.startsWith("okhttp3/") }, "Must NOT bundle okhttp3.* host classes")
        }
    }
}
