package eu.kanade.tachiyomi.animeextension.en.hanime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class HanimeHandshakeTest {

    private val source = Hanime()

    @Test
    fun testBase64UrlRoundtrip() {
        val testStrings = listOf(
            "",
            "f",
            "fo",
            "foo",
            "foob",
            "fooba",
            "foobar",
            "Hello, world! Special chars: ?&=+/%~#",
            """{"v":1,"alg":"AES-256-GCM","data":"abc-123_xyz"}"""
        )

        for (str in testStrings) {
            val bytes = str.toByteArray(StandardCharsets.UTF_8)
            val encoded = Hanime.Base64Url.encode(bytes)
            val decoded = Hanime.Base64Url.decode(encoded)
            assertEquals(str, String(decoded, StandardCharsets.UTF_8))
        }
    }

    @Test
    fun testHandshakeCryptoRoundtrip() {
        val secret = "htv-insecure-handshake-v1".toByteArray(StandardCharsets.UTF_8)
        val aad = "htv-insecure-v1".toByteArray(StandardCharsets.UTF_8)
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(secret)
        val keySpec = SecretKeySpec(keyBytes, "AES")

        val payload = """{"timestamp_unix":1700000000,"directive":"htv_player_handshake","slug":"test-slug","csrf_token":"sample-token"}"""
        val iv = ByteArray(12) { it.toByte() }

        // Encrypt
        val encCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encCipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        encCipher.updateAAD(aad)
        val encrypted = encCipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))

        val ciphertext = encrypted.copyOfRange(0, encrypted.size - 16)
        val tag = encrypted.copyOfRange(encrypted.size - 16, encrypted.size)

        val combined = ByteArray(ciphertext.size + tag.size)
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.size)
        System.arraycopy(tag, 0, combined, ciphertext.size, tag.size)

        // Decrypt
        val decCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        decCipher.updateAAD(aad)
        val decryptedBytes = decCipher.doFinal(combined)
        val decryptedText = String(decryptedBytes, StandardCharsets.UTF_8)

        assertEquals(payload, decryptedText)
    }

    @Test
    fun testParseHandshakeSources() {
        val mockJson = """
            {
              "is_preroll_enabled": true,
              "sources": [
                {
                  "src": "",
                  "height": 1080,
                  "label": "1080p",
                  "kind": "promotion"
                },
                {
                  "src": "/hls/3598/wrvyft2b09725hym.m3u8",
                  "height": 720,
                  "label": "720p",
                  "kind": "normal"
                },
                {
                  "src": "/hls/3598/cpw6jksn02sn0v1A.m3u8",
                  "height": 480,
                  "label": "480p",
                  "kind": "normal"
                }
              ]
            }
        """.trimIndent()

        val videos = source.parseHandshakeSources(mockJson)
        assertEquals(2, videos.size)
        assertEquals("Hanime 720p", videos[0].quality)
        assertEquals("https://hanime.tv/hls/3598/wrvyft2b09725hym.m3u8", videos[0].videoUrl)
        assertEquals("Hanime 480p", videos[1].quality)
        assertEquals("https://hanime.tv/hls/3598/cpw6jksn02sn0v1A.m3u8", videos[1].videoUrl)
    }
}
