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

        fun findJar(): File? {
            val searchDirs = listOf(File(rootDir, "build/libs"), File(rootDir, "repo/jar"))
            for (dir in searchDirs) {
                if (dir.exists()) {
                    val match = dir.listFiles { f ->
                        f.name.startsWith("kaziyami-all-v") && f.name.endsWith(".jar")
                    }?.firstOrNull()
                    if (match != null) return match
                }
            }
            return null
        }

        var jarFile = findJar()
        if (jarFile == null) {
            val script = File(rootDir, "tools/package_kototoro_plugin.py")
            val pb = ProcessBuilder("python", script.absolutePath)
            pb.directory(rootDir)
            pb.redirectErrorStream(true)
            val process = pb.start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val output = process.inputStream.reader().readText()
                System.err.println("package_kototoro_plugin.py failed (exit $exitCode):\n$output")
            }
            jarFile = findJar()
        }

        assertNotNull(jarFile, "Kototoro plugin JAR must be generated under build/libs/ or repo/jar/")
        assertTrue(jarFile!!.exists(), "Kototoro plugin JAR must exist: ${jarFile.absolutePath}")

        ZipFile(jarFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()

            // 1. Must contain AndroidManifest.xml
            assertTrue(entries.contains("AndroidManifest.xml"), "Must contain text AndroidManifest.xml for MihonJarInspector")
            val manifestText = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).reader().readText()
            assertTrue(manifestText.contains("tachiyomi.animeextension.class"), "Manifest must declare tachiyomi.animeextension.class")
            assertTrue(manifestText.contains("app.kaziyami.adapter.kototoro.KototoroPlugin"), "Manifest must point to KototoroPlugin")

            // 2. Must contain Dalvik executable (classes.dex) for Android ART
            assertTrue(entries.contains("classes.dex"), "Must package classes.dex for Android ART DexClassLoader")

            // 3. Must contain core engine, adapter classes, and Kototoro factory
            assertTrue(entries.any { it.contains("KototoroPlugin.class") }, "Must package KototoroPlugin.class")
            assertTrue(entries.any { it.contains("AniyomiSourceAdapter.class") }, "Must package AniyomiSourceAdapter.class")
            assertTrue(entries.any { it.contains("BrowserProfile.class") }, "Must package BrowserProfile.class")
            assertTrue(entries.any { it.contains("ContentParserFactoryKt.class") }, "Must package ContentParserFactoryKt.class")
            assertTrue(entries.any { it.contains("ContentParserSource.class") }, "Must package ContentParserSource.class")

            // 4. Must NOT contain test fixtures or test classes
            assertFalse(entries.any { it.endsWith(".html") }, "Must NOT contain HTML test fixtures")
            assertFalse(entries.any { it.contains("Test.class") || it.contains("Tests.class") }, "Must NOT contain compiled test classes")

            // 5. Must NOT contain keystores or credentials
            assertFalse(entries.any { it.endsWith(".keystore") || it.endsWith(".jks") }, "Must NOT contain keystore files")

            // 6. Must NOT duplicate host classes
            assertFalse(entries.any { it.startsWith("android/") }, "Must NOT bundle android.* host classes")
            assertFalse(entries.any { it.startsWith("okhttp3/") }, "Must NOT bundle okhttp3.* host classes")
            assertFalse(entries.any { it.startsWith("org/jsoup/") }, "Must NOT bundle jsoup host classes")
            assertFalse(entries.any { it.startsWith("org/skepsun/kototoro/parsers/ContentParser.class") }, "Must NOT bundle parser-api host interface")
            assertFalse(entries.any { it.startsWith("org/skepsun/kototoro/parsers/ContentLoaderContext.class") }, "Must NOT bundle parser-api host context")
        }
    }
}
