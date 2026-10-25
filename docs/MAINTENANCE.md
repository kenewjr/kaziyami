# Kaziyami Maintenance & Bug-Fix Lifecycle

This guide details the operational procedures for triaging bugs, releasing hotfixes, performing scheduled monitoring, and keeping dependencies synchronized.

---

## 1. Hotfix Workflow

Target turnaround:
- **Critical (Entire source down or zero videos playing)**: < 24 hours.
- **Moderate (Broken filters, missing metadata, pagination bug)**: < 72 hours.
- **Minor (Formatting quirk, studio label)**: Next scheduled release.

### Step-by-Step Hotfix Loop
1. **Reproduce via Static Fixture**:
   - Fetch the broken page using `python tools/refresh_fixtures.py` or save the HTML directly into `src/<lang>/<source>/src/test/resources/<source>/<broken_page>.html`.
2. **Add a Failing Regression Test**:
   - In `src/<lang>/<source>/src/test/kotlin/.../<Source>Test.kt`, add a test reproducing the exact failure. Confirm the test fails (`RED`).
3. **Apply the Fix**:
   - Update selectors, base classes, or extractor logic with the minimum necessary diff.
4. **Verify Green Suite**:
   - Run `./gradlew :src:<lang>:<source>:test`. Confirm all tests pass (`GREEN`).
5. **Automated Version Bump**:
   - Run `python tools/bump_changed.py --bump`.
   - The tool maps modified files to affected extension modules and increments `extVersionCode` in their `build.gradle.kts`.
6. **Publish Checklist**:
   - Commit changes: `git commit -m "fix(<source>): resolve player selector drift"`.
   - Push to `master` branch.
   - GitHub Actions `publish.yml` automatically builds signed APKs, regenerates `index.json`, and updates the `repo` release branch.

---

## 2. Maintenance Cadence

### Weekly Cadence
- Review GitHub Actions `monitor.yml` runs.
- Inspect structure reports in `reports/structure_report.json` for selector hit count degradation.
- Run `./gradlew liveSmokeTest` locally or check `smoke.yml` on GitHub Actions.

### Monthly Cadence
- **Dependency Audit**: Review Dependabot PRs or run `./gradlew dependencyUpdates`.
- **Upstream Sync**: Diff against upstream `aniyomi-extensions` and `aniyomi` core repository to port video host extractor fixes or API enhancements.
