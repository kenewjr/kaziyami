package app.kaziyami.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class SourceIdContractTest {

    private fun calculateId(name: String, lang: String, versionStep: Int = 1): Long {
        val key = "${name.lowercase()}/$lang/$versionStep"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) } and Long.MAX_VALUE
    }

    private val permanentRegistry = mapOf(
        ("Nekopoi" to "id") to 7073589375897212735L,
        ("IndoHentai" to "id") to 7369497329911250289L,
        ("NontonHentai" to "id") to 7400791378172011716L,
        ("Hentaimama" to "en") to 8058423919997530503L,
        ("JAVSubbed" to "en") to 9192959167101688940L,
        ("Hanime" to "en") to 2150907185602526076L,
        ("JavSubIndo" to "id") to 3834839640077976573L,
        ("Ohentai" to "en") to 7247837649602913976L
    )

    @Test
    fun testPermanentSourceIdsNeverChange() {
        for ((source, expectedId) in permanentRegistry) {
            val (name, lang) = source
            val calculated = calculateId(name, lang)
            assertEquals(
                expectedId,
                calculated,
                "PERMANENT SOURCE ID MISMATCH for $name ($lang)! Changing source IDs breaks existing user bookmarks."
            )
        }
    }
}
