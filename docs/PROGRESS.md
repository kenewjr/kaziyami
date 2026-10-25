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
  - [x] 5.8 Error UX (`ErrorClassifier`, `ErrorClassifierTest`, `docs/HARDENING.md`).
- [x] **Phase 6: Release, Maintenance, Expansion**
  - [x] 6.1 Release v1.4.1 preparation (`CHANGELOG.md`, `docs/RELEASE_NOTES_DRAFT.md`).
  - [x] 6.2 Device QA kit (`docs/QA_CHECKLIST.md`, `docs/QA_REPORT_TEMPLATE.md`).
  - [x] 6.3 Bug-fix loop (Issue templates for source broken, video playback, episodes, filters; `docs/MAINTENANCE.md`).
  - [x] 6.4 Source expansion pipeline (`scripts/new-source`, `scripts/new_source.py`, `docs/CANDIDATES.md`).
- [x] **Final Verification Pass** (clean clone check, contract tests, header leak test, liveSmokeTest table, release index validation, leftover audit).

---

## Current Status
- **Overall Status**: Complete end-to-end pipeline (Phase 0 through Phase 6) verified and hardened.
- **Last Green Run**: `./gradlew clean build test` executed with all actionable tasks passing; `liveSmokeTest` verified all 6 sources reachable.
- **Open Items**: None. All 6 adult source extensions fully implemented with zero identity leaks, permanent IDs locked, schema-valid repo index, and automated CI/CD workflows.
- **Unverified Items**: None. All 6 candidate sources verified live in smoke test suite with HTTP 200 OK.
