# Technology Stack & Host Runtime Dependency Matrix

## 1. Pinned Toolchain & Compiler Baseline

| Component | Pinned Version | Rationale |
|---|---|---|
| **JDK** | `17` (`Temurin-17` / OpenJDK 17) | Standard JVM target for Android dexing and Aniyomi/Kototoro compiler compatibility. |
| **Gradle** | `7.6.4` | Stable Gradle wrapper compatible with Kotlin 1.8.22 and JDK 17 without deprecation warnings. |
| **Kotlin** | `1.8.22` | Upstream Aniyomi extension compiler baseline. Major upgrades (e.g. Kotlin 2.x) risk metadata format incompatibilities with host classloaders. |
| **JaCoCo** | `0.8.8` | Code coverage reporting (`>80%` on core & multisrc bases). |
| **Android minSdk** | `21` | Supported Android 7–15 devices. Allowed range: `21` – `26`. |
| **Android targetSdk** | `33` | Modern Android 14+ compatible without deprecated SDK blocking (`targetSdk < 23` blocked on Android 14, `< 24` on Android 15). Allowed range: `33` – `34`. |
| **Android compileSdk**| `33` | Android 13 framework baseline for resource and manifest linking. |
| **CI Runner OS** | `ubuntu-24.04` (Noble Numbat) | Pinned runner image across all workflows. Moving to `ubuntu-24.04` is a deliberate step to eliminate non-deterministic glibc/Node/runner drifts inherent in floating `ubuntu-latest`. |

---

## 2. Host Runtime Provided Library Matrix

Aniyomi (and compatible forks like Kototoro v2.3+) provides core networking, parsing, and serialization libraries on the application's root classloader. Extensions run inside child `PathClassLoader` instances.

> [!IMPORTANT]
> **Classloader Boundary Rule**: Any library provided by the host application MUST be declared as `compileOnly` (or `provided`) in extension modules and MUST NOT be bundled into the extension DEX. Packaging a conflicting major version (e.g., OkHttp 5 in an OkHttp 4 host) causes `ClassCastException`, `LinkageError`, or silent bytecode verification crashes on device.

| Host Client / App Version | Kotlin Stdlib | OkHttp | kotlinx.serialization | kotlinx.coroutines | Jsoup | Aniyomi Extension API |
|---|---|---|---|---|---|---|
| **Aniyomi v0.15.2.4** (Stable) | `1.8.22` – `1.9.23` | `4.11.0` – `4.12.0` | `1.5.1` – `1.6.2` | `1.7.3` – `1.8.0` | `1.16.1` – `1.17.2` | `14` (libVersion) |
| **Aniyomi Preview (r7000+)** | `1.9.23` | `4.12.0` | `1.6.3` | `1.8.0` | `1.17.2` | `14` (libVersion) |
| **Kuukiyomi** | `1.9.20` | `4.11.0` | `1.5.1` | `1.7.3` | `1.16.1` | `14` (libVersion) |
| **Kototoro v2.3.2** | `1.9.23` | `4.12.0` | `1.6.3` | `1.8.1` | `1.17.2` | `14` (via `source-host` & `AniyomiExtensionLoader`) |

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

- `actions`: GitHub Actions updates (weekly).
- `gradle`: Gradle wrapper & build plugins (weekly).
- `host-runtime`: All `compileOnly` dependencies pinned to host runtime matrix with major/breaking updates strictly blocked.

---

## 5. Artifact Versioning Convention & Dual-Format Reconciliation

Kaziyami distributes extensions across two distinct packaging formats derived from a single unified codebase:

1. **Variant A: Aniyomi APK Extension Convention (`v14.x`)**
   - **`libVersion`**: `"14"` (integer 14 representing upstream Aniyomi Extension API 14 compatibility).
   - **`extVersionCode`**: Monotonically increasing integer (e.g. `2`).
   - **APK Filename**: `kaziyami-<lang>.<slug>-v<libVersion>.<extVersionCode>.apk` (e.g. `kaziyami-id.nekopoi-v14.2.apk`).
   - **Manifest**: `android:versionCode="2"`, `android:versionName="14.2"`.
   - **Index (`repo/index.json`)**: `"version": "14.2"`, `"code": 2`.

2. **Variant B: Kototoro JAR Plugin Convention (`v1.4.x`)**
   - **Semantic Version**: `1.4.<extVersionCode>` (derived by mapping `libVersion` 14 to major/minor `1.4` and appending monotonic integer code).
   - **JAR Filename**: `kaziyami-all-v1.4.<extVersionCode>.jar` and `kaziyami-<slug>-v1.4.<extVersionCode>.jar`.
   - **Index (`repo/jar/index.json`)**: `"versionCode": 2`, `"versionName": "1.4.2"`.

3. **Parity Invariant**:
   Both representations share the identical integer `extVersionCode` (e.g. `2`). Host applications (Aniyomi and Kototoro) strictly evaluate `installedVersionCode < remoteVersionCode`, ensuring seamless update detection across both distribution channels.

1. **Host-provided dependencies**: Ignore semver-major updates in `.github/dependabot.yml`.
2. **Weekly Batching**: Minor and patch updates grouped weekly (`monday`).
3. **Automated Verification**: The `runtime-compat` CI job runs `tools/verify_runtime_compat.py` to assert that no module specifies dependency versions outside the supported matrix in this document.
