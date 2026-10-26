# Licensing Architecture & Module Compatibility

## 1. Licensing Matrix by Module

| Module / Component | License | Compatibility Rationale |
|---|---|---|
| `kaziyami-engine` (Platform-Neutral Core) | **MIT** | Pure Kotlin/JVM domain models, HTTP client abstraction, parsing utilities, selectors DSL, token bucket rate limiter, and video extractors. Zero dependencies on Android SDK, Aniyomi, or Kotatsu/Kototoro GPL classes. |
| `lib-multisrc/*` (Theme Bases) | **MIT** | Generic, DooPlay, AnimeStream, and RetroTube scrapers. Built on `kaziyami-engine`. |
| `adapter-aniyomi` (Aniyomi Extension Contract) | **MIT** | Implements `AnimeHttpSource` and `ParsedAnimeHttpSource` from Aniyomi API. Compatible with Aniyomi's Apache 2.0 / MIT ecosystem. |
| `adapter-kototoro` (Kototoro Plugin Bridge) | **MIT** / **Apache-2.0** Compatible | Adapts Kaziyami sources for Kototoro's `source-host` runtime via reflection / manifest descriptors. Does not link against `skepsun/kototoro-parsers` (GPLv3). Interacts solely across the public `source-host` ABI (Apache-2.0). |
| Distribution Artifacts (`*.apk`, `*.jar`) | **MIT** | Clean binary distributions without GPL contamination. |

---

## 2. Kototoro & Kotatsu GPL Isolation

- **Upstream Context**: The Kotatsu parser library lineage (`kototoro-parsers`) is licensed under `GPL-3.0`. Linking directly against `parser-api` or `MangaLoaderContext` would impose GPL copyleft on downstream code.
- **Kaziyami Design Boundary**:
  1. Kaziyami does **NOT** import or link GPL classes from `kototoro-parsers`.
  2. Kaziyami utilizes Kototoro's official **Apache-2.0** `source-host` architecture (`AniyomiSourceRuntime` and `MihonJarInspector`), which consumes Aniyomi/Mihon extension bytecode over reflection.
  3. Consequently, the entire Kaziyami codebase remains cleanly licensed under **MIT**.
