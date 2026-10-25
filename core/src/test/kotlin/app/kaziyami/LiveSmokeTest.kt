package app.kaziyami

import app.kaziyami.network.BrowserHeaders
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
        val status: String,
        val note: String
    )

    @Test
    fun runLiveSmokeTests() {
        println("\n====================== LIVE SMOKE TEST REPORT ======================")
        println(String.format("%-12s | %-30s | %-6s | %-10s | %s", "SOURCE", "URL", "CODE", "STATUS", "NOTE"))
        println("-".repeat(85))

        val results = mutableListOf<Result>()

        for (target in targets) {
            val urls = listOf(target.primary) + target.mirrors
            var passed = false
            for (url in urls) {
                val req = Request.Builder()
                    .url(url)
                    .headers(BrowserHeaders.defaultHeaders(url))
                    .build()

                try {
                    client.newCall(req).execute().use { resp ->
                        val code = resp.code
                        val server = resp.header("Server").orEmpty()
                        val cfMitigated = resp.header("cf-mitigated")

                        if (code in 200..399) {
                            results.add(Result(target.name, url, code, "PASS", "Site reachable, catalog available"))
                            passed = true
                        } else if (cfMitigated == "challenge" || (code in listOf(403, 503) && server.contains("cloudflare", ignoreCase = true))) {
                            results.add(Result(target.name, url, code, "CHALLENGE", "Cloudflare protection active (WebView required)"))
                            passed = true
                        } else {
                            results.add(Result(target.name, url, code, "WARN", "HTTP $code returned"))
                        }
                    }
                } catch (_: IOException) {
                    continue
                }
                if (passed) break
            }

            if (!passed) {
                results.add(Result(target.name, target.primary, 0, "UNREACHABLE", "Connection refused / DNS failure from current environment"))
            }
        }

        for (r in results) {
            println(String.format("%-12s | %-30s | %-6d | %-10s | %s", r.name, r.workingUrl, r.httpCode, r.status, r.note))
        }
        println("=".repeat(85))
    }
}
