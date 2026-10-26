package app.kaziyami.network

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Thrown when a host is temporarily paused by the circuit breaker to avoid hammering.
 */
class CircuitBreakerOpenException(
    val host: String,
    val cooldownRemainingMs: Long,
    message: String = "Kaziyami: Connection paused for $host ($cooldownRemainingMs ms cooldown remaining). Failing fast."
) : IOException(message)

/**
 * Host-level circuit breaker implementing CLOSED, OPEN, and HALF_OPEN states.
 */
class HostCircuitBreaker(
    private val failureThreshold: Int = NetworkConfig.CIRCUIT_BREAKER_FAILURE_THRESHOLD,
    private val cooldownMs: Long = NetworkConfig.CIRCUIT_BREAKER_COOLDOWN_MS,
    private val timeProvider: TimeProvider = TimeProvider.SYSTEM
) {
    enum class State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private class HostCircuit(
        var state: State = State.CLOSED,
        var consecutiveFailures: Int = 0,
        var lastFailureTime: Long = 0L,
        var lastError: String = ""
    )

    private val circuits = ConcurrentHashMap<String, HostCircuit>()

    private fun getCircuit(host: String): HostCircuit = circuits.computeIfAbsent(host) { HostCircuit() }

    /**
     * Checks if a request is allowed to proceed.
     * Throws [CircuitBreakerOpenException] if circuit is OPEN and cooldown has not expired.
     */
    fun checkPermission(host: String) {
        val circuit = getCircuit(host)
        val now = timeProvider.currentTimeMillis()

        synchronized(circuit) {
            when (circuit.state) {
                State.CLOSED -> return
                State.OPEN -> {
                    val elapsed = now - circuit.lastFailureTime
                    if (elapsed >= cooldownMs) {
                        circuit.state = State.HALF_OPEN
                        return
                    } else {
                        val remaining = cooldownMs - elapsed
                        throw CircuitBreakerOpenException(host, remaining, "Kaziyami: Host $host is paused due to repeated failures. Cooldown: ${remaining / 1000}s remaining.")
                    }
                }
                State.HALF_OPEN -> return
            }
        }
    }

    /**
     * Records a successful response, resetting failures and transitioning state to CLOSED.
     */
    fun recordSuccess(host: String) {
        val circuit = getCircuit(host)
        synchronized(circuit) {
            circuit.consecutiveFailures = 0
            circuit.state = State.CLOSED
        }
    }

    /**
     * Records a request failure (WAF challenge, connection error, 5xx).
     */
    fun recordFailure(host: String, errorReason: String) {
        val circuit = getCircuit(host)
        val now = timeProvider.currentTimeMillis()
        synchronized(circuit) {
            circuit.consecutiveFailures++
            circuit.lastFailureTime = now
            circuit.lastError = errorReason

            if (circuit.consecutiveFailures >= failureThreshold) {
                circuit.state = State.OPEN
            }
        }
    }

    fun getState(host: String): State {
        val circuit = getCircuit(host)
        val now = timeProvider.currentTimeMillis()
        synchronized(circuit) {
            if (circuit.state == State.OPEN && (now - circuit.lastFailureTime) >= cooldownMs) {
                return State.HALF_OPEN
            }
            return circuit.state
        }
    }

    fun getConsecutiveFailures(host: String): Int {
        val circuit = getCircuit(host)
        return synchronized(circuit) { circuit.consecutiveFailures }
    }
}
