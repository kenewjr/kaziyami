package app.kaziyami.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Interface allowing clock injection for deterministic, instantaneous offline tests.
 */
interface TimeProvider {
    fun currentTimeMillis(): Long
    fun sleep(ms: Long)

    companion object {
        val SYSTEM = object : TimeProvider {
            override fun currentTimeMillis(): Long = System.currentTimeMillis()
            override fun sleep(ms: Long) {
                if (ms > 0) Thread.sleep(ms)
            }
        }
    }
}

/**
 * Token bucket rate limiter with concurrency limits and randomized jitter per host.
 * Prevents bursting requests that trigger automated WAF challenges.
 */
class TokenBucketRateLimiter(
    private val defaultCapacity: Double = NetworkConfig.DEFAULT_REQUESTS_PER_PERIOD.toDouble(),
    private val defaultRefillRatePerSec: Double = (NetworkConfig.DEFAULT_REQUESTS_PER_PERIOD.toDouble() / NetworkConfig.DEFAULT_PERIOD_SECONDS),
    private val maxConcurrentPerHost: Int = NetworkConfig.MAX_REQUESTS_PER_HOST,
    private val minJitterMs: Long = NetworkConfig.MIN_JITTER_MS,
    private val maxJitterMs: Long = NetworkConfig.MAX_JITTER_MS,
    private val timeProvider: TimeProvider = TimeProvider.SYSTEM
) {
    private class HostState(
        var capacity: Double,
        var refillRatePerSec: Double,
        val maxConcurrent: Int,
        var tokens: Double,
        var lastRefill: Long
    ) {
        val concurrencySemaphore = Semaphore(maxConcurrent, true)
    }

    private val hostStates = ConcurrentHashMap<String, HostState>()

    private fun getHostState(host: String): HostState {
        val now = timeProvider.currentTimeMillis()
        return hostStates.computeIfAbsent(host) {
            HostState(
                capacity = defaultCapacity,
                refillRatePerSec = defaultRefillRatePerSec,
                maxConcurrent = maxConcurrentPerHost,
                tokens = defaultCapacity,
                lastRefill = now
            )
        }
    }

    /**
     * Acquires permission to make a request to the given host.
     * Enforces concurrency cap, drains 1 token from bucket (waiting if necessary), and adds randomized jitter.
     */
    fun acquire(host: String) {
        val state = getHostState(host)

        // 1. Enforce strict concurrency cap per host
        state.concurrencySemaphore.acquire()

        try {
            // 2. Token bucket check and refill
            var acquiredToken = false
            while (!acquiredToken) {
                val waitTimeMs = synchronized(state) {
                    val now = timeProvider.currentTimeMillis()
                    val elapsedMs = now - state.lastRefill
                    if (elapsedMs > 0) {
                        val addedTokens = (elapsedMs / 1000.0) * state.refillRatePerSec
                        state.tokens = minOf(state.capacity, state.tokens + addedTokens)
                        state.lastRefill = now
                    }

                    if (state.tokens >= 1.0) {
                        state.tokens -= 1.0
                        acquiredToken = true
                        0L
                    } else {
                        val deficit = 1.0 - state.tokens
                        ((deficit / state.refillRatePerSec) * 1000.0).toLong().coerceAtLeast(10L)
                    }
                }

                if (!acquiredToken && waitTimeMs > 0) {
                    timeProvider.sleep(waitTimeMs)
                }
            }

            // 3. Add randomized micro-jitter to prevent mechanical request cadence
            val jitter = if (maxJitterMs > minJitterMs) {
                Random.nextLong(minJitterMs, maxJitterMs)
            } else {
                minJitterMs
            }
            if (jitter > 0) {
                timeProvider.sleep(jitter)
            }
        } catch (e: Throwable) {
            state.concurrencySemaphore.release()
            throw e
        }
    }

    /**
     * Releases the concurrency slot after request completion.
     */
    fun release(host: String) {
        val state = hostStates[host] ?: return
        state.concurrencySemaphore.release()
    }

    /**
     * Lowers host budget upon receiving HTTP 429/1015 for the rest of the session.
     */
    fun lowerBudget(host: String) {
        val state = getHostState(host)
        synchronized(state) {
            state.capacity = (state.capacity * 0.5).coerceAtLeast(1.0)
            state.refillRatePerSec = (state.refillRatePerSec * 0.5).coerceAtLeast(0.5)
            state.tokens = minOf(state.tokens, state.capacity)
        }
    }

    /**
     * Inspects current token count for diagnostics.
     */
    fun getAvailableTokens(host: String): Double {
        val state = hostStates[host] ?: return defaultCapacity
        return synchronized(state) { state.tokens }
    }
}
