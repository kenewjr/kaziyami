package app.kaziyami.extractors

import app.kaziyami.core.ExtractorRegistry
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SequentialExtractorTest {

    private class ProbeTrackingExtractor(
        override val name: String,
        val domainKeyword: String,
        val shouldSucceed: Boolean
    ) : VideoExtractor {
        val probeCount = AtomicInteger(0)

        override fun canExtract(url: String): Boolean = url.contains(domainKeyword)

        override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
            probeCount.incrementAndGet()
            if (shouldSucceed) {
                return listOf(Video("https://cdn.$domainKeyword/stream.mp4", "$name - 1080p", "https://cdn.$domainKeyword/stream.mp4"))
            }
            return emptyList()
        }
    }

    @Test
    fun stopsAtFirstWorkingExtractorAndDoesNotProbeOthers() {
        val extA = ProbeTrackingExtractor("HostA", "host-a.com", shouldSucceed = true)
        val extB = ProbeTrackingExtractor("HostB", "host-b.com", shouldSucceed = true)

        ExtractorRegistry.register(extB)
        ExtractorRegistry.register(extA)

        val urls = listOf("https://host-a.com/e/123", "https://host-b.com/e/456")
        val videos = ExtractorRegistry.resolveSequential(urls)

        assertEquals(1, videos.size)
        assertTrue(videos[0].videoUrl.orEmpty().contains("host-a.com"))
        // First host succeeded: extA probed once, extB NOT probed!
        assertEquals(1, extA.probeCount.get())
        assertEquals(0, extB.probeCount.get())
    }

    @Test
    fun prioritizesPreferredHostFirst() {
        val extA = ProbeTrackingExtractor("HostA", "host-a.com", shouldSucceed = true)
        val extB = ProbeTrackingExtractor("HostB", "host-b.com", shouldSucceed = true)

        ExtractorRegistry.register(extB)
        ExtractorRegistry.register(extA)

        val urls = listOf("https://host-a.com/e/123", "https://host-b.com/e/456")
        // Preferred host is HostB
        val videos = ExtractorRegistry.resolveSequential(urls, preferredHost = "host-b.com")

        assertEquals(1, videos.size)
        assertTrue(videos[0].videoUrl.orEmpty().contains("host-b.com"))
        // HostB probed first and succeeded: extA NOT probed!
        assertEquals(1, extB.probeCount.get())
        assertEquals(0, extA.probeCount.get())
    }

    @Test
    fun probesSecondHostIfFirstFails() {
        val extFail = ProbeTrackingExtractor("FailingHost", "fail-host.com", shouldSucceed = false)
        val extWorking = ProbeTrackingExtractor("WorkingHost", "work-host.com", shouldSucceed = true)

        ExtractorRegistry.register(extWorking)
        ExtractorRegistry.register(extFail)

        val urls = listOf("https://fail-host.com/e/1", "https://work-host.com/e/2")
        val videos = ExtractorRegistry.resolveSequential(urls)

        assertEquals(1, videos.size)
        assertTrue(videos[0].videoUrl.orEmpty().contains("work-host.com"))
        // Both were probed because first failed
        assertEquals(1, extFail.probeCount.get())
        assertEquals(1, extWorking.probeCount.get())
    }
}
