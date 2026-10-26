package app.kaziyami.network

import okhttp3.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebViewSolverTest {

    private class MockSolver(
        var delayMs: Long = 0L,
        var shouldSucceed: Boolean = true,
        var throwException: Throwable? = null
    ) : IWebViewSolver {
        val solveCallCount = AtomicInteger(0)

        override fun solve(url: String, cookieJar: SimpleCookieJar): WebViewSolveResult {
            solveCallCount.incrementAndGet()
            if (delayMs > 0) Thread.sleep(delayMs)
            if (throwException != null) throw throwException!!

            if (shouldSucceed) {
                val fakeCookie = Cookie.Builder()
                    .domain("site.com")
                    .name("cf_clearance")
                    .value("solved_token_123")
                    .expiresAt(System.currentTimeMillis() + 3600_000L)
                    .build()
                cookieJar.saveCookies("site.com", listOf(fakeCookie))
                return WebViewSolveResult(
                    success = true,
                    finalUrl = url,
                    cookies = listOf(fakeCookie)
                )
            } else {
                return WebViewSolveResult(
                    success = false,
                    finalUrl = url,
                    error = "Challenge failed"
                )
            }
        }
    }

    @Test
    fun solverSucceedsAndPersistsCookies() {
        val mockSolver = MockSolver(shouldSucceed = true)
        val manager = WebViewSolverManager(mockSolver, timeoutSeconds = 5)
        val cookieJar = SimpleCookieJar()

        val result = manager.solve("https://site.com/anime", cookieJar)
        assertTrue(result.success)
        assertEquals(1, mockSolver.solveCallCount.get())
        assertTrue(cookieJar.hasValidClearance("site.com"))
    }

    @Test
    fun concurrentCallersCoalesceIntoSingleSolve() {
        val mockSolver = MockSolver(delayMs = 100L, shouldSucceed = true)
        val manager = WebViewSolverManager(mockSolver, timeoutSeconds = 5)
        val cookieJar = SimpleCookieJar()

        val latch = CountDownLatch(3)
        val results = mutableListOf<WebViewSolveResult>()

        for (i in 0 until 3) {
            Thread {
                val res = manager.solve("https://site.com/anime", cookieJar)
                synchronized(results) { results.add(res) }
                latch.countDown()
            }.start()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(3, results.size)
        assertTrue(results.all { it.success })
        // ONLY 1 solve invocation was executed across all 3 concurrent callers!
        assertEquals(1, mockSolver.solveCallCount.get())
    }

    @Test
    fun loopGuardAbortsOnExcessiveRedirects() {
        val mockSolver = MockSolver(shouldSucceed = false)
        val manager = WebViewSolverManager(mockSolver, timeoutSeconds = 5, maxRedirectLoops = 2)
        val cookieJar = SimpleCookieJar()

        // 1st attempt
        manager.solve("https://site.com/loop", cookieJar)
        // 2nd attempt
        manager.solve("https://site.com/loop", cookieJar)
        // 3rd attempt exceeds limit 2
        val result3 = manager.solve("https://site.com/loop", cookieJar)
        assertFalse(result3.success)
        assertTrue(result3.error!!.contains("redirect loop limit"))
    }

    @Test
    fun solverTimesOutGracefully() {
        // Solver delays 500ms, manager timeout set to 1s in test if possible,
        // or test timeout with 1s timeout manager and 1500ms delay solver:
        val mockSolver = MockSolver(delayMs = 1200L, shouldSucceed = true)
        val manager = WebViewSolverManager(mockSolver, timeoutSeconds = 1)
        val cookieJar = SimpleCookieJar()

        val result = manager.solve("https://site.com/slow", cookieJar)
        assertFalse(result.success)
        assertTrue(result.error!!.contains("timed out"))
    }
}
