package app.kaziyami.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

class LibrarySafetyTest {

    @Test
    fun testStoredUrlMigrationScenarios() {
        val expectedCanonical = "/hentai/kanojo-x-kanojo/"

        // 1. Saved as absolute URL under an old domain
        val oldDomain = "https://nekopoi.care/hentai/kanojo-x-kanojo/"
        assertEquals(expectedCanonical, UrlUtils.normalizeStoredPath(oldDomain))

        // 2. Saved without trailing slash
        val noTrailingSlash = "https://nekopoi.care/hentai/kanojo-x-kanojo"
        assertEquals(expectedCanonical, UrlUtils.normalizeStoredPath(noTrailingSlash))

        // 3. Saved as http insecure
        val httpInsecure = "http://nekopoi.care/hentai/kanojo-x-kanojo/"
        assertEquals(expectedCanonical, UrlUtils.normalizeStoredPath(httpInsecure))

        // 4. Saved with www subdomain
        val withWww = "https://www.nekopoi.care/hentai/kanojo-x-kanojo/"
        assertEquals(expectedCanonical, UrlUtils.normalizeStoredPath(withWww))

        // 5. Saved with tracking parameters
        val withTracking = "https://nekopoi.care/hentai/kanojo-x-kanojo/?utm_source=fb&fbclid=abc12345"
        assertEquals(expectedCanonical, UrlUtils.normalizeStoredPath(withTracking))

        // 6. Saved as pure relative path
        val relative = "/hentai/kanojo-x-kanojo"
        assertEquals(expectedCanonical, UrlUtils.normalizeStoredPath(relative))

        // 7. Resolving migrated entry against new mirror
        val newMirror = "https://nekopoi.app"
        val resolved = UrlUtils.resolve(newMirror, UrlUtils.normalizeStoredPath(oldDomain))
        assertEquals("https://nekopoi.app/hentai/kanojo-x-kanojo/", resolved)
    }

    @Test
    fun testSourceNamesNeverContainKaziyamiPrefix() {
        val sourceIdsFile = File("docs/SOURCE_IDS.md")
        if (sourceIdsFile.exists()) {
            val lines = sourceIdsFile.readLines()
            for (line in lines) {
                if (line.trim().startsWith("|") && line.contains("**")) {
                    val sourceName = line.split("|")[1].replace("*", "").trim()
                    assertFalse(
                        sourceName.contains("Kaziyami", ignoreCase = true),
                        "Source name '$sourceName' in docs/SOURCE_IDS.md must NOT contain 'Kaziyami' branding!"
                    )
                }
            }
        }
    }
}
