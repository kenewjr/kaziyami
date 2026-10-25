package eu.kanade.tachiyomi.lib.core

import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class LazyImageTest {

    @Test
    fun `extract from data-src`() {
        val html = """<img data-src="/images/thumb.jpg" src="data:image/svg+xml;base64,...">"""
        val el = Jsoup.parse(html).selectFirst("img")!!
        assertThat(LazyImage.extract(el, "https://example.com")).isEqualTo("https://example.com/images/thumb.jpg")
    }

    @Test
    fun `extract from data-lazy-src`() {
        val html = """<img data-lazy-src="https://cdn.example.com/cover.webp" src="blank.gif">"""
        val el = Jsoup.parse(html).selectFirst("img")!!
        assertThat(LazyImage.extract(el, "https://example.com")).isEqualTo("https://cdn.example.com/cover.webp")
    }

    @Test
    fun `extract highest resolution from srcset`() {
        val html = """<img srcset="https://example.com/img-175.webp 175w, https://example.com/img-500.webp 500w" src="data:image/gif;base64,R0lGOD">"""
        val el = Jsoup.parse(html).selectFirst("img")!!
        assertThat(LazyImage.extract(el, "https://example.com")).isEqualTo("https://example.com/img-500.webp")
    }

    @Test
    fun `extract from style background-image`() {
        val html = """<div class="thumb-crop" style="background-image: url('https://nekopoi.care/wp-content/uploads/cover.jpg')"></div>"""
        val el = Jsoup.parse(html).selectFirst("div")!!
        assertThat(LazyImage.extract(el, "https://nekopoi.care")).isEqualTo("https://nekopoi.care/wp-content/uploads/cover.jpg")
    }

    @Test
    fun `extract from noscript fallback`() {
        val html = """<div class="poster"><img src="data:image/svg+xml"><noscript><img src="https://example.com/real.jpg"></noscript></div>"""
        val el = Jsoup.parse(html).selectFirst("div.poster")!!
        assertThat(LazyImage.extract(el, "https://example.com")).isEqualTo("https://example.com/real.jpg")
    }
}
