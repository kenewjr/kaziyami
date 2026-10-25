package app.kaziyami.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UrlUtilsTest {

    @Test
    fun `resolve relative and absolute urls`() {
        val base = "https://example.com/anime/test/"
        assertThat(UrlUtils.resolve(base, "ep1.html")).isEqualTo("https://example.com/anime/test/ep1.html")
        assertThat(UrlUtils.resolve(base, "/root.html")).isEqualTo("https://example.com/root.html")
        assertThat(UrlUtils.resolve(base, "//cdn.example.com/img.jpg")).isEqualTo("https://cdn.example.com/img.jpg")
        assertThat(UrlUtils.resolve(base, "https://other.com/video.mp4")).isEqualTo("https://other.com/video.mp4")
    }

    @Test
    fun `remove trailing slash preserves query`() {
        assertThat(UrlUtils.removeTrailingSlash("https://example.com/path/")).isEqualTo("https://example.com/path")
        assertThat(UrlUtils.removeTrailingSlash("https://example.com/path/?page=2")).isEqualTo("https://example.com/path?page=2")
        assertThat(UrlUtils.removeTrailingSlash("https://example.com/path")).isEqualTo("https://example.com/path")
    }

    @Test
    fun `strip tracking params removes utm and fbclid`() {
        val url = "https://example.com/watch?id=123&utm_source=twitter&utm_medium=social&fbclid=xyz&quality=1080p"
        val cleaned = UrlUtils.stripTrackingParams(url)
        assertThat(cleaned).contains("id=123")
        assertThat(cleaned).contains("quality=1080p")
        assertThat(cleaned).doesNotContain("utm_source")
        assertThat(cleaned).doesNotContain("utm_medium")
        assertThat(cleaned).doesNotContain("fbclid")
    }

    @Test
    fun `unify mirror replaces host`() {
        val url = "https://nekopoi.care/hentai/test-series/?page=1"
        val unified = UrlUtils.unifyMirror(url, "https://nekopoi.app")
        assertThat(unified).isEqualTo("https://nekopoi.app/hentai/test-series/?page=1")
    }

    @Test
    fun `getPath extracts relative path and query for bookmark storage`() {
        assertThat(UrlUtils.getPath("https://nekopoi.care/hentai/episode-1/")).isEqualTo("/hentai/episode-1/")
        assertThat(UrlUtils.getPath("https://nekopoi.care/search?q=test")).isEqualTo("/search?q=test")
        assertThat(UrlUtils.getPath("/relative/path")).isEqualTo("/relative/path")
    }

    @Test
    fun `sameResource matches urls across mirrors and ignores tracking params`() {
        val urlA = "https://nekopoi.care/hentai/series-1/?utm_source=feed"
        val urlB = "https://nekopoi.app/hentai/series-1/?fbclid=12345"
        val urlDifferent = "https://nekopoi.app/hentai/series-2/"

        assertThat(UrlUtils.sameResource(urlA, urlB)).isTrue
        assertThat(UrlUtils.sameResource(urlA, urlDifferent)).isFalse
    }
}
