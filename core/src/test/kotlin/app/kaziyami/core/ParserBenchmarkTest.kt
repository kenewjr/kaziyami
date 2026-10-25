package app.kaziyami.core

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class ParserBenchmarkTest {

    @Test
    fun benchmark500EpisodeParsePerformance() {
        val count = 500
        val sb = StringBuilder(200_000)
        sb.append("<div class='episodelist'><ul>")
        for (i in 1..count) {
            sb.append("<li><a href='/ep-$i/'><span class='epl-num'>Episode $i</span><span class='epl-date'>2024-01-15</span></a></li>")
        }
        sb.append("</ul></div>")
        val html = sb.toString()

        System.gc()
        val beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        val elapsed = measureTimeMillis {
            val doc = Jsoup.parse(html)
            val elements = doc.select(".episodelist ul li")
            assertEquals(count, elements.size)

            val parsed = elements.map { el ->
                val name = el.selectFirst(".epl-num")?.text() ?: ""
                val num = EpisodeNumberParser.parse(name)
                val date = DateParser.parse(el.selectFirst(".epl-date")?.text() ?: "")
                Triple(name, num, date)
            }
            assertEquals(count, parsed.size)
        }

        val afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryDeltaMb = (afterMemory - beforeMemory) / (1024 * 1024)

        println("[+] Benchmark: 500 episodes parsed in ${elapsed}ms (Heap delta: ${memoryDeltaMb}MB)")

        // Sanity thresholds: parsing must finish in under 1000ms and consume < 50MB
        assertTrue(elapsed < 1000, "Parse time exceeded threshold: ${elapsed}ms")
        assertTrue(memoryDeltaMb < 50, "Peak memory exceeded threshold: ${memoryDeltaMb}MB")
    }
}
