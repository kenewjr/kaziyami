package app.kaziyami.network

import java.util.concurrent.TimeUnit

/**
 * Pinned network timeouts, rate limit budgets, and concurrency boundaries.
 */
object NetworkConfig {
    const val CONNECT_TIMEOUT_SECONDS: Long = 15
    const val READ_TIMEOUT_SECONDS: Long = 20
    const val WRITE_TIMEOUT_SECONDS: Long = 15

    const val MAX_RETRIES_ON_429: Int = 2
    const val RETRY_BACKOFF_BASE_MS: Long = 1000

    const val MAX_CONCURRENT_REQUESTS: Int = 16
    const val MAX_REQUESTS_PER_HOST: Int = 2
    const val DEFAULT_REQUESTS_PER_PERIOD: Int = 3
    const val DEFAULT_PERIOD_SECONDS: Long = 1
    const val MIN_JITTER_MS: Long = 30
    const val MAX_JITTER_MS: Long = 120

    const val CIRCUIT_BREAKER_FAILURE_THRESHOLD: Int = 4
    const val CIRCUIT_BREAKER_COOLDOWN_MS: Long = 30_000L

    const val WEBVIEW_SOLVER_TIMEOUT_SECONDS: Long = 20L
    const val MAX_REDIRECT_LOOPS: Int = 3

    val DETAIL_PAGE_CACHE_TTL_MS: Long = TimeUnit.MINUTES.toMillis(10)
    val FILTER_OPTIONS_CACHE_TTL_MS: Long = TimeUnit.HOURS.toMillis(1)
    val POPULAR_CACHE_TTL_MS: Long = TimeUnit.MINUTES.toMillis(5)
    val EPISODE_LIST_CACHE_TTL_MS: Long = TimeUnit.MINUTES.toMillis(10)
}
