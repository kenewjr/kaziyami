package app.kaziyami.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Result of a WebView challenge solving attempt.
 */
data class WebViewSolveResult(
    val success: Boolean,
    val finalUrl: String,
    val cookies: List<Cookie> = emptyList(),
    val error: String? = null,
    val isOutdatedWebView: Boolean = false
)

/**
 * Interface for solving challenges through a browser WebView.
 */
interface IWebViewSolver {
    fun solve(url: String, cookieJar: SimpleCookieJar): WebViewSolveResult
}

/**
 * Manages concurrent solving requests, loop protection, and timeouts across hosts.
 * Ensures only one solver runs per host at any time; concurrent callers share the result.
 */
class WebViewSolverManager(
    private val solver: IWebViewSolver,
    private val timeoutSeconds: Long = NetworkConfig.WEBVIEW_SOLVER_TIMEOUT_SECONDS,
    private val maxRedirectLoops: Int = NetworkConfig.MAX_REDIRECT_LOOPS
) {
    private val activeSolves = ConcurrentHashMap<String, CompletableFuture<WebViewSolveResult>>()
    private val redirectCounts = ConcurrentHashMap<String, Int>()

    fun solve(url: String, cookieJar: SimpleCookieJar): WebViewSolveResult {
        val httpUrl = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: $url")
        val host = httpUrl.host

        // 1. Loop guard: detect infinite redirect loops
        val redirects = redirectCounts.compute(host) { _, current -> (current ?: 0) + 1 } ?: 1
        if (redirects > maxRedirectLoops) {
            redirectCounts.remove(host)
            return WebViewSolveResult(
                success = false,
                finalUrl = url,
                error = "Kaziyami: Aborted solving challenge for $host due to redirect loop limit ($maxRedirectLoops)."
            )
        }

        // 2. Coalesce concurrent solves: only one active solver per host
        val future = activeSolves.computeIfAbsent(host) {
            CompletableFuture.supplyAsync {
                try {
                    solver.solve(url, cookieJar)
                } catch (t: Throwable) {
                    val errorMsg = if (t.javaClass.name.contains("MissingWebViewPackage") || t is NoClassDefFoundError) {
                        "Kaziyami: Android System WebView is outdated or missing. Please update Android System WebView / Google Chrome."
                    } else {
                        "Kaziyami: Solver error on $host: ${t.message}"
                    }
                    WebViewSolveResult(
                        success = false,
                        finalUrl = url,
                        error = errorMsg,
                        isOutdatedWebView = t.javaClass.name.contains("MissingWebViewPackage")
                    )
                }
            }
        }

        val result = try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            WebViewSolveResult(
                success = false,
                finalUrl = url,
                error = "Kaziyami: Solving timed out after ${timeoutSeconds}s on $host."
            )
        } catch (e: Exception) {
            WebViewSolveResult(
                success = false,
                finalUrl = url,
                error = "Kaziyami: Failed waiting for solver on $host: ${e.message}"
            )
        } finally {
            activeSolves.remove(host)
        }

        if (result.success) {
            redirectCounts.remove(host)
        }

        return result
    }
}
