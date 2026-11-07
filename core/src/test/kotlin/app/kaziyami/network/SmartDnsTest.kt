package app.kaziyami.network

import okhttp3.Dns
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException

class SmartDnsTest {

    @Test
    fun `localhost and IP literals bypass DoH and use system DNS`() {
        val fakeSystemDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return listOf(InetAddress.getByName("127.0.0.1"))
            }
        }

        val smartDns = SmartDns(systemDns = fakeSystemDns, enableDohFallback = false)
        val result = smartDns.lookup("localhost")
        assertEquals(1, result.size)
        assertEquals("127.0.0.1", result[0].hostAddress)
    }

    @Test
    fun `clean system DNS response is returned and cached`() {
        var systemCalls = 0
        val fakeSystemDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                systemCalls++
                return listOf(InetAddress.getByName("104.21.58.10"))
            }
        }

        val smartDns = SmartDns(systemDns = fakeSystemDns, enableDohFallback = false)
        val res1 = smartDns.lookup("example.com")
        val res2 = smartDns.lookup("example.com")

        assertEquals(1, systemCalls) // Cached
        assertEquals("104.21.58.10", res1[0].hostAddress)
        assertEquals(res1, res2)
    }

    @Test
    fun `sinkholed response without fallback throws UnknownHostException`() {
        val fakeSystemDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return listOf(InetAddress.getByName("36.86.63.181")) // TrustPositif sinkhole
            }
        }

        val smartDns = SmartDns(systemDns = fakeSystemDns, enableDohFallback = false)
        assertThrows(UnknownHostException::class.java) {
            smartDns.lookup("blocked-domain.com")
        }
    }
}

