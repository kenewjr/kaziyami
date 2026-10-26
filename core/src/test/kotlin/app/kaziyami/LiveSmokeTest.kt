package app.kaziyami

import app.kaziyami.network.BrowserHeaders
import app.kaziyami.network.ChallengeClassifier
import app.kaziyami.network.ChallengeClass
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

@Tag("live")
class LiveSmokeTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val targets = listOf(
        Target("Nekopoi", "https://nekopoi.care", listOf("https://nekopoi.app", "https://nekopoi.link")),
        Target("IndoHentai", "https://indohentai.net", emptyList()),
        Target("NontonHentai", "https://nontonhentai.net", emptyList()),
        Target("Hentaimama", "https://hentaimama.io", emptyList()),
        Target("JAVSubbed", "https://javsubbed.net", emptyList()),
        Target("Hanime", "https://hanime.tv", listOf("https://guest.freeanimehentai.net"))
    )

    data class Target(val name: String, val primary: String, val mirrors: List<String>)
    data class Result(
        val name: String,
        val workingUrl: String,
        val httpCode: Int,
        val challengeClass: ChallengeClass,
        val requestCount: Int,
        val latencyMs: Long,
        val status: String,
        val note: String
    )

    @Test
    fun runLiveSmokeTests() {
        println("\n================================== LIVE SMOKE & NETWORK RESILIENCE REPORT ==================================")
        println(String.format("%-12s | %-28s | %-5s | %-16s | %-5s | %-6s | %s", "SOURCE", "URL", "CODE", "CHALLENGE CLASS", "REQS", "TTFB", "STATUS"))
        println("-".repeat(108))

        val results = mutableListOf<Result>()

        for (target in targets) {
            val urls = listOf(target.primary) + target.mirrors
            var passed = false
            var attempts = 0

            for (url in urls) {
                attempts++
                val req = Request.Builder()
                    .url(url)
                    .headers(BrowserHeaders.defaultHeaders(url))
                    .build()

                val startTime = System.currentTimeMillis()
                try {
                    client.newCall(req).execute().use { resp ->
                        val latency = System.currentTimeMillis() - startTime
                        val code = resp.code
                        val classification = ChallengeClassifier.classify(resp)

                        if (code in 200..399 && classification.classification == ChallengeClass.NONE) {
                            results.add(Result(target.name, url, code, classification.classification, attempts, latency, "PASS", "Site reachable, catalog available"))
                            passed = true
                        } else if (classification.classification in listOf(ChallengeClass.JS_CHALLENGE, ChallengeClass.INTERACTIVE_CAPTCHA)) {
                            results.add(Result(target.name, url, code, classification.classification, attempts, latency, "CHALLENGE", classification.message))
                            passed = true
                        } else {
                            results.add(Result(target.name, url, code, classification.classification, attempts, latency, "WARN", "HTTP $code: ${classification.message}"))
                        }
                    }
                } catch (_: IOException) {
                    continue
                }
                if (passed) break
            }

            if (!passed) {
                results.add(Result(target.name, target.primary, 0, ChallengeClass.ORIGIN_DOWN, attempts, 0L, "UNREACHABLE", "Connection refused / DNS failure from current environment"))
            }
        }

        for (r in results) {
            println(String.format("%-12s | %-28s | %-5d | %-16s | %-5d | %-4dms | %s (%s)", r.name, r.workingUrl, r.httpCode, r.challengeClass.name, r.requestCount, r.latencyMs, r.status, r.note))
        }
        println("=".repeat(108))
    }
}
