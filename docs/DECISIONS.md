# Architectural & Source Implementation Decisions

Format: `[Decision]: [Reason]. Alternative rejected: [Alternative].`

1. **Replace Kuronime and Otakudesu with NontonHentai**: Kuronime/Otakudesu are general-audience anime sites and fail the adult (NSFW) requirement; NontonHentai is verified adult hentai with Indonesian subs using identical AnimeStream engine. Alternative rejected: Keeping Kuronime with an adult disclaimer.
2. **Treat hanime.tv, hhanime.tv, and hanime1.me as distinct platforms**: `hhanime.tv` is a dead squatter domain (HTTP 302 redirect to parked ads); `hanime1.me` is a Chinese-language portal with Cloudflare Managed Challenge; `hanime.tv` is Astro SSR with direct HLS CDN. Alternative rejected: Aliasing all three domains under one extractor.
3. **Keep `eu.kanade.tachiyomi.animesource.*` and `eu.kanade.tachiyomi.animeextension.*`**: The Android extension classloader inside Aniyomi explicitly searches for this package structure and manifest meta-data. Alternative rejected: Renaming root packages to `app.kaziyami.*` which causes Aniyomi loader classnotfound crashes.
4. **Use `app.kaziyami.*` for internal utilities and multisrc modules**: Isolates proprietary networking, rate limiters, anti-bot guards, and extractors from upstream collisions. Alternative rejected: Keeping internal classes under `eu.kanade.tachiyomi.lib.core`.
5. **Lock permanent 64-bit Source IDs**: Derived from MD5 key hash `name/lang/1` matching Aniyomi upstream algorithm; changing them destroys users' bookmarks and watch history. Alternative rejected: Dynamic auto-generated IDs or integer IDs.
6. **Kototoro classified as unsupported architecture**: Kototoro loads internal in-app parsers compiled into the application rather than APK extensions; creating a second codebase now violates the priority ladder. Alternative rejected: Porting sources to Kotatsu parser format.
7. **Use JDK standard `keytool` and `jarsigner` for release packaging**: Enables self-contained APK assembly and SHA-256 fingerprint extraction across Linux, macOS, and Windows without requiring multi-gigabyte Android SDK installations. Alternative rejected: Mandatory Android SDK / Gradle Android plugin dependency.
8. **Store relative URLs in `SAnime.url` and `SEpisode.url`**: Eliminates broken bookmarks when mirror domains change or fail over. Alternative rejected: Storing absolute URLs.
