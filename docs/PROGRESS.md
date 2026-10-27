# Kaziyami Development Progress

## Phase Checklist
- [x] **Phase 0: Research & Evidence Verification** (NSFW sources, Hanime separation, evidence artifacts under `docs/evidence/`, matrix in `docs/SOURCES.md`).
- [x] **Phase 1: Foundation & Shared Architecture** (Scaffold, `core`, network layer, multisrc bases `generic`, `dooplay`, `animestream`, `retrotube`, JSON schema, fixtures).
- [x] **Project Rename: Kaziyami** (Namespaces `app.kaziyami.*`, labels `Kaziyami: <Site>`, repo metadata, branding vectors, loader contract in `docs/COMPATIBILITY.md`, Kototoro analysis in `docs/KOTOTORO.md`, permanent source IDs locked in `docs/SOURCE_IDS.md` + contract tests).
- [x] **Phase 2 & 3: Real Source Implementations & Extractors**
  - [x] 1. `hentaimama` (`en`): DooPlay base, direct MP4 / mirrors, preferences, filters, offline + contract tests.
  - [x] 2. `javsubbed` (`en`): RetroTube base, Streamtape / VOE / Dood extractors, offline + contract tests.
  - [x] 3. `nontonhentai` (`id`): AnimeStream base, HentaiCop / HepiDrive / LocalStream decoders, offline + contract tests.
  - [x] 4. `nekopoi` (`id`): StreamPoi p.a.c.k.e.r HLS extractor (`master.m3u8`), PlayMogo, MirrorManager failover, offline + contract tests.
  - [x] 5. `indohentai` (`id`): Custom SSR card grid parser, Loadvid / XXContent extractors, offline + contract tests.
  - [x] 6. `hanime` (`en`): Astro / Vue SSR direct HLS extractor, guest token auth, offline + contract tests.
- [x] **Phase 4: Tooling, Packaging, CI/CD, Docs** (assembleRelease APK packaging, keytool signing tools, repo.json/index.json generation, GitHub Actions `ci.yml`, `publish.yml`, `smoke.yml`, `dependabot.yml`, docs).
- [x] **Phase 5: Hardening & Resilience**
  - [x] 5.1 Selector drift protection (`SourceConfig`, `SourceParseException`, `SelectorDriftTest`).
  - [x] 5.2 Mirror and domain resilience (Manual domain override preference, `RemoteMirrorFetcher`, `repo/mirrors.json`, `MirrorResilienceTest`).
  - [x] 5.3 Library & history safety (`normalizeStoredPath`, `LibrarySafetyTest`).
  - [x] 5.4 Parser robustness (`ParserFuzzTest`, `MalformedHtmlCorpusTest`).
  - [x] 5.5 Network performance & caching (`TtlCache`, `NetworkConfig`, `ParserBenchmarkTest`, extended `NetworkLeakTest`).
  - [x] 5.6 Security & privacy review (`LogScrubber`, `tools/scan_secrets.py`, CI secret scanning).
  - [x] 5.7 Scheduled structure monitoring (`tools/monitor_sources.py`, `.github/workflows/monitor.yml`).
- [x] **Network Resilience v2: Fewer Challenges, Correct Solving, Clear Failure**
  - [x] Request budget & concurrency: Token bucket rate limiter, concurrency cap (1-2 per host), jitter (`TokenBucketRateLimiter`, `RateLimiterAndJitterTest`).
  - [x] Caching & referer chain: Aggressive TTL caching on popular/latest/details/episodes, browser navigation referer chains (`GenericThemeSource`).
  - [x] Sequential extraction: Prioritize preferred host, stop on first working stream (`ExtractorRegistry.resolveSequential`, `SequentialExtractorTest`).
  - [x] Session & header consistency: BrowserProfile UA / Client Hints validation, stable deterministic header order (`BrowserProfile`, `BrowserProfileTest`).
  - [x] Connection pooling: HTTP/2 with keep-alive connection reuse (`ConnectionReuseTest`).
  - [x] Challenge classification: Classify JS_CHALLENGE, INTERACTIVE_CAPTCHA, RATE_LIMITED, IP_OR_REGION_BLOCK, WAF_OTHER, ORIGIN_DOWN, ISP_BLOCK (`ChallengeClassifier`, `ChallengeClassificationTest`).
  - [x] Circuit breaker: Host-level failure tracking, cooldown, and fast-fail (`HostCircuitBreaker`, `CircuitBreakerTest`).
  - [x] WebView solver reliability: Coalesced concurrent solves, loop guard, timeout handling (`WebViewSolverManager`, `WebViewSolverTest`).
  - [x] Signed video URL handling: Single re-resolution on 403/410 (`VideoResolverHelper`, `SignedVideoResolverTest`).
  - [x] Observability without leaking: Local redacted metrics table (`HostDiagnostics`, `LiveSmokeTest`).
  - [x] Documentation: `docs/ANTIBOT.md` detailing capabilities, deliberate non-features, known limits, and per-source protection matrix.
