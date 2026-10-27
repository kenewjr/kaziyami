# Licensing Architecture & Module Compatibility

## 1. Licensing Matrix by Module

| Module / Component | License | Compatibility Rationale |
|---|---|---|
| `kaziyami-engine` (Platform-Neutral Core) | **MIT** | Pure Kotlin/JVM domain models, HTTP client abstraction, parsing utilities, selectors DSL, token bucket rate limiter, and video extractors. Zero dependencies on Android SDK, Aniyomi, or Kotatsu/Kototoro GPL classes. |
| `lib-multisrc/*` (Theme Bases) | **MIT** | Generic, DooPlay, AnimeStream, and RetroTube scrapers. Built on `kaziyami-engine`. |
| `adapter-aniyomi` (Aniyomi Extension Contract - Variant A) | **MIT** | Implements `AnimeHttpSource` and `ParsedAnimeHttpSource` from Aniyomi API. Compatible with Aniyomi's Apache 2.0 / MIT ecosystem. |
| `adapter-kototoro-jar` (Kototoro Parser Plugin - Variant B) | **MIT** | Implements Kototoro's `ContentParser` and `ContentSource` interface contracts. Uses Apache-2.0 `parser-api` interfaces solely as `compileOnly` stubs/interfaces. Zero runtime bundling of upstream GPL code. |
| Distribution Artifacts (`*.apk`, `*.jar`) | **MIT** | Clean binary distributions without GPL copyleft contamination. |

---

## 2. Kototoro & Kotatsu GPL Isolation

- **Upstream Context**:
  - **Kototoro** host application is licensed under **Apache-2.0** (`Kototoro-app/Kototoro` LICENSE; Copyright 2023-2026 Skepsun / Kototoro contributors).
  - Kototoro's `parser-api` module is licensed under **Apache-2.0**.
  - Kotatsu parser collection (`kotatsu-parsers` / `kotatsu-parsers-redo`) is licensed under **GPL-3.0** (Copyright 2018-2023 Koitharu / Kotatsu contributors).
- **Kaziyami Isolation Boundary**:
  1. Kaziyami targets Kototoro's native `ContentParser` architecture (`org.skepsun.kototoro.parsers.*`), NOT Kotatsu's `MangaParser` architecture (`org.koitharu.kotatsu.parsers.*`).
  2. Kaziyami does **NOT** import, copy, or link GPL classes from `kotatsu-parsers`.
  3. `adapter-kototoro-jar` compiles against the Apache-2.0 `ContentParser` / `ContentSource` ABI interfaces using `compileOnly` linkage. The host application provides the runtime classes.
  4. All scraper implementations, selectors, and network handlers reside in the MIT-licensed `kaziyami-engine`.
  5. Consequently, the entire Kaziyami codebase and both distribution variants (APK and JAR) remain cleanly licensed under **MIT**.
