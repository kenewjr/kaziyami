package app.kaziyami.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

interface VideoExtractor {
    val name: String
    fun canExtract(url: String): Boolean
    fun extract(url: String, client: OkHttpClient): List<Video>
}

class DirectVideoExtractor : VideoExtractor {
    override val name = "Direct"

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.contains(".mp4?") || lower.contains(".m3u8?")
    }

    override fun extract(url: String, client: OkHttpClient): List<Video> {
        val headers = Headers.Builder()
            .set("Referer", url)
            .build()
        val quality = if (url.contains("1080p", ignoreCase = true)) {
            "Direct 1080p"
        } else if (url.contains("720p", ignoreCase = true)) {
            "Direct 720p"
        } else if (url.contains("480p", ignoreCase = true)) {
            "Direct 480p"
        } else {
            "Direct"
        }
        return listOf(Video(url, quality, url, headers))
    }
}

class StreamTapeExtractor : VideoExtractor {
    override val name = "StreamTape"
    private val hostRegex = Pattern.compile("streamtape\\.com|streamta\\.pe|tapecontent\\.net", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, client: OkHttpClient): List<Video> {
        val embedUrl = if (!url.contains("/e/")) url.replace("/v/", "/e/") else url
        val req = Request.Builder().url(embedUrl).build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        // Match Streamtape token concatenation
        val pattern = Pattern.compile("id=['\"]videolink['\"][^>]*>.*?</", Pattern.DOTALL)
        val matcher = pattern.matcher(html)
        var streamUrl: String? = null
        if (matcher.find()) {
            val part = matcher.group(0)
            val linkMatch = Pattern.compile("['\"](//[^'\"]+)['\"]").matcher(part)
            if (linkMatch.find()) {
                streamUrl = "https:" + linkMatch.group(1)
            }
        }
        if (streamUrl == null) {
            val altPattern = Pattern.compile("robotlink['\"]\\s*\\)\\.innerHTML\\s*=\\s*['\"]([^'\"]+)['\"]\\s*\\+\\s*['\"]([^'\"]+)['\"]")
            val altMatch = altPattern.matcher(html)
            if (altMatch.find()) {
                streamUrl = "https:" + altMatch.group(1) + altMatch.group(2)
            }
        }
        if (streamUrl == null) {
            val directMatch = Pattern.compile("id=['\"]robotlink['\"][^>]*>([^<]+)<").matcher(html)
            if (directMatch.find()) {
                streamUrl = directMatch.group(1).trim()
                if (streamUrl.startsWith("//")) streamUrl = "https:$streamUrl"
            }
        }

        val videoUrl = streamUrl ?: return emptyList()
        val headers = Headers.Builder().set("Referer", embedUrl).build()
        return listOf(Video(embedUrl, "StreamTape 720p", videoUrl, headers))
    }
}

class DoodExtractor : VideoExtractor {
    override val name = "DoodStream"
    private val hostRegex = Pattern.compile("doodstream\\.com|dood\\.(?:wf|pm|to|so|ws|li)", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, client: OkHttpClient): List<Video> {
        val embedUrl = if (!url.contains("/e/")) url.replace("/d/", "/e/") else url
        val req = Request.Builder().url(embedUrl).build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        val passPattern = Pattern.compile("/pass_md5/[^'\"\\s<]*")
        val passMatcher = passPattern.matcher(html)
        if (!passMatcher.find()) return emptyList()
        val passRel = passMatcher.group(0)
        val passUrl = req.url.resolve(passRel)?.toString() ?: "https://${req.url.host}$passRel"

        val passReq = Request.Builder().url(passUrl).header("Referer", embedUrl).build()
        val passBody = try {
            client.newCall(passReq).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        val token = "zUEJeL3mUN"
        val finalUrl = passBody + token + "?token=" + passUrl.substringAfterLast("/") + "&expiry=" + System.currentTimeMillis()
        val headers = Headers.Builder().set("Referer", "https://${req.url.host}/").build()
        return listOf(Video(embedUrl, "DoodStream", finalUrl, headers))
    }
}

class VoeExtractor : VideoExtractor {
    override val name = "VOE"
    private val hostRegex = Pattern.compile("voe\\.sx|voe-network\\.net|audaciousdefaulthouse\\.com", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, client: OkHttpClient): List<Video> {
        val req = Request.Builder().url(url).build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        val pattern = Pattern.compile("'hls':\\s*'([^']+)'")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val hlsUrl = matcher.group(1)
            val headers = Headers.Builder().set("Referer", url).build()
            return listOf(Video(url, "VOE 720p", hlsUrl, headers))
        }

