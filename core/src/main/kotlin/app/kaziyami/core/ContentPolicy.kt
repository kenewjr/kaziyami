package app.kaziyami.core

import eu.kanade.tachiyomi.animesource.model.SAnime
import app.kaziyami.engine.KaziyamiMedia

/**
 * Shared safety and content-filtering layer enforcing strict exclusion of:
 * 1. Underage/minor indicators across English, Indonesian, and Japanese romanization.
 * 2. Non-consensual material, voyeurism, spy/hidden cam, leaks, deepfakes.
 * 3. Live-action / real-person pornography on mixed sites (scope locked to animated 2D/3D).
 */
object ContentPolicy {

    private val MINOR_PATTERNS = listOf(
        // English
        """\b(underage|minor|child|children|pedophil\w*|pedo\w*|kindergarten|elementary\s+school)\b""",
        """\b(loli|lolicon|shotacon|shota)\b""",
        // Indonesian
        """\b(bocil|anak\s+sd|anak\s+smp|anak\s+sekolah|anak\s+kecil|bawah\s+umur)\b""",
        // Japanese Romanization
        """\b(kodomo|shoujo\s+ai|enjo\s+kousai)\b"""
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val NON_CONSENSUAL_AND_LIVE_ACTION_PATTERNS = listOf(
        // Non-consensual / voyeur / leaks / non-consensual acts
        """\b(rape|raping|non-consensual|non\s+consensual|forced\s+sex|voyeur|spy\s+cam|hidden\s+cam|candid|upskirt|peeping|leaked|private\s+leak|deepfake|revenge\s+porn)\b""",
        // Indonesian
        """\b(pemerkosaan|perkosaan|kamera\s+tersembunyi|intip|rekaman\s+rahasia|bocoran\s+pribadi|skandal\s+nyata)\b""",
        // Live action exclusion (enforce animated-only policy)
        """\b(live-action|live\s+action|real\s+life|real\s+person|3d\s+real|真人|cosplay|三次元)\b"""
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val AD_AND_ANNOUNCEMENT_PATTERNS = listOf(
        """\b(advertisement|sponsor|sponsored)\b""",
        """(慾望王座|櫻境物語|erolabs|ero-labs)""",
        """(selamat\s+tahun\s+baru|anniversary|dirgahayu|mengucapkan|donasi\s+server|pemberitahuan)"""
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    fun isAdOrAnnouncement(text: String): Boolean {
        if (text.isBlank()) return false
        return AD_AND_ANNOUNCEMENT_PATTERNS.any { it.containsMatchIn(text) }
    }

    fun isMinorIndicator(text: String): Boolean {
        if (text.isBlank()) return false
        return MINOR_PATTERNS.any { it.containsMatchIn(text) }
    }

    fun isProhibitedContent(text: String): Boolean {
        if (text.isBlank()) return false
        return MINOR_PATTERNS.any { it.containsMatchIn(text) } ||
            NON_CONSENSUAL_AND_LIVE_ACTION_PATTERNS.any { it.containsMatchIn(text) }
    }

    fun isSafeAnime(anime: SAnime): Boolean {
        val title = anime.title.orEmpty()
        val genre = anime.genre.orEmpty()
        val desc = anime.description.orEmpty()
        val url = try { anime.url.orEmpty() } catch (_: Throwable) { "" }
        if (isAdOrAnnouncement(title) || isAdOrAnnouncement(url)) return false
        val lowerUrl = url.lowercase()
        if (lowerUrl.contains("/d.php") || lowerUrl.contains("/out.php") || lowerUrl.contains("ku34bh") || lowerUrl.contains("sadbaguette")) return false
        return !isProhibitedContent(title) && !isProhibitedContent(genre) && !isProhibitedContent(desc)
    }

    fun isSafeMedia(media: KaziyamiMedia): Boolean {
        if (isAdOrAnnouncement(media.title) || isAdOrAnnouncement(media.description) || isAdOrAnnouncement(media.url)) return false
        if (isProhibitedContent(media.title) || isProhibitedContent(media.description)) return false
        if (media.genres.any { isProhibitedContent(it) || isAdOrAnnouncement(it) }) return false
        return true
    }

    fun filterAnimes(animes: List<SAnime>): List<SAnime> = animes.filter { isSafeAnime(it) }

    fun filterMedia(items: List<KaziyamiMedia>): List<KaziyamiMedia> = items.filter { isSafeMedia(it) }
}