- [x] **Phase 6: Release, Maintenance, Expansion**
  - [x] 6.1 Release v1.4.1 preparation (`CHANGELOG.md`, `docs/RELEASE_NOTES_DRAFT.md`).
  - [x] 6.2 Device QA kit (`docs/QA_CHECKLIST.md`, `docs/QA_REPORT_TEMPLATE.md`).
  - [x] 6.3 Bug-fix loop (Issue templates for source broken, video playback, episodes, filters; `docs/MAINTENANCE.md`).
  - [x] 6.4 Source expansion pipeline (`scripts/new-source`, `scripts/new_source.py`, `docs/CANDIDATES.md`).
- [x] **Phase 7: Pre-flight Gate**
  - [x] 7.1 Local preflight script (`scripts/preflight.py`, `scripts/preflight.sh`, `scripts/preflight.ps1`, `tools/test_preflight.py`). Toolchain, DNS/ISP blocking detection, mirror HTTP/Cloudflare probing, parser counts, video stream verification, sanitized JSON report.
  - [x] 7.2 Local debug install path (`assembleDebugAll` task generating `build/kaziyami-debug/*.apk`, `scripts/install_debug.py`, `scripts/install-debug.sh`, `scripts/install-debug.ps1`).
  - [x] 7.3 Device setup guide (`docs/DEVICE_SETUP.md`: backup, WebView, untrusted extension flow, logcat dump, rollback).
  - [x] 7.4 Test order & triage table (`docs/QA_CHECKLIST.md` updated with test sequence and first-run failure triage).
  - [x] 7.5 Bug-report bundle (`scripts/collect_diagnostics.py`, wrappers, `tools/test_collect_diagnostics.py` verifying fake cookies, bearer tokens, signed URLs, and IP redaction).
  - [x] 7.6 Acceptance & Verification pass (`assembleDebugAll` verified, `./gradlew test` green, secret scan 0 leaks, preflight & diagnostics unit tests green).

- [x] **Phase 8: Repo Audit Fixes, Dependency Policy, Kototoro Support**
  - [x] 8.1 Audit fixes: Replaced all `kaziyami-extensions` references; custom repo URL is `https://raw.githubusercontent.com/kenewjr/kaziyami/repo/index.min.json`. Cleaned `master` branch by gitignoring generated artifacts (`repo/apk/`, `repo/index.json`, `repo/index.min.json`, `repo/sha256sums.txt`) published on orphan `repo` branch. Verified remote raw index and all 6 APK URLs resolve HTTP 200 (`tools/verify_remote_repo.py`). Removed test key fingerprint; confirmed 0 secrets in git history. Softened unverified fork claims to "Untested". Documented `hhanime.tv` vs `hanime.tv` deep dive in `docs/SOURCES.md`. Fixed `.github/workflows/smoke.yml` permissions.
  - [x] 8.2 Dependency policy: Documented host app runtime library matrix in `docs/STACK.md`. Configured `.github/dependabot.yml` with semver-major ignores for host-provided libraries and weekly batching. Prepared PR #1 closure explanation in `docs/PR1_RESPONSE.md`. Implemented `tools/verify_runtime_compat.py` and added `runtime-compat` CI job.
  - [x] 8.3 Kototoro support: Researched official `Kototoro-app/Kototoro` (`v2.3.2`, Apache-2.0) and parsers repo (`skepsun/kototoro-parsers`, GPL-3.0) with exact citations in `docs/KOTOTORO.md`. Documented licensing decision in `docs/LICENSES.md` (GPL isolation via Apache-2.0 `source-host` reflection bridge). Built platform-neutral engine interfaces (`app.kaziyami.engine.*`) and adapters (`AniyomiSourceAdapter`, `KototoroSourceWrapper`, `KototoroPlugin`). Built `packageKototoroPlugin` Gradle task producing `build/libs/kaziyami-kototoro-all-v1.4.1.jar`. Created shared adapter contract tests, host fakes test, loader smoke test, packaging test, and extended network leak test.
  - [x] 8.4 Documentation: Updated `README.md`, `docs/ARCHITECTURE.md`, `CONTRIBUTING.md`, `docs/COMPATIBILITY.md`, `docs/QA_CHECKLIST.md`, and `docs/DECISIONS.md`.

