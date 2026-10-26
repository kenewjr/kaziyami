package app.kaziyami.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks local, in-memory host diagnostic counters without transmitting telemetry.
 * Formats redacted status summaries for debugging and pre-flight checks.
 */
object HostDiagnostics {

    data class HostStats(
        val host: String,
        var lastChallenge: ChallengeClass = ChallengeClass.NONE,
        val totalRequests: AtomicInteger = AtomicInteger(0),
        val solveAttempts: AtomicInteger = AtomicInteger(0),
        val solveSuccesses: AtomicInteger = AtomicInteger(0),
        val rateLimitHits: AtomicInteger = AtomicInteger(0),
        var activeMirror: String = "",
        var isPaused: Boolean = false
    ) {
        val solveSuccessRate: Double
            get() {
                val attempts = solveAttempts.get()
                if (attempts == 0) return 1.0
                return solveSuccesses.get().toDouble() / attempts
            }
    }

    private val statsMap = ConcurrentHashMap<String, HostStats>()

    fun getStats(host: String): HostStats = statsMap.computeIfAbsent(host) { HostStats(host = host, activeMirror = host) }

    fun recordRequest(host: String) {
        getStats(host).totalRequests.incrementAndGet()
    }

    fun recordChallenge(host: String, classification: ChallengeClass) {
        getStats(host).lastChallenge = classification
    }

    fun recordSolve(host: String, success: Boolean) {
        val s = getStats(host)
        s.solveAttempts.incrementAndGet()
        if (success) {
            s.solveSuccesses.incrementAndGet()
        }
    }

    fun recordRateLimit(host: String) {
        getStats(host).rateLimitHits.incrementAndGet()
    }

    fun updateActiveMirror(host: String, mirrorUrl: String) {
        getStats(host).activeMirror = mirrorUrl
    }

    fun setPaused(host: String, paused: Boolean) {
        getStats(host).isPaused = paused
    }

    /**
     * Produces a clean, sanitized text summary table of all host stats.
     */
    fun getSummaryTable(): String {
        if (statsMap.isEmpty()) return "No host activity recorded."

        val header = String.format("%-20s | %-15s | %-8s | %-10s | %-8s | %-8s", "HOST", "LAST CHALLENGE", "REQUESTS", "RATE LIMITS", "SOLVES", "PAUSED")
        val sep = "-".repeat(header.length)
        val rows = statsMap.values.joinToString("\n") { s ->
            val solves = "${s.solveSuccesses.get()}/${s.solveAttempts.get()}"
            String.format(
                "%-20s | %-15s | %-8d | %-10d | %-8s | %-8b",
                s.host,
                s.lastChallenge.name,
                s.totalRequests.get(),
                s.rateLimitHits.get(),
                solves,
                s.isPaused
            )
        }
        return "$header\n$sep\n$rows"
    }

    fun clear() {
        statsMap.clear()
    }
}
