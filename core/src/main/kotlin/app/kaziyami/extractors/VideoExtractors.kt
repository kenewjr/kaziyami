package app.kaziyami.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

interface VideoExtractor {
    val name: String
    fun canExtract(url: String): Boolean
    fun extract(url: String, referer: String = "", client: OkHttpClient = OkHttpClient()): List<Video>
    fun extract(url: String, client: OkHttpClient): List<Video> = extract(url, "", client)
}

class DirectVideoExtractor : VideoExtractor {
    override val name = "Direct"

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.contains(".mp4?") || lower.contains(".m3u8?")
    }

    override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
        val effectiveRef = if (referer.isNotBlank()) referer else url
        val headers = Headers.Builder()
            .set("Referer", effectiveRef)
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

    override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
        val embedUrl = if (!url.contains("/e/")) url.replace("/v/", "/e/") else url
        val reqBuilder = Request.Builder().url(embedUrl)
        if (referer.isNotBlank()) {
            reqBuilder.header("Referer", referer)
        }
        val req = reqBuilder.build()
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
    private val hostRegex = Pattern.compile("doodstream\\.com|dood\\.(?:wf|pm|to|so|ws|li)|playmogo\\.(?:com|net|org)|mogo(?:stream)?\\.", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
        val embedUrl = if (!url.contains("/e/")) url.replace("/d/", "/e/") else url
        val reqBuilder = Request.Builder().url(embedUrl)
        val effectiveRef = if (referer.isNotBlank()) referer else "https://nekopoi.care/"
        reqBuilder.header("Referer", effectiveRef)
        reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        val req = reqBuilder.build()
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

        val passReq = Request.Builder()
            .url(passUrl)
            .header("Referer", embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .build()
        val passBody = try {
            client.newCall(passReq).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        val token = "zUEJeL3mUN"
        val finalUrl = passBody + token + "?token=" + passUrl.substringAfterLast("/") + "&expiry=" + System.currentTimeMillis()
        val headers = Headers.Builder().set("Referer", "https://${req.url.host}/").build()
        val hostName = if (url.contains("playmogo", ignoreCase = true) || url.contains("mogo", ignoreCase = true)) "PlayMogo" else "DoodStream"
        return listOf(Video(embedUrl, "$hostName 720p", finalUrl, headers))
    }
}

class VoeExtractor : VideoExtractor {
    override val name = "VOE"
    private val hostRegex = Pattern.compile("voe\\.sx|voe-network\\.net|audaciousdefaulthouse\\.com", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
        val reqBuilder = Request.Builder().url(url)
        if (referer.isNotBlank()) {
            reqBuilder.header("Referer", referer)
        }
        val req = reqBuilder.build()
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

    override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
        val embedUrl = if (!url.contains("embed-")) {
            val id = url.substringAfterLast("/").substringBefore(".")
            "https://www.mp4upload.com/embed-$id.html"
        } else {
            url
        }

        val reqBuilder = Request.Builder().url(embedUrl)
        if (referer.isNotBlank()) {
            reqBuilder.header("Referer", referer)
        }
        val req = reqBuilder.build()
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

    override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
        val reqBuilder = Request.Builder().url(url)
        if (referer.isNotBlank()) {
            reqBuilder.header("Referer", referer)
        }
        val req = reqBuilder.build()
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

    override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
        val reqBuilder = Request.Builder().url(url)
        if (referer.isNotBlank()) {
            reqBuilder.header("Referer", referer)
        }
        val req = reqBuilder.build()
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

    override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
        val effectiveRef = if (referer.isNotBlank()) referer else "https://nekopoi.care/"
        val req = Request.Builder().url(url).header("Referer", effectiveRef).build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) {
            return emptyList()
        }

        val unpacked = unpack(html)
        val hostName = "StreamPoi"
        val fileMatch = Pattern.compile("(?:sources:\\s*\\[\\{['\"]?file['\"]?|['\"]?file['\"]?):\\s*['\"]([^'\"]+)['\"]").matcher(unpacked)
        if (fileMatch.find()) {
            val masterUrl = fileMatch.group(1)
            val headers = Headers.Builder().set("Referer", url).build()
            return listOf(
                Video(url, "$hostName 1080p (HLS)", masterUrl, headers),
                Video(url, "$hostName 720p (HLS)", masterUrl, headers)
            )
        }
        val directMatch = Pattern.compile("['\"]?file['\"]?:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]").matcher(unpacked)
        if (directMatch.find()) {
            val masterUrl = directMatch.group(1)
            val headers = Headers.Builder().set("Referer", url).build()
            return listOf(Video(url, "$hostName HD (HLS)", masterUrl, headers))
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

class HentaiCopExtractor : VideoExtractor {
    override val name = "HentaiCop"
    private val hostRegex = Pattern.compile("hentaicop\\.com|hepidrive\\.com|gdplayer\\.(?:top|to|co|xyz)", Pattern.CASE_INSENSITIVE)

    override fun canExtract(url: String): Boolean = hostRegex.matcher(url).find()

    override fun extract(url: String, referer: String, client: OkHttpClient): List<Video> {
        val effectiveRef = if (referer.isNotBlank()) referer else "https://nontonhentai.net/"
        val req = Request.Builder()
            .url(url)
            .header("Referer", effectiveRef)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
            .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
            .header("Sec-Ch-Ua-Mobile", "?0")
            .header("Sec-Ch-Ua-Platform", "\"Windows\"")
            .header("Sec-Fetch-Dest", "iframe")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "cross-site")
            .build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string().orEmpty() }
        } catch (_: Exception) {
            return emptyList()
        }

        val videos = mutableListOf<Video>()
        val headers = Headers.Builder().set("Referer", url).build()

        var unpackedHtml = html
        val pMatcher = Pattern.compile("""var\s+p\s*=\s*"([^"]+)"""").matcher(html)
        if (pMatcher.find()) {
            try {
                val p = pMatcher.group(1)
                val d1 = p.reversed()
                val d2 = java.util.Base64.getDecoder().decode(d1)
                val d3 = java.net.URLDecoder.decode(String(d2, Charsets.UTF_8), "UTF-8")
                unpackedHtml = d3
            } catch (_: Exception) {}
        }

        val rhsMatcher = Pattern.compile("""var\s+kodeRHS\s*=\s*"([^"]+)"""").matcher(unpackedHtml)
        if (rhsMatcher.find()) {
            try {
                val rhsB64 = rhsMatcher.group(1)
                val rhsBytes = java.util.Base64.getDecoder().decode(rhsB64)
                unpackedHtml += "\n" + String(rhsBytes, Charsets.UTF_8)
            } catch (_: Exception) {}
        }

        val normalized = unpackedHtml.replace("\\/", "/")

        val sourcesPattern = Pattern.compile("""sources:\s*\[(.*?)]""", Pattern.DOTALL)
        val sourcesMatcher = sourcesPattern.matcher(normalized)
        if (sourcesMatcher.find()) {
            val block = sourcesMatcher.group(1)
            val itemPattern = Pattern.compile("""\{(.*?)}""", Pattern.DOTALL)
            val itemMatcher = itemPattern.matcher(block)
            while (itemMatcher.find()) {
                val item = itemMatcher.group(1)
                val fileM = Pattern.compile("""['"]?(?:file|src)['"]?\s*:\s*['"]([^'"]+)['"]""").matcher(item)
                if (fileM.find()) {
                    val streamUrl = fileM.group(1)
                    val labelM = Pattern.compile("""['"]?label['"]?\s*:\s*['"]([^'"]+)['"]""").matcher(item)
                    val label = if (labelM.find()) labelM.group(1) else "HD"
                    videos.add(Video(url, "HentaiCop $label", streamUrl, headers))
                }
            }
        }

        if (videos.isEmpty()) {
            val directPattern = Pattern.compile("""['"]?file['"]?\s*:\s*['"]([^'"]+\.(?:m3u8|mp4)[^'"]*)['"]""")
            val directMatcher = directPattern.matcher(normalized)
            while (directMatcher.find()) {
                videos.add(Video(url, "HentaiCop HD", directMatcher.group(1), headers))
            }
        }

        if (videos.isEmpty()) {
            val videoTagPattern = Pattern.compile("""<source[^>]+src=['"]([^'"]+)['"]""")
            val videoTagMatcher = videoTagPattern.matcher(normalized)
            while (videoTagMatcher.find()) {
                videos.add(Video(url, "HentaiCop HD", videoTagMatcher.group(1), headers))
            }
        }

        return videos.distinctBy { it.videoUrl }
    }
}
