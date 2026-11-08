# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [14.3] - 2026-11-08

### Fixed
- **Host ABI Binary Compatibility (`NoSuchMethodError: GET(String)` Fix)**:
  - Root cause resolved: Purged all vendored/stub copies of host classes (`eu.kanade.*`, `org.skepsun.*`) from production sources (`main`).
  - Compiled directly against official `com.github.aniyomiorg:extensions-lib:14` as `compileOnly` with `android.jar` (API 33).
  - Replaced invalid virtual member calls (`GenericThemeSource.GET(url)`) with real top-level extension functions `eu.kanade.tachiyomi.network.GET` and `eu.kanade.tachiyomi.util.asJsoup`.
  - Isolated working test doubles strictly to test source sets (`core/src/test/kotlin`), ensuring 0 host classes are bundled into release APKs.

### Added
- **Automated Host ABI Linkage Verification in CI**:
  - `verifyNoHostClassesPackaged`: Enforces strict ClassLoader isolation by asserting 0 host classes are bundled into built extension APKs.
  - `verifyHostApiBinding`: Scans APK DEX bytecode and verifies that 100% of method/field calls resolve against the host classpath (walking superclass and interface inheritance chains).
  - Fixture unit tests in `tools/test_verify_host_api_binding.py` validating that missing methods fail the build and valid calls pass.
  - Integrated verification directly into `./gradlew check`.

### Changed
- **Kototoro Video Architecture Realignment**:
  - Discontinued Kototoro JAR parser distribution for video sources. Verified from Kototoro source code that `ContentParser` only models manga pages and novel text; Kototoro delegates all video streaming exclusively to its built-in `AniyomiExtensionLoader`.
  - Designated Variant A (Aniyomi APK repository) as the sole supported distribution path for Kototoro video.
  - Bumped all 6 adult sources to `extVersionCode = 3`, `extVersion = "14.3"`.

## [1.0.0] - 2026-11-07

### Added
- **Official 1.0.0 Baseline**:
  - Unified version baseline `1.0.0` (`extVersionCode = 2`, `libVersion = "14"`) across all 6 adult sources (`Nekopoi`, `IndoHentai`, `NontonHentai`, `Hentaimama`, `JAVSubbed`, `Hanime`).
  - Restored `libVersion = "14"` Aniyomi framework ABI contract (`12..16` range) and formatted APK `versionName` to `14.2` with `<meta-data android:name="aniyomix.extensionLib" />`, ensuring clean installation in both official Aniyomi and Kototoro without loader errors.
- **Kototoro Injekt Decoupling**:
  - Decoupled `GenericThemeSource` from host `Injekt` dependency by explicitly overriding `client` and `headersBuilder()`, eliminating crashes during Kototoro's `SourceTester` execution in `JarExtensionLoader`.
- **Single All-in-One Kototoro Plugin JAR**:
  - Strictly 1 unified distribution archive `kaziyami-all-v1.0.0.jar` (with `kaziyami.jar` alias) containing all 6 sources.
  - Embedded `android:icon="@drawable/ic_launcher"` and raw icon assets directly into the JAR archive and repo metadata for native Kototoro package browser display.
  - Granular per-source enable/disable support in Kototoro via `ContentParserSource`.
  - Multi-bytecode JAR with standard Java JVM `.class` files and Android ART `classes.dex` via D8.
- **SmartDns & Fallback DoH**:
  - `SmartDns` with zero-latency loopback resolution (`127.0.0.1`).
  - Automatic fallback to Cloudflare (`1.1.1.1`) and Google (`8.8.8.8`) DNS-over-HTTPS upon Indonesian ISP sinkhole detection (`SINKHOLE_IPS` / Kominfo / Nawala / TrustPositif).
- **One-Shot Source Bumper CLI**:
  - `./gradlew bump -Psource=<slug>` / `tools/bump_source.py` for automated monotonic extension bumps.
- **Repo-Only Distribution Model**:
  - Client updates served exclusively via GitHub Pages `repo` branch (`index.min.json`), matching upstream Aniyomi/Kototoro practice with zero GitHub Releases binary clutter.

## [0.0.1] - 2026-11-07

### Added
- **Baseline Release v0.0.1**:
  - Unified version baseline `0.0.1` (`versionCode = 1`) across all 6 adult sources (`Nekopoi`, `IndoHentai`, `NontonHentai`, `Hentaimama`, `JAVSubbed`, `Hanime`).
  - Target SDK 34 for Android 14/15 privacy and security compliance.
