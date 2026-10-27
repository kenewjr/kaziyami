package app.kaziyami.hello

import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.ContentParser
import org.skepsun.kototoro.parsers.model.*
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile

class KototoroHelloContractTest {

    private val dummyContext = object : ContentLoaderContext() {
        override val httpClient: OkHttpClient = OkHttpClient()
        override val cookieJar: CookieJar = CookieJar.NO_COOKIES
    }

    private fun findJar(): File {
        val rootDir = listOf(File("."), File("../.."), File("..")).firstOrNull { File(it, "tools/package_hello_jar.py").exists() }
            ?: File(".")

        val candidates = listOf(
            File(rootDir, "samples/kototoro-hello/build/libs/kaziyami-hello.jar"),
            File(rootDir, "build/kototoro-hello-repo/apk/kaziyami-hello.jar")
        )
        for (f in candidates) {
            if (f.exists()) return f
        }

        // Run package_hello_jar.py if not already present
        val script = File(rootDir, "tools/package_hello_jar.py")
        val pb = ProcessBuilder("python", script.absolutePath)
        pb.directory(rootDir)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val exit = proc.waitFor()
        if (exit != 0) {
            val err = proc.inputStream.reader().readText()
            fail<Unit>("package_hello_jar.py failed with exit $exit: $err")
        }
        for (f in candidates) {
            if (f.exists()) return f
        }
        fail<Unit>("kaziyami-hello.jar not found after running packager")
        throw IllegalStateException()
    }

    @Test
    fun testJarZipInvariants() {
        val jar = findJar()
        assertTrue(jar.exists(), "JAR must exist on disk: ${jar.absolutePath}")

        ZipFile(jar).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()

            // 1. Dalvik bytecode required for Android ART
            assertTrue(entries.contains("classes.dex"), "JAR must contain classes.dex for Android ART")

            // 2. JVM bytecode required for Desktop JVM host
            assertTrue(
                entries.contains("org/skepsun/kototoro/parsers/ContentParserFactoryKt.class"),
                "JAR must contain ContentParserFactoryKt.class"
            )
            assertTrue(
                entries.contains("org/skepsun/kototoro/parsers/model/ContentParserSource.class"),
                "JAR must contain ContentParserSource.class"
            )
            assertTrue(
                entries.contains("app/kaziyami/hello/KaziyamiDemoParser.class"),
                "JAR must contain KaziyamiDemoParser.class"
            )

            // 3. No test classes, fixtures, or keystores
            assertFalse(entries.any { it.endsWith("Test.class") }, "JAR must not contain test classes")
            assertFalse(entries.any { it.endsWith(".jks") || it.endsWith(".keystore") }, "JAR must not contain keystores")

            // 4. No bundled host interfaces (parent delegation)
            assertFalse(entries.contains("org/skepsun/kototoro/parsers/ContentParser.class"), "Host interface ContentParser must not be bundled")
            assertFalse(entries.contains("org/skepsun/kototoro/parsers/ContentLoaderContext.class"), "Host context ContentLoaderContext must not be bundled")
        }
    }

    @Test
    fun testHostReflectionLoadingAndContentServing() = runBlocking {
        val jar = findJar()
        val pluginClassLoader = URLClassLoader(arrayOf(jar.toURI().toURL()), javaClass.classLoader)

        // 1. Reflectively load the source enum
        val sourceEnumClass = pluginClassLoader.loadClass("org.skepsun.kototoro.parsers.model.ContentParserSource")
        assertTrue(sourceEnumClass.isEnum, "ContentParserSource must be an enum")

        val constants = sourceEnumClass.enumConstants
        assertNotNull(constants)
        assertTrue(constants.isNotEmpty(), "ContentParserSource must declare at least one source")

        val demoSource = constants.first { (it as Enum<*>).name == "KAZIYAMI_DEMO" }
        assertNotNull(demoSource)
        val contentSource = demoSource as ContentSource
        assertEquals("all", contentSource.locale)
        assertEquals(ContentType.HENTAI_VIDEO, contentSource.contentType)

        // 2. Reflectively load the factory class
        val factoryClass = pluginClassLoader.loadClass("org.skepsun.kototoro.parsers.ContentParserFactoryKt")
        val newParserMethod = factoryClass.declaredMethods.firstOrNull { m ->
            m.name == "newParser" && m.parameterTypes.size == 2 && m.parameterTypes[1] == ContentLoaderContext::class.java
        }
        assertNotNull(newParserMethod, "Factory must declare newParser(sourceEnum, ContentLoaderContext)")

        // 3. Instantiate parser
        val parser = newParserMethod!!.invoke(null, demoSource, dummyContext) as ContentParser
        assertNotNull(parser)
        assertEquals("demo.kaziyami.local", parser.domain)
        assertTrue(parser.availableSortOrders.contains(SortOrder.POPULARITY))

        // 4. Fetch list (must return 2 titles)
        val list = parser.getList(0, SortOrder.POPULARITY, ContentListFilter())
        assertEquals(2, list.size, "Hello sample must return exactly 2 titles")
        val item1 = list[0]
        assertEquals("Kaziyami Demo Series 1", item1.title)
        assertEquals("/anime/demo-1", item1.url)
        assertEquals(ContentRating.ADULT, item1.contentRating)

        // 5. Fetch details (must return 2 episodes)
        val details = parser.getDetails(item1)
        assertNotNull(details.chapters, "Details must include chapters (episodes)")
        assertEquals(2, details.chapters!!.size, "Details must have 2 episodes")
        val ep1 = details.chapters!![0]
        assertEquals("Episode 1: Awakening", ep1.title)
        assertTrue(ep1.url.endsWith("ep1.mp4"), "Episode 1 URL must point to mp4 stream")

        // 6. Fetch pages (video stream target)
        val pages = parser.getPages(ep1)
        assertEquals(1, pages.size, "Episode should have 1 playable stream page")
        assertEquals(ep1.url, parser.getPageUrl(pages[0]))
    }

    @Test
    fun testRepoIndexSchema() {
        val rootDir = listOf(File("."), File("../.."), File("..")).firstOrNull { File(it, "tools/package_hello_jar.py").exists() }
            ?: File(".")

        val indexFile = File(rootDir, "build/kototoro-hello-repo/index.min.json")
        assertTrue(indexFile.exists(), "Repository index must exist: ${indexFile.absolutePath}")

        val content = indexFile.readText()
        assertTrue(content.startsWith("[") && content.endsWith("]"), "index.min.json must be a JSON array")
        assertTrue(content.contains("\"pkg\":\"app.kaziyami.kototoro.hello\""), "Must contain package ID")
        assertTrue(content.contains("\"apk\":\"kaziyami-hello.jar\""), "Must contain apk file pointer")
        assertTrue(content.contains("\"code\":1"), "Must contain version code")
        assertTrue(content.contains("\"nsfw\":1"), "Must indicate NSFW")

        val apkFile = File(rootDir, "build/kototoro-hello-repo/apk/kaziyami-hello.jar")
        assertTrue(apkFile.exists(), "Target APK file must exist under repo/apk/ layout")
    }
}

