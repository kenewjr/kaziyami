# Kaziyami Troubleshooting Guide

This guide covers common playback issues, anti-bot challenges, network mirror blocks, and how to report issues cleanly without leaking authentication tokens or personal session cookies.

---

## 1. Cloudflare Challenge Pages & Turnstile

Adult anime streaming portals frequently deploy Cloudflare Managed Challenges or Turnstile verification to stop headless scrapers.

### Symptoms
- Error message: `CloudflareChallengeException: Cloudflare challenge detected`.
- HTTP 403 Forbidden or HTTP 503 Service Unavailable returned with `cf-mitigated: challenge` header.

### Resolution Steps
1. Tap the three dots (⋮) in the top-right corner of the anime details screen.
2. Select **Open in WebView**.
3. Wait for the page to load, then solve the Cloudflare CAPTCHA or checkbox.
4. Once the site displays normal content, tap the back button to return to Aniyomi.
5. Pull down to refresh the screen. The clearance cookie (`cf_clearance`) is now stored in Aniyomi's cookie jar, allowing automated playback to resume.

---

## 2. Blocked Mirror Domains & Custom Base URL

Regional Internet Service Providers (e.g. Indonesian Kominfo / TrustPositif DNS filtering) frequently block adult domain names.

### Symptoms
- `java.net.UnknownHostException`
- `java.net.SocketTimeoutException`
- Connection reset by peer (`ECONNRESET`)

### Resolution Steps
1. Refer to [docs/SOURCES.md](SOURCES.md) for the active list of working mirror domains.
2. In Aniyomi, open **Settings** -> **Browse** -> **Anime Extensions**.
3. Tap the affected extension (e.g., `Kaziyami: Nekopoi`).
4. Select **Custom Base URL** / **Mirror Domain Override**.
5. Enter an alternative verified mirror (e.g., replace `https://nekopoi.care` with `https://nekopoi.org`).
6. Tap **Save** and retry loading.

---

## 3. Expiring & Tokenized Video URLs

Certain video host engines (such as StreamPoi or Astro SSR CDNs) generate time-limited query tokens (`?token=...&expires=...`) or restrict playback by IP/Referer.

### Symptoms
- Video stream URL resolves, but the internal player displays `HTTP 403 Forbidden` or `Source Error`.

### Resolution Steps
- Video links are valid only for a short duration (typically 30–120 minutes). If you paused playback or left an episode paused for a prolonged period, back out to the episode list and re-open the episode to generate a fresh stream token.
- Ensure your device is not switching network interfaces (e.g. Wi-Fi to cellular data) during token negotiation, as tokenized CDNs validate the client IP.

---

## 4. Submitting a Bug Report: Safe Log Hygiene

When opening an issue on GitHub, **never paste raw headers, cookies, or authorization tokens**.

### What NOT to Include:
- `Cookie:` header contents (especially `cf_clearance`, `PHPSESSID`, `user_token`).
- Full stream query strings containing secret signatures (`?sign=...`, `?key=...`, `?auth=...`).
- Personal IP addresses or device serial numbers.

### What to Include:
1. **Source Name and Extension Version**: e.g., `Kaziyami: NontonHentai v1.4.1`.
2. **Aniyomi Version**: e.g., `Aniyomi Preview r7821`.
3. **Android OS Version**: e.g., `Android 13 (Tiramisu)`.
4. **Network Type**: Residential Wi-Fi / Mobile Carrier (Telkomsel, Indosat, XL, etc.) / VPN.
5. **Sanitized Error Log**:
   ```text
   Failed to extract video list for: /hentai/sample-episode-1/
   Exception: StreamWishExtractor: player source URL not found in DOM
   HTTP Status: 200 OK
   ```

---

## 5. Install Fails: "There was a problem parsing the package"

### Symptom
When attempting to install a Kaziyami extension APK downloaded from the custom repository branch in Kototoro or Android OS Package Installer, Android aborts with the dialog:
`There was a problem parsing the package` (`INSTALL_PARSE_FAILED_BAD_MANIFEST`).

### Root Causes & Diagnostic Evidence
1. **Raw Text AndroidManifest.xml**:
   The build script packaged `AndroidManifest.xml` as a raw UTF-8 text XML file (`b'<?xml...'`, magic `0x3C 0x3F`) instead of compiled Android Binary XML (`AXML`, magic `RES_XML_TYPE = 0x0003`). When Android's native `ResXMLTree` tries to parse text XML, it misinterprets ASCII `<?xml ve` (`0x6D78` header size, `0x6576206C` chunk size) and fails:
   ```bash
   aapt2 dump badging repo/apk/kaziyami-id.nekopoi-v14.1.apk
   # Output: Bad XML block: header size 28024 or total size 1702240364 is larger than data size 943
   # Output: error: could not identify format of APK.

   aapt2 dump xmltree repo/apk/kaziyami-id.nekopoi-v14.1.apk --file AndroidManifest.xml
   # Output: Bad XML block: header size 28024 or total size 1702240364 is larger than data size 943
   ```
2. **Missing `classes.dex`**:
   The archive contained un-dexed JVM `.class` bytecode files rather than Dalvik Executable (`classes.dex`). Kototoro's converter (`ApkExtensionConverter.kt:17-25`) explicitly validates that `classes.dex` is present and that `AndroidManifest.xml` does not start with `'<'`:
   ```kotlin
   fun isApk(path: Path): Boolean = try {
       ZipFile(path.toFile()).use { zip ->
           val manifest = zip.getEntry("AndroidManifest.xml")
           manifest != null && zip.getEntry("classes.dex") != null &&
               zip.getInputStream(manifest).use { it.read() } != '<'.code
       }
   } catch (_: IOException) { false }
   ```
3. **Missing APK Signature Scheme v2/v3**:
   The APK was signed only with legacy v1 JAR signing via `jarsigner`. Modern Android (Android 11+, API 30+) requires APK Signature Scheme v2 or v3:
   ```bash
   apksigner verify --verbose --print-certs repo/apk/kaziyami-id.nekopoi-v14.1.apk
   # Output: com.android.apksig.apk.MinSdkVersionException: Unable to determine APK's minimum supported Android platform version: malformed binary resource: AndroidManifest.xml
   # Output: Caused by: AndroidBinXmlParser$XmlParserException: No XML chunk in file
   ```
4. **Missing `<uses-sdk>` metadata**:
   No `minSdkVersion` or `targetSdkVersion` was compiled. Android 14 (API 34) blocks apps with `targetSdkVersion < 23` (`INSTALL_FAILED_DEPRECATED_SDK_VERSION`), and Android 15 (API 35) blocks `< 24`.

### Resolution & Platform Compatibility
- The build pipeline now compiles resources and manifests using `aapt2 link`, dexes class files using `d8` targeting `--min-api 21`, aligns 4-byte boundaries using `zipalign`, and signs with `apksigner` enforcing v2 and v3 signatures.
- `minSdkVersion = 21` guarantees support on Android 7.0 (API 24) through Android 15 (API 35).
- `targetSdkVersion = 33` satisfies modern Android target requirements.