- **Authentic Website Icons**:
  - Direct official logos and icons from live platforms bundled offline for deterministic builds:
    - Nekopoi: Official cat mascot emblem.
    - IndoHentai: Platform favicon.
    - NontonHentai: High-resolution official site mark.
    - Hentaimama: Official crown/heart icon.
    - JAVSubbed: Official site favicon.
    - Hanime: Official Apple touch icon.
- **Dual Distribution Architecture**:
  - **Variant A (Aniyomi APK)**: Aniyomi extension schema (`repo/index.min.json`).
  - **Variant B (Kototoro JAR)**: Unified plugin suite (`app.kaziyami.kototoro` / `kaziyami-all-v0.0.1.jar`) resolving `GET$default` ABI compatibility for Kototoro Android & Desktop JVM hosts.
- **Auto-Update & Security**:
  - Monotonic version check for seamless in-app updates.
  - Zero identity leaks and zero secrets guaranteed by automated verification suite.

## [1.4.2] - 2026-10-27

### Added
- **Dual Distribution Architecture**:
  - Variant A: Aniyomi extension APKs with Aniyomi-format repository metadata (`repo/index.json`, `repo/index.min.json`, `repo/repo.json`).
  - Variant B: Kototoro JAR parser plugins with `ExtensionStoreIndex` metadata (`repo/jar/index.json`, `repo/jar/index.min.json`, `repo/jar/repo.json`).
- **Kototoro Shared Adapters**:
  - `KototoroParserAdapter` and `KototoroParserRegistry` bridge `KaziyamiSource` to `ContentParser` and `ContentLoaderContext`.
  - Content models mapped to `ContentType.HENTAI_VIDEO` and `ContentRating.ADULT`.
- **Unified Bytecode FAT JARs**:
  - Bundles standard Java `.class` bytecode (for Desktop JVM) and `classes.dex` generated via D8 (for Android ART) in a single archive.
  - Ships both all-in-one suite (`kaziyami-all-v1.4.2.jar`) and 6 individual per-source JARs.
- **Auto-Update & Rollback Pipeline**:
  - Enforces monotonic integer `versionCode` update detection (`isNewerThanInstalled(versionCode)`).
  - SHA-256 and byte size integrity tracking for all published artifacts.
  - Rollback retention keeping the previous 3 versions of every APK and JAR on the release branch.
  - Automated simulation regression tests (`AutoUpdateSimulationTest` and `test_update_simulation.py`).

## [1.4.1] - 2026-10-25

### Added
- **Six Adult Anime Sources**:
  - `Kaziyami: Nekopoi` (`id`): StreamPoi Dean Edwards p.a.c.k.e.r HLS unpacker, PlayMogo, multi-mirror failover.
  - `Kaziyami: IndoHentai` (`id`): Custom SSR card grid parser, Loadvid / XXContent extractors.
  - `Kaziyami: NontonHentai` (`id`): AnimeStream base with Base64 option servers (HentaiCop, HepiDrive, LocalStream).
  - `Kaziyami: Hentaimama` (`en`): DooPlay base with AJAX token video resolution.
  - `Kaziyami: JAVSubbed` (`en`): RetroTube tube-style multi-server base with Streamtape, VOE, and DoodStream extractors.
  - `Kaziyami: Hanime` (`en`): Astro / Vue SSR direct HLS extractor with guest token authorization.
- **Multisrc Engine Bases**: `generic`, `dooplay`, `animestream`, and `retrotube`.
- **Selector Drift Protection**: Ordered primary and alternate selector evaluation with session-level caching.
- **Structured Error Handling**: `SourceParseException` and `ErrorClassifier` with clear user-facing messages.
- **Mirror Resilience**: In-memory `MirrorManager`, manual domain override preferences, and opt-in static `RemoteMirrorFetcher`.
- **Library Safety**: Relative URL storage (`/anime/slug/`) with read-time normalizer surviving domain and protocol migrations.
- **Zero Identity Leakage**: Outgoing requests scrubbed of `"kaziyami"`, `"aniyomi"`, `"tachiyomi"`, or `"extension"` headers.
- **Performance & Caching**: `TtlCache` for in-flight request deduplication and filter caching.
- **Automated CI/CD**: GitHub Actions workflows for continuous integration (`ci.yml`), automated repo publishing (`publish.yml`), scheduled live smoke testing (`smoke.yml`), and weekly structure monitoring (`monitor.yml`).
- **Development Tooling**: Source generator (`scripts/new-source`), icon builder, APK assembler and signer (`tools/build_apks.py`), and secret scanner (`tools/scan_secrets.py`).

### Security
- Automated log scrubbing of cookies, bearer tokens, and signed video stream URLs via `LogScrubber`.
- Static mirror allowlist enforcing strict HTTPS domain validation.