- [x] **Phase 10: Two Distribution Variants (Variant A APK Repo + Variant B JAR Parser Repo)**
  - [x] 10.1 Kototoro JAR Mechanism Research: Inspected `Kototoro-app/Kototoro` (`414e58f`), documented entrypoints (`ContentParserFactoryKt.newParser`), ClassLoader hierarchies, and licensing in `docs/KOTOTORO.md` and `docs/LICENSES.md`.
  - [x] 10.2 Source ID Stability: Locked 64-bit permanent Kototoro IDs in `docs/SOURCE_IDS.md` with contract tests.
  - [x] 10.3 Shared Engine & Adapters: Defined Kototoro ABI stubs in `core` (`ContentParser`, `ContentLoaderContext`, `ContentParserSource`, `ContentParserFactoryKt`), implemented `KototoroParserAdapter` and `KototoroParserRegistry`.
  - [x] 10.4 Unified Dual-Runtime FAT JARs: `tools/package_kototoro_plugin.py` bundles both standard `.class` files (Desktop JVM) and `classes.dex` via D8 (Android ART) into `kaziyami-all-v*.jar` and 6 per-source JARs.
  - [x] 10.5 Dual Repository Generation: `tools/generate_repo_index.py` produces Aniyomi repo metadata in `repo/` and Kototoro `ExtensionStoreIndex` metadata in `repo/jar/`.
  - [x] 10.6 Build Automation: Added Gradle tasks `assembleAniyomi`, `packageJar`, and `assembleAll`.
  - [x] 10.7 Revised Ground-Truth Proof & "Hello" JAR Sample: Inspected upstream `k-parsers-r` repo and Kototoro `JarExtensionLoader` / `ExtensionRepoService`. Created `docs/KOTOTORO_JAR_FORMAT.md`. Built `samples/kototoro-hello` with deterministic static titles, dual bytecode (`classes.dex` + JVM `.class`), `tools/package_hello_jar.py`, and isolated reflection loader contract test. Implemented `tools/verify_jars.py` and connected `verifyJars` to `check`. Regenerated repo indexes adhering to verified array schema `List<ExtensionIndexDto>` under `<repo>/apk/<file>`. Updated `README.md` with verified Kototoro UI paths (`Explore` -> `Manage` -> `Repositories` / `Packages`).

- [x] **Phase 11: Auto-Update Pipeline for JAR and APK Variants**
  - [x] 11.1 Update Mechanism Research: Documented exact update rule (`isNewerThanInstalled(versionCode)` -> strictly `versionCode > installedVersionCode`), Packages tab UI presentation, on-demand refresh triggers, Extension Groups snapshot behavior, host file replacement (`files/plugins/<pkg>.jar` + `jar_plugin_versions` prefs), and ART DEX requirements with citations in `docs/KOTOTORO.md`.
  - [x] 11.2 Monotonic Versioning & Simulation Tests: Implemented dynamic version extraction from `build.gradle.kts`. Created `AutoUpdateSimulationTest` and `test_update_simulation.py` asserting that release N+1 flags strictly the bumped package, while unchanged, same-version, and downgraded packages remain unflagged.
  - [x] 11.3 Publish Ordering, Integrity & Rollback: Artifacts published before indexes; kept latest 3 versions of all APKs and JARs; SHA-256 and byte sizes embedded in index metadata; `verifyPublishedRepo` validates both variants over HTTPS; automatic rollback reverts index on verification failure.
  - [x] 11.4 Release CI/CD Automation: Configured `.github/workflows/release.yml`, `.github/workflows/bump-and-release.yml`, and updated `.github/workflows/monitor.yml` with deduplicated issue alerts.
  - [x] 11.5 User Documentation: Authored `docs/UPDATING.md` and updated `README.md` and `CHANGELOG.md` ([1.4.2]).

---

## Current Status
- **Overall Status**: Phase 11 complete. Auto-update pipeline, simulation tests, dual-variant publishing, and rollback integrity verified across both Aniyomi and Kototoro targets. All tests 100% green.
- **Last Green Run**: `./gradlew check` (all modules passing), `test_update_simulation.py` (4/4 passed), `test_preflight.py` (7/7 passed), `./gradlew assembleAll` (13 APKs & JARs built and verified).
- **Open Items**: Remote release publish gated on presence of live signing secrets (`KAZIYAMI_KEYSTORE_B64`, etc.). Ready for physical device test.

