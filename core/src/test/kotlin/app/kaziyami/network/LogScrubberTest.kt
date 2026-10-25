package app.kaziyami.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogScrubberTest {

    @Test
    fun testCookiesRedacted() {
        val input = "Request outgoing headers: Cookie: cf_clearance=abcd1234efgh; session=xyz987"
        val scrubbed = LogScrubber.scrub(input)
        assertFalse(scrubbed.contains("abcd1234efgh"))
        assertFalse(scrubbed.contains("xyz987"))
        assertTrue(scrubbed.contains("[REDACTED]"))
    }

    @Test
    fun testSignedVideoUrlQueryRedacted() {
        val input = "Loading stream from https://cdn.host.com/master.m3u8?token=secret123&expires=1790000000&sig=abcdef"
        val scrubbed = LogScrubber.scrub(input)
        assertFalse(scrubbed.contains("secret123"))
        assertFalse(scrubbed.contains("abcdef"))
        assertTrue(scrubbed.contains("[REDACTED_QUERY]"))
    }

    @Test
    fun testTokensAndPasswordsRedacted() {
        val input = "Payload auth=topsecretpassword123 and token=my_secret_token"
        val scrubbed = LogScrubber.scrub(input)
        assertFalse(scrubbed.contains("topsecretpassword123"))
        assertFalse(scrubbed.contains("my_secret_token"))
        assertTrue(scrubbed.contains("[REDACTED]"))
    }
}
