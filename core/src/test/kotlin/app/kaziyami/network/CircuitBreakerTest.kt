package app.kaziyami.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CircuitBreakerTest {

    private class MockTimeProvider(var time: Long = 100_000L) : TimeProvider {
        override fun currentTimeMillis(): Long = time
        override fun sleep(ms: Long) { time += ms }
    }

    @Test
    fun circuitBreakerTransitionsClosedToOpenToHalfOpenToClosed() {
        val mockTime = MockTimeProvider(100_000L)
        val breaker = HostCircuitBreaker(
            failureThreshold = 3,
            cooldownMs = 30_000L,
            timeProvider = mockTime
        )

        val host = "mirror.site"

        // 1. Initial state: CLOSED
        assertEquals(HostCircuitBreaker.State.CLOSED, breaker.getState(host))
        breaker.checkPermission(host) // Should not throw

        // 2. Failures below threshold remain CLOSED
        breaker.recordFailure(host, "Error 1")
        breaker.recordFailure(host, "Error 2")
        assertEquals(HostCircuitBreaker.State.CLOSED, breaker.getState(host))

        // 3. Third failure triggers OPEN
        breaker.recordFailure(host, "Error 3")
        assertEquals(HostCircuitBreaker.State.OPEN, breaker.getState(host))

        // 4. Request while OPEN throws CircuitBreakerOpenException fast
        val ex = assertThrows(CircuitBreakerOpenException::class.java) {
            breaker.checkPermission(host)
        }
        assertEquals(host, ex.host)
        assertTrue(ex.message!!.contains("paused due to repeated failures"))

        // 5. Advance time by 20s (less than 30s cooldown) -> still OPEN
        mockTime.time += 20_000L
        assertEquals(HostCircuitBreaker.State.OPEN, breaker.getState(host))

        // 6. Advance past 30s cooldown -> transitions to HALF_OPEN
        mockTime.time += 15_000L
        assertEquals(HostCircuitBreaker.State.HALF_OPEN, breaker.getState(host))
        breaker.checkPermission(host) // probe allowed

        // 7. Success in HALF_OPEN resets to CLOSED
        breaker.recordSuccess(host)
        assertEquals(HostCircuitBreaker.State.CLOSED, breaker.getState(host))
        assertEquals(0, breaker.getConsecutiveFailures(host))
    }
}
