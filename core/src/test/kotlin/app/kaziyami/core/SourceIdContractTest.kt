package app.kaziyami.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class SourceIdContractTest {

    private fun calculateAniyomiId(name: String, lang: String, versionStep: Int = 1): Long {
        val key = "${name.lowercase()}/$lang/$versionStep"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) } and Long.MAX_VALUE
    }

    private fun calculateKototoroId(name: String, lang: String): Long {
        val key = "kototoro:${name.lowercase()}/$lang"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) } and Long.MAX_VALUE
    }

    private val permanentAniyomiRegistry = mapOf(
        ("Nekopoi" to "id") to 7073589375897212735L,
        ("IndoHentai" to "id") to 7369497329911250289L,
        ("NontonHentai" to "id") to 7400791378172011716L,
        ("Hentaimama" to "en") to 8058423919997530503L,
        ("JAVSubbed" to "en") to 9192959167101688940L,
        ("Hanime" to "en") to 2150907185602526076L,
        ("JavSubIndo" to "id") to 3834839640077976573L,
        ("Ohentai" to "en") to 7247837649602913976L
    )

    private val permanentKototoroRegistry = mapOf(
        ("Nekopoi" to "id") to 5719133896636556881L,
        ("IndoHentai" to "id") to 4404885773423021844L,
        ("NontonHentai" to "id") to 702783498723389045L,
        ("Hentaimama" to "en") to 4363242715237708113L,
        ("JAVSubbed" to "en") to 3548241241858622381L,
        ("Hanime" to "en") to 1681524797754884851L
    )

    @Test
    fun testPermanentAniyomiSourceIdsNeverChange() {
        for ((source, expectedId) in permanentAniyomiRegistry) {
            val (name, lang) = source
            val calculated = calculateAniyomiId(name, lang)
            assertEquals(
                expectedId,
                calculated,
                "PERMANENT ANIYOMI SOURCE ID MISMATCH for $name ($lang)! Changing source IDs breaks existing bookmarks."
            )
        }
    }

    @Test
    fun testPermanentKototoroSourceIdsNeverChange() {
        for ((source, expectedId) in permanentKototoroRegistry) {
            val (name, lang) = source
            val calculated = calculateKototoroId(name, lang)
            assertEquals(
                expectedId,
                calculated,
                "PERMANENT KOTOTORO SOURCE ID MISMATCH for $name ($lang)! Changing Kototoro IDs breaks source stability."
            )
        }
    }
}
