# Technology Stack & Host Runtime Dependency Matrix

## 1. Pinned Toolchain & Compiler Baseline

| Component | Pinned Version | Rationale |
|---|---|---|
| **JDK** | `11` (`Temurin-11` / OpenJDK 11) | Standard JVM target for Android dexing and Aniyomi extension runtime. |
| **Gradle** | `7.6.4` | Stable Gradle wrapper compatible with Kotlin 1.8.22 and JDK 11 without deprecation warnings. |
| **Kotlin** | `1.8.22` | Upstream Aniyomi extension compiler baseline. Major upgrades (e.g. Kotlin 2.x) risk metadata format incompatibilities with host classloaders. |
| **JaCoCo** | `0.8.8` | Code coverage reporting (`>80%` on core & multisrc bases). |

---

## 2. Host Runtime Provided Library Matrix

Aniyomi (and compatible forks like Kototoro v2.3+) provides core networking, parsing, and serialization libraries on the application's root classloader. Extensions run inside child `PathClassLoader` instances.

> [!IMPORTANT]
> **Classloader Boundary Rule**: Any library provided by the host application MUST be declared as `compileOnly` (or `provided`) in extension modules and MUST NOT be bundled into the extension DEX. Packaging a conflicting major version (e.g., OkHttp 5 in an OkHttp 4 host) causes `ClassCastException`, `LinkageError`, or silent bytecode verification crashes on device.

| Host Client / App Version | Kotlin Stdlib | OkHttp | kotlinx.serialization | kotlinx.coroutines | Jsoup | Aniyomi Extension API |
|---|---|---|---|---|---|---|
| **Aniyomi v0.15.2.4** (Stable) | `1.8.22` – `1.9.23` | `4.11.0` – `4.12.0` | `1.5.1` – `1.6.2` | `1.7.3` – `1.8.0` | `1.16.1` – `1.17.2` | `1.3` / `1.4` |
| **Aniyomi Preview (r7000+)** | `1.9.23` | `4.12.0` | `1.6.3` | `1.8.0` | `1.17.2` | `1.4` |
| **Kuukiyomi** | `1.9.20` | `4.11.0` | `1.5.1` | `1.7.3` | `1.16.1` | `1.4` |
| **Kototoro v2.3.2** | `1.9.23` | `4.12.0` | `1.6.3` | `1.8.1` | `1.17.2` | `1.4` (via `source-host`) |

---

## 3. Extension Dependency Rules & Semver Bounds

| Dependency | Scope in Extensions | Allowed Version Range | Major Jump Policy |
|---|---|---|---|
| `org.jetbrains.kotlin:*` | `compileOnly` | `1.8.20` – `1.9.25` | **BLOCKED**: Kotlin 2.x compiler releases generate Kotlin 2.0 metadata unreadable by Kotlin 1.8/1.9 host runtimes. |
| `com.squareup.okhttp3:okhttp` | `compileOnly` | `4.10.0` – `4.12.x` | **BLOCKED**: OkHttp 5.x alpha introduces breaking package movements and requires higher Android API desugaring. |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `implementation` (core) / `compileOnly` | `1.5.1` – `1.6.3` | Minor/patch updates allowed. Major updates blocked until host app upgrades. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | `compileOnly` | `1.7.0` – `1.8.x` | Host-aligned. |
| `org.jsoup:jsoup` | `implementation` / `compileOnly` | `1.16.1` – `1.17.x` | Safe across 1.x minor releases. |
| `org.junit.jupiter:*` | `testImplementation` | `5.9.0` – `5.11.x` | **BLOCKED**: JUnit 6 does not exist as a stable release; Dependabot jumps are invalid. |
| `com.squareup.okhttp3:mockwebserver` | `testImplementation` | `4.11.0` – `4.12.0` | Must match OkHttp 4.x runtime. |
| `org.assertj:assertj-core` | `testImplementation` | `3.25.x` – `3.27.x` | Safe patch and minor updates in isolated test PRs. |

---

## 4. Dependabot Grouping & Safety Controls

1. **Host-provided dependencies**: Ignore semver-major updates in `.github/dependabot.yml`.
2. **Weekly Batching**: Minor and patch updates grouped weekly (`monday`).
3. **Automated Verification**: The `runtime-compat` CI job runs `tools/verify_runtime_compat.py` to assert that no module specifies dependency versions outside the supported matrix in this document.
