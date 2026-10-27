# Kototoro JAR Plugin Format & Specification

**Status**: Verified Ground Truth  
**Date**: October 2026  
**Reference Codebase**: [Kototoro-app/Kototoro](https://github.com/Kototoro-app/Kototoro) (branch `devel`, Apache-2.0), commit `414e58f`  
**Reference Repository**: [skepsun/k-parsers-r](https://github.com/skepsun/k-parsers-r) (branch `repo`, commit `13e127b`)  

---

## 1. Overview & Scope Distinction

Kototoro supports several distinct external source mechanisms:
1. **Kototoro Parser Plugins (type "JAR")**: In scope for Variant B. Compiled against Kototoro's `parser-api` (`ContentParser` or Kotatsu `MangaParser`), packaged as a JAR containing Dalvik bytecode (`classes.dex`), and distributed via an HTTP repository index (`index.min.json`).
2. **TVBox Sources (type "TVBOX")**: Out of scope. Aggregation playlists from Chinese OTT/IPTV apps using MacCMS XML/JSON APIs or spider JARs (`csp_*` / `type=3` / `type=4` QuickJS) handled by Kototoro's `:source-tvbox` module (`MacCmsSourceRuntime.kt`).
3. **CloudStream Plugins (type "CLOUDSTREAM")**: Out of scope. Compiled against CloudStream's plugin runtime (`com.lagradost.cloudstream3`) and packaged as `.cs3` files handled by Kototoro's `:cloudstream-shared` module.
4. **Aniyomi Extensions (type "ANIYOMI")**: In scope for Variant A. Standard Android APKs implementing Aniyomi's `AnimeHttpSource`.

---

## 2. Host-Plugin Interface Contract

Kototoro supports three parser plugin architectures (defined in `org.skepsun.kototoro.parserhost.ParserPluginArchitecture` and `JarExtensionLoader.kt`):
- `KOTOTORO`: Entrypoint `org.skepsun.kototoro.parsers.ContentParserFactoryKt` (Recommended: supports Video, Hentai Video, Novel, Manga).
- `KOTATSU`: Entrypoint `org.koitharu.kotatsu.parsers.MangaParserFactoryKt` (Legacy Manga-only).
- `TSUKI`: Entrypoint `tsuki.MangaParserFactoryKt` (UMA manga parser architecture).

### 2.1 Factory Entrypoint & Reflection Contract
The host dynamically probes and loads the plugin using reflection:
- **Factory Class**: `org.skepsun.kototoro.parsers.ContentParserFactoryKt`
- **Factory Method**:
  ```kotlin
  fun newParser(
      source: org.skepsun.kototoro.parsers.model.ContentParserSource,
      context: org.skepsun.kototoro.parsers.ContentLoaderContext
  ): org.skepsun.kototoro.parsers.ContentParser
  ```
- **Detection Logic** (`JarExtensionLoader.kt:101-110` on Android, `ParserPluginRegistry.kt:219-224` on JVM):
  The host searches for any method starting with `newParser` that accepts 2 parameters where the second parameter is `ContentLoaderContext.class`.
  The first parameter must be an `Enum` representing the sources provided by the plugin.

### 2.2 Source Enumeration
- **Enum Class**: `org.skepsun.kototoro.parsers.model.ContentParserSource`
- The host calls `enumClass.enumConstants` to enumerate all available sources.
- Each enum constant implements `org.skepsun.kototoro.parsers.model.ContentSource`:
  ```kotlin
  public interface ContentSource {
      public val name: String
      public val locale: String
      public val contentType: ContentType
  }
  ```
- **Content Types**:
  `ContentType.HENTAI_VIDEO` and `ContentType.VIDEO` are first-class enum values (`org.skepsun.kototoro.parsers.model.ContentType`).

### 2.3 Descriptors and Service Files
- **No SPI or ServiceLoader file is required**: Kototoro does **NOT** read `META-INF/services/`.
- **No AndroidManifest.xml is required for parser plugins**: `AndroidManifest.xml` was only inspected by `MihonJarInspector` when loading desktop Aniyomi/Mihon source-host jars. Pure Kototoro parser plugins do not need an Android manifest.
- **Broken Sources**: Marked via `@Broken` annotation (`org.skepsun.kototoro.parsers.Broken` or `org.koitharu.kotatsu.parsers.Broken`) placed on parser classes alongside `@ContentSourceParser(name = "...")`.

---

## 3. Packaging & Bytecode Requirements

### 3.1 Dual Runtime Target (Android ART + Desktop JVM)
- **Android ART (Mobile)**:
  - Runtime uses Dalvik/ART (`PluginClassLoader` extending `dalvik.system.DexClassLoader`).
  - **The JAR MUST contain `classes.dex`** at the root of the archive.
  - Reference: In `skepsun/k-parsers-r/repo/apk/plugin.jar`, `jar tf` reveals only `META-INF/MANIFEST.MF` and `classes.dex`.
- **Desktop JVM (Windows, macOS, Linux)**:
  - Kototoro Desktop runs `ParserPluginClassLoader` (standard `java.net.URLClassLoader`).
  - Standard JVM requires Java `.class` files. If given a DEX-only JAR, `ParserPluginInspector.kt:105` throws `ParserPluginFailure.DEX_ONLY` (unless converted via `dex-convert` / `dex2jar`).
- **Kaziyami Dual-Packaging**:
  By bundling **both** `classes.dex` (generated via Android SDK `d8`) and `.class` files (compiled via `jvmTarget = "11"`), the resulting JAR runs natively on Android ART and desktop JVM without conversion steps.

### 3.2 Compilation & D8 Command
```bash
# 1. Compile Kotlin/Java with JVM 11 target
kotlinc -jvm-target 11 ...

# 2. Convert to classes.dex using D8
d8 --release --min-api 21 --lib android.jar --output <build-dir> <compiled-classes...>

# 3. Assemble plugin JAR
jar cvf plugin.jar classes.dex <all-compiled-classes...>
```

---

## 4. Classloader Isolation & Host Libraries

In `JarExtensionLoader.kt` (`PluginClassLoader`):
1. **Parent Delegation (Host ABI)**:
   The host enforces parent-first delegation for all shared `parser-api` interfaces:
   - `org.skepsun.kototoro.parsers.ContentParser`
   - `org.skepsun.kototoro.parsers.ContentLoaderContext`
   - `org.skepsun.kototoro.parsers.model.*` (Content, ContentChapter, ContentPage, ContentType, etc.)
   - `org.skepsun.kototoro.parsers.config.*`
   - `org.skepsun.kototoro.parsers.util.LinkResolver`
2. **Child / Plugin Delegation (Plugin Implementation)**:
   The plugin loads its own unique entrypoint and implementation classes:
   - `org.skepsun.kototoro.parsers.ContentParserFactoryKt`
   - `org.skepsun.kototoro.parsers.model.ContentParserSource`
   - Plugin-specific site/engine classes (`app.kaziyami.*`, `org.skepsun.kototoro.parsers.site.*`).
3. **Provided Host Dependencies**:
   The host application provides OkHttp (`okhttp3.*`), Okio (`okio.*`), jsoup (`org.jsoup.*`), Kotlin standard library (`kotlin.*`), and Kotlin coroutines (`kotlinx.coroutines.*`). These MUST NOT be bundled into the plugin JAR (use `compileOnly`).

---

## 5. Repository Index Schema & Distribution

Kototoro fetches `<baseUrl>/index.min.json` for repositories of type `ExternalExtensionType.JAR` (see `ExtensionRepoService.kt:278-285` decoding `List<ExtensionIndexDto>`).

### 5.1 JSON Schema (`index.min.json`)
```json
[
  {
    "name": "Kaziyami: Adult Anime Suite",
    "pkg": "app.kaziyami.kototoro",
    "version": "1.4.2",
    "code": 2,
    "apk": "kaziyami-all-v1.4.2.jar",
    "lang": "all",
    "nsfw": 1
  }
]
```

### 5.2 Field Definitions
- `name`: Human-readable display name of the package.
- `pkg`: Unique package identifier (e.g. `app.kaziyami.kototoro`).
- `version`: Display version string (e.g. `"1.4.2"`).
- `code`: Monotonically increasing version integer (e.g. `2`).
- `apk`: Filename of the JAR relative to the `<baseUrl>/apk/` directory. Even though the field is named `"apk"`, for JAR repositories Kototoro downloads `${extension.repoUrl}/apk/${extension.archiveName}` (`ExtensionInstallService.kt:72`).
- `lang`: Target language code (e.g. `"all"`, `"id"`, `"en"`).
- `nsfw`: `1` if the package contains adult content, `0` otherwise.

### 5.3 Repository Directory Layout
```text
repo/
├── index.min.json           # Minified repository index
├── index.json               # Formatted repository index (optional/debug)
├── repo.json                # Repository metadata
├── sha256sums.txt           # SHA-256 checksums
└── apk/
    └── kaziyami-all-v1.4.2.jar   # Plugin JAR containing classes.dex
```

---

## 6. Update Detection Lifecycle

1. **Installation Record**:
   When a JAR plugin is installed (`ExtensionInstallService.kt:120-132`):
   - The JAR is copied to `<context.filesDir>/plugins/<pkgName>.jar`.
   - The version code is saved in SharedPreferences `jar_plugin_versions`:
     `putLong(extension.pkgName, extension.versionCode)`
   - `GlobalExtensionManager.initialize(context)` is triggered to reload plugins.
2. **Update Check**:
   During catalog refresh (`UnifiedSourcesViewModel.kt`, `WelcomeViewModel.kt:1458`):
   - Kototoro compares `dto.code` from `index.min.json` against `jar_plugin_versions.getLong(pkgName, -1L)`.
   - If `dto.code > installedVersionCode`, the package state is flagged as `UnifiedSourcePackageState.UPDATE_AVAILABLE`.
   - The UI displays an "Update" button / update badge on the Packages tab.

---

## 7. Verified UI Navigation in Kototoro

Based directly on Kototoro's source code (`SettingsActivity.kt`, `UnifiedSourcesTopBar.kt`, `UnifiedSourcesLists.kt`):

### Path A: In-App Repository Subscription
1. From Explore screen: Tap the **Manage** icon button (top bar).
   *(Alternatively: **Settings** → **Content Sources** → **Manage sources**)*.
2. Select the **"Repositories"** tab (middle tab).
3. Tap the **"+" (Add)** button.
4. In the dialog, select repository type: **JAR**.
5. Enter the repository URL (e.g. `https://raw.githubusercontent.com/kenewjr/kaziyami/repo` or direct link to `index.min.json`).
6. Kototoro fetches `index.min.json`. The package appears in the **"Packages"** tab where you tap **Install**.

### Path B: Direct Local JAR Installation
1. Open **Unified Sources** (**Manage** icon in Explore, or **Settings** → **Content Sources** → **Manage sources**).
2. Select the **"Packages"** tab (third tab).
3. At the top of the package list, tap the **"Import local JAR"** chip button (with `+` icon).
4. The system document picker opens (`application/java-archive`).
5. Select `kaziyami-all-v1.4.2.jar`.
6. Kototoro copies the file to `<filesDir>/plugins/`, reloads `JarExtensionLoader`, and the 6 Kaziyami video sources appear in the **"Sources"** tab.

---

## 8. Fallback / STOP Rule Evaluation

- **Anime/Video Compatibility**:
  `ContentParser` natively supports `ContentType.HENTAI_VIDEO` and `ContentType.VIDEO`. When video items are opened, Kototoro's `AppRouter.openVideo` passes the chapter's stream URL directly to `VideoPlayerActivity`.
- **License Isolation**:
  Kaziyami compiles only against the Apache-2.0 `parser-api` interfaces (`compileOnly`). No GPL code from `kotatsu-parsers` is imported or linked.
- **Android Compatibility**:
  D8-generated `classes.dex` inside the JAR runs on Android ART via `PluginClassLoader`.
- **Conclusion**:
  **Fallback is NOT triggered.** Variant B is fully feasible and compliant.