        return emptyList()
    }
}

class Mp4UploadExtractor : VideoExtractor {
    override val name = "Mp4Upload"
    private val hostRegex = Pattern.compile("mp4upload\\.com", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, client: OkHttpClient): List<Video> {
        val embedUrl = if (!url.contains("embed-")) {
            val id = url.substringAfterLast("/").substringBefore(".")
            "https://www.mp4upload.com/embed-$id.html"
        } else {
            url
        }

        val req = Request.Builder().url(embedUrl).build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        val pattern = Pattern.compile("player\\.src\\(['\"]([^'\"]+)['\"]")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val videoUrl = matcher.group(1)
            val headers = Headers.Builder().set("Referer", embedUrl).build()
            return listOf(Video(embedUrl, "Mp4Upload HD", videoUrl, headers))
        }
        return emptyList()
    }
}

class StreamWishExtractor : VideoExtractor {
    override val name = "StreamWish"
    private val hostRegex = Pattern.compile("streamwish\\.to|strwish\\.com|wishembed\\.pro|flaswish\\.com", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, client: OkHttpClient): List<Video> {
        val req = Request.Builder().url(url).build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        val pattern = Pattern.compile("sources:\\s*\\[\\{['\"]?file['\"]?:\\s*['\"]([^'\"]+)['\"]")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val videoUrl = matcher.group(1)
            val headers = Headers.Builder().set("Referer", url).build()
            return listOf(Video(url, "StreamWish HD", videoUrl, headers))
        }
        return emptyList()
    }
}

class FilemoonExtractor : VideoExtractor {
    override val name = "Filemoon"
    private val hostRegex = Pattern.compile("filemoon\\.sx|filemoon\\.to", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, client: OkHttpClient): List<Video> {
        val req = Request.Builder().url(url).build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        val pattern = Pattern.compile("(?:sources:\\s*\\[\\{['\"]?file['\"]?|['\"]?file['\"]?):\\s*['\"]([^'\"]+)['\"]")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val videoUrl = matcher.group(1)
            val headers = Headers.Builder().set("Referer", url).build()
            return listOf(Video(url, "Filemoon HD", videoUrl, headers))
        }
        return emptyList()
    }
}

class StreamPoiExtractor : VideoExtractor {
    override val name = "StreamPoi"
    private val hostRegex = Pattern.compile("streampoi\\.com|streamruby\\.(?:net|com)", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, client: OkHttpClient): List<Video> {
        val req = Request.Builder().url(url).header("Referer", "https://nekopoi.care/").build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        val unpacked = unpack(html)
        val fileMatch = Pattern.compile("sources:\\s*\\[\\{file:\\s*['\"]([^'\"]+)['\"]").matcher(unpacked)
        if (fileMatch.find()) {
            val masterUrl = fileMatch.group(1)
            val headers = Headers.Builder().set("Referer", url).build()
            return listOf(
                Video(url, "StreamPoi 1080p (HLS)", masterUrl, headers),
                Video(url, "StreamPoi 720p (HLS)", masterUrl, headers)
            )
        }
        val directMatch = Pattern.compile("file:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]").matcher(unpacked)
        if (directMatch.find()) {
            val masterUrl = directMatch.group(1)
            val headers = Headers.Builder().set("Referer", url).build()
            return listOf(Video(url, "StreamPoi HD (HLS)", masterUrl, headers))
        }

        return emptyList()
    }

    fun unpack(script: String): String {
        val regex = Pattern.compile(
            "eval\\(function\\(p,a,c,k,e,d\\)\\{.*?return p\\}\\('(.*?)',(\\d+),(\\d+),'(.*?)'\\.split\\('\\|'\\)",
            Pattern.DOTALL
        )
        val matcher = regex.matcher(script)
        if (!matcher.find()) return script
        var payload = matcher.group(1)
        val radix = matcher.group(2).toInt()
        val count = matcher.group(3).toInt()
        val symTab = matcher.group(4).split("|")
        for (i in count - 1 downTo 0) {
            val word = if (i < symTab.size && symTab[i].isNotEmpty()) symTab[i] else null
            if (word != null) {
                val key = i.toString(radix)
                payload = payload.replace("\\b$key\\b".toRegex(), word)
            }
        }
        return payload
    }
}

