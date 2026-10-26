# Contributing to Kaziyami

Thank you for contributing to Kaziyami extensions. To maintain high code quality, loader compatibility, and anti-bot network safety, please follow these guidelines.

---

## 1. Naming & Compatibility Rules (Crucial)

- **Extension Label (`android:label`)**: Must follow `"Kaziyami: <Site>"` format (e.g. `"Kaziyami: Nekopoi"`).
- **Source Name (`AnimeSource.name`)**: Must remain unbranded (e.g. `"Nekopoi"`). **NEVER prefix source names with "Kaziyami:"**. Changing a source name breaks the permanent 64-bit Source ID hash and invalidates user bookmarks.
- **Loader Contracts**:
  - Never alter package imports from `eu.kanade.tachiyomi.animesource.*`.
  - Extension APK package names must strictly follow `eu.kanade.tachiyomi.animeextension.<lang>.<name>`.
  - Internal Kaziyami shared utilities must reside under `app.kaziyami.*`.
- **Zero Network Identity Leak**:
  - Never send `"kaziyami"`, `"aniyomi"`, `"tachiyomi"`, or `"extension"` in outgoing HTTP headers, User-Agent, referers, or query parameters. Outgoing requests must stay indistinguishable from modern standard desktop Chromium.

---

## 2. Adding a New Site in ~15 Lines (Existing Engine)

When adding a site powered by an existing engine (such as `AnimeStream`, `DooPlay`, or `RetroTube`), inherit the multisrc base and configure selectors:

```kotlin
package eu.kanade.tachiyomi.animeextension.id.mysite

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.InMemorySourcePreferences
import eu.kanade.tachiyomi.animesource.SourcePreferences
import app.kaziyami.multisrc.animestream.AnimeStreamTheme
import app.kaziyami.multisrc.generic.SelectorsConfig

class MySite(
    preferences: SourcePreferences = InMemorySourcePreferences()
) : AnimeStreamTheme(
    name = "MySite",
    lang = "id",
    baseUrl = "https://mysite.com",
    mirrors = listOf("https://mysite.com", "https://mysite.org"),
    popularPath = "/anime/?order=popular&page=%d",
    latestPath = "/anime/?order=update&page=%d",
    searchPath = "/?s=%s&page=%d",
    customSelectors = SelectorsConfig(
        item = "div.listupd article.bs",
        itemTitle = ".title, h2",
        itemThumbnail = "img"
    ),
    preferences = preferences
)
```

In `src/<lang>/<site>/build.gradle.kts`:
```kotlin
val extName = "MySite"
val extClass = "eu.kanade.tachiyomi.animeextension.id.mysite.MySite"
val extVersionCode = 1
val libVersion = "1.4"
val isNsfw = true

extra["extName"] = extName
extra["extClass"] = extClass
extra["extVersionCode"] = extVersionCode
extra["libVersion"] = libVersion
extra["isNsfw"] = isNsfw

dependencies {
    implementation(project(":core"))
    implementation(project(":lib-multisrc:animestream"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
```

---

## 3. Adding a New Engine Base

If a site uses an unhandled CMS or custom player architecture:
1. Create a module under `lib-multisrc/<theme-name>/`.
2. Inherit from [GenericThemeSource](lib-multisrc/generic/src/main/kotlin/app/kaziyami/multisrc/generic/GenericThemeSource.kt).
3. Override engine-specific logic (AJAX player decoders, Base64 unpacked embeds, or API token exchanges).
4. Provide comprehensive unit tests with offline mock fixtures.

---

## 4. Refreshing HTML Fixtures

To ensure offline unit tests reflect actual website DOM trees, refresh HTML fixtures using the fixture utility:

```bash
python tools/refresh_fixtures.py
```
Always sanitize fixtures to remove user tokens, cookies, tracking identifiers, or session IDs before committing.

---

## 5. Running Tests Locally

### Offline Unit & Contract Tests
```bash
./gradlew check jacocoTestReport
```

### JDK 17 Configuration & Overrides
The build requires JDK 17. **Never commit local file paths or `org.gradle.java.home` into the repository's `gradle.properties`**. Machine-specific JDK overrides belong exclusively in user-level `~/.gradle/gradle.properties`:
```properties
org.gradle.java.home=/path/to/your/jdk-17
```

### Live Smoke Integration Tests
Hits live endpoints to verify `popular -> details -> episodes -> video stream 200/206`:
```bash
./gradlew liveSmokeTest
```

---

## 6. Version Bumping Rules

Every pull request that modifies code in an extension or any shared library it depends on **must increment `extVersionCode`**:
- Changes to `core/` or `lib-multisrc/generic/` require bumping **all 6 extensions**.
- Changes to `lib-multisrc/animestream/` require bumping `nontonhentai`.
- Changes to `src/<lang>/<source>/` require bumping only that extension.

To automatically calculate and bump affected extensions:
```bash
./gradlew bumpChanged
```

CI will fail if code was altered without bumping `extVersionCode` (`./gradlew checkBump`).

---

## 7. Dual Adapter Availability (Aniyomi + Kototoro)

Because Kaziyami sources are built on a platform-neutral engine and standard `AnimeHttpSource` ABI:

1. **Automatic Dual Compatibility**: Every newly created source is automatically compatible with **both Aniyomi and Kototoro (v2.3+)** out of the box. No secondary parser rewrite is necessary.
2. **In Aniyomi**: The source builds as a standard extension APK (`eu.kanade.tachiyomi.animeextension.<lang>.<name>`) indexed in `index.min.json`.
3. **In Kototoro**:
   - **Direct APK Import**: Kototoro's embedded `source-host` runtime and `dex-convert` bridge automatically convert and run the extension APK.
   - **Suite Plugin JAR**: Running `./gradlew packageKototoroPlugin` bundles all active sources into `build/libs/kaziyami-kototoro-all-v1.4.1.jar`, loadable via **Settings -> Remote Sources -> Import Plugin JAR**.
