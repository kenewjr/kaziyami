# Technology Stack & Versions (Phase 1)

Pinned build toolchain, language runtime, and library dependency versions aligned with upstream `aniyomiorg/aniyomi-extensions` and community standards:

| Component | Pinned Version | Rationale |
|---|---|---|
| **JDK** | `11` (`Temurin-11` / OpenJDK 11) | Standard JVM target for Android dexing and Aniyomi extension runtime. |
| **Gradle** | `7.6.4` | Stable Gradle wrapper compatible with Kotlin 1.8.22 and JDK 11 without deprecation warnings. |
| **Kotlin** | `1.8.22` | Upstream Aniyomi extension compiler baseline. |
| **Kotlinx Serialization** | `1.5.1` | JSON serialization for `SourceConfig` and schema validation. |
| **Aniyomi Extension API** | `1.3` / `1.4` | Core interfaces: `AnimeHttpSource`, `ParsedAnimeHttpSource`, `ConfigurableAnimeSource`. |
| **OkHttp** | `4.11.0` | HTTP client runtime and MockWebServer. |
| **Jsoup** | `1.16.1` | HTML parsing, DOM traversal, CSS selector queries, entities decoding. |
| **JUnit Jupiter (JUnit 5)** | `5.9.3` | Test runner, parameterized tests (`@ParameterizedTest`), offline fixture testing. |
| **JaCoCo** | `0.8.8` | Code coverage reporting (`>80%` on core & multisrc bases). |

---

## Tooling & Verification

- **Code Formatting / Style**: `.editorconfig` (4-space indent for Kotlin, LF line endings, 140-char max line length).
- **Test Harness**: JUnit 5 + OkHttp `MockWebServer` + Jsoup offline HTML fixtures.
- **CI / Distribution**: GitHub Actions on Ubuntu latest (`Temurin 11`), Python 3.10 for repo index generation.
