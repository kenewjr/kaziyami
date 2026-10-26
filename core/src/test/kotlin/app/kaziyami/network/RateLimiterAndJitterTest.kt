package app.kaziyami.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RateLimiterAndJitterTest {

    private class MockTimeProvider(var currentTime: Long = 1000L) : TimeProvider {
        val sleepCalls = mutableListOf<Long>()

        override fun currentTimeMillis(): Long = currentTime

        override fun sleep(ms: Long) {
            sleepCalls.add(ms)
            currentTime += ms
        }
    }

    @Test
    fun tokenBucketDrainsAndWaitsWhenEmpty() {
        val mockTime = MockTimeProvider(1000L)
        val rateLimiter = TokenBucketRateLimiter(
            defaultCapacity = 2.0,
            defaultRefillRatePerSec = 1.0,
            maxConcurrentPerHost = 2,
            minJitterMs = 10,
            maxJitterMs = 20,
            timeProvider = mockTime
        )

        // 1st request: has token
        rateLimiter.acquire("site.com")
        rateLimiter.release("site.com")

        // 2nd request: has token
        rateLimiter.acquire("site.com")
        rateLimiter.release("site.com")

        // 3rd request: bucket empty, must wait for refill
        rateLimiter.acquire("site.com")
        rateLimiter.release("site.com")

        // Verified sleep was called to wait for tokens
        assertTrue(mockTime.sleepCalls.isNotEmpty())
        assertTrue(mockTime.sleepCalls.any { it >= 500L })
    }

    @Test
    fun rateLimiterEnforcesJitterBounds() {
        val mockTime = MockTimeProvider(1000L)
        val minJitter = 40L
        val maxJitter = 80L
        val rateLimiter = TokenBucketRateLimiter(
            defaultCapacity = 10.0,
            defaultRefillRatePerSec = 10.0,
            maxConcurrentPerHost = 10,
            minJitterMs = minJitter,
            maxJitterMs = maxJitter,
            timeProvider = mockTime
        )

        repeat(5) {
            rateLimiter.acquire("site.com")
            rateLimiter.release("site.com")
        }

        val jitters = mockTime.sleepCalls
        assertEquals(5, jitters.size)
        for (j in jitters) {
            assertTrue(j in minJitter..maxJitter, "Jitter $j was outside range [$minJitter, $maxJitter]")
        }
    }

    @Test
    fun rateLimiterLowersBudgetOn429() {
        val mockTime = MockTimeProvider(1000L)
        val rateLimiter = TokenBucketRateLimiter(
            defaultCapacity = 4.0,
            defaultRefillRatePerSec = 4.0,
            timeProvider = mockTime
        )

        assertEquals(4.0, rateLimiter.getAvailableTokens("target.com"))
        rateLimiter.lowerBudget("target.com")
        assertTrue(rateLimiter.getAvailableTokens("target.com") <= 2.0)
    }

    @Test
    fun rateLimiterEnforcesConcurrencyCap() {
        val rateLimiter = TokenBucketRateLimiter(
            defaultCapacity = 10.0,
            defaultRefillRatePerSec = 10.0,
            maxConcurrentPerHost = 2,
            minJitterMs = 0,
            maxJitterMs = 0
        )

        val inFlight = AtomicInteger(0)
        val maxObservedInFlight = AtomicInteger(0)
        val latch = CountDownLatch(4)

        for (i in 0 until 4) {
            Thread {
                rateLimiter.acquire("host.com")
                val current = inFlight.incrementAndGet()
                maxObservedInFlight.updateAndGet { maxOf(it, current) }
                Thread.sleep(50)
                inFlight.decrementAndGet()
                rateLimiter.release("host.com")
                latch.countDown()
            }.start()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertTrue(maxObservedInFlight.get() <= 2, "Concurrency exceeded cap 2: observed ${maxObservedInFlight.get()}")
    }
}
