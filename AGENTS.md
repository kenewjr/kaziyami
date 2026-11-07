# Kaziyami Agent Guidelines & Operational Invariants

Mandatory instruction document for AI assistants and automated coding agents modifying the Kaziyami codebase. Every agent MUST read and adhere strictly to these rules to ensure that iterations and improvisations do not cause regressions, break contracts, or drop existing features.

---

## 1. Zero Feature Regression Rules

1. **Preserve Existing Capabilities**:
   - Never remove alternate selectors, fallback stream extractors, or caching mechanisms without explicit instructions.
   - All improvements and modifications must be additive and bolster resilience.
2. **Permanent Source ID Lock (`docs/SOURCE_IDS.md`)**:
   - The 64-bit hash IDs of all 6 adult sources (`Nekopoi`, `IndoHentai`, `NontonHentai`, `Hentaimama`, `JAVSubbed`, `Hanime`) are permanent.
   - The internal source name `AnimeSource.name` MUST remain the raw site name (e.g., `Nekopoi`), NEVER prefixed with `Kaziyami:`.
   - Modifying source IDs corrupts bookmarks and watch history in the host application database.
3. **Display Label Naming Conventions**:
   - Display labels in host UIs (`android:label` in the manifest and `name` in `index.min.json`): `Kaziyami: <Site>` (e.g., `Kaziyami: Nekopoi`).

---

## 2. Host Compatibility Invariants (Dual-Variant Contract)

This repository produces and distributes two variants from a single core engine:

### Variant A: Aniyomi Extension (APK)
- **Root Package**: Must maintain the `eu.kanade.tachiyomi.animesource.*` and `eu.kanade.tachiyomi.animeextension.*` namespaces.
- **Manifest**: Must include metadata `tachiyomi.animeextension.class` and `tachiyomi.animeextension.nsfw=1`.
- **Repository Index**: Aniyomi extension array schema at `repo/index.min.json`.

### Variant B: Kototoro Plugin (JAR)
- **Reflection Entrypoint**: Must provide `org.skepsun.kototoro.parsers.ContentParserFactoryKt.newParser(ContentParserSource, ContentLoaderContext): ContentParser`.
- **Source Enum**: Enum constants of `org.skepsun.kototoro.parsers.model.ContentParserSource`.
- **Mandatory Dual-Bytecode**: Every plugin JAR must bundle both `classes.dex` (via D8 for Android ART) and standard JVM `.class` files (for Desktop JVM hosts).
- **Repository Index**: Direct JSON array `List<ExtensionIndexDto>` at `repo/jar/index.min.json` with download path `<repo>/apk/<file.jar>`.
- **ClassLoader Isolation**: Never bundle host `parser-api` classes into plugin JARs.

---

## 3. Network Resilience & Anti-Bot Constraints

1. **Clean & Anonymous Request Headers**:
   - Never inject custom identifying headers (such as `User-Agent: Kaziyami` or `X-Extension-Version`).
   - Maintain `User-Agent` and Client Hints consistency centrally via `BrowserProfile`.
2. **Rate Limiting & Anti-Burst**:
   - Always use `TokenBucketRateLimiter` (maximum 3 req/s per host) with a concurrency semaphore (cap 1-2 per host) and micro-jitter.
3. **Sequential Stream Extraction**:
   - Resolve embed streams sequentially (prioritizing user's preferred host). Terminate immediately on the first successful playable stream; do not hammer embed hosts in parallel.
4. **Security & Legal Boundaries**:
   - Never integrate or bypass third-party paid/automated captcha-solving services.
   - On Cloudflare challenges or ISP sinkholes, route users cleanly via `SourceParseException` ("Open in WebView" or switch mirror domain).

---

## 4. Mandatory Runbook Per Prompt

Before modifying code, follow this sequence:

1. **Check Ground Truth**:
   - Read [docs/DECISIONS.md](file:///e:/project/kaziyami/docs/DECISIONS.md) to understand existing architectural constraints.
   - Review [docs/PROGRESS.md](file:///e:/project/kaziyami/docs/PROGRESS.md) for current implementation status.
2. **Minimal Diff (Lazy Senior Dev Principle)**:
   - Implement the smallest viable change. Avoid unnecessary abstractions, boilerplate, or unrequested dependencies.
   - Do not touch files unrelated to the user's prompt.
3. **Execute Verification Suite (Must Be 100% Green)**:
   Run the full verification suite in the terminal:
   ```bash
   # 1. Unit & Contract Tests + Bytecode Checks
   ./gradlew check

   # 2. Kototoro JAR Structure & Bytecode Verification
   python tools/verify_jars.py

   # 3. Monotonic Auto-Update Simulation
   python tools/test_update_simulation.py

   # 4. Preflight & Diagnostic Sanitization
   python tools/test_preflight.py
   python tools/test_collect_diagnostics.py

   # 5. Secret Scanner (Zero Leaks Required)
   python tools/scan_secrets.py
   ```
4. **Document Decisions**:
   - Record architectural choices or tradeoffs in `docs/DECISIONS.md` using the format:
     `[Decision]: [Reason]. Alternative rejected: [Alternative].`
   - Update task items in `docs/PROGRESS.md`.
5. **Zero Secret Leaks**:
   - Never stage or commit `.keystore`, `.jks`, `.properties` containing credentials, or AI session logs.
