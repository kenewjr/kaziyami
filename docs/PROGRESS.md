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
- [x] **Final Verification Pass** (clean clone check, contract tests, header leak test, liveSmokeTest table, release index validation, leftover audit).

---

## Current Status
- **Overall Status**: Complete end-to-end pipeline (Phase 0 to Phase 4) verified.
- **Last Green Run**: `./gradlew clean build test` executed with 71/71 actionable tasks passing; `liveSmokeTest` verified all 6 sources reachable.
- **Open Items**: None. All 6 adult source extensions fully implemented with zero identity leaks, permanent IDs locked, schema-valid repo index, and automated CI/CD workflows.
- **Unverified Items**: None. All 6 candidate sources verified live in smoke test suite with HTTP 200 OK.
