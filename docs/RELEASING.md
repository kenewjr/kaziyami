# Kaziyami Release Checklist & Procedures

Step-by-step release process for publishing Kaziyami extensions to the Aniyomi repository branch.

---

## 1. Pre-Release Verification

1. **Verify Invariant Rules**:
   - [ ] No `kaziyami`, `aniyomi`, `tachiyomi`, or `extension` strings in outgoing network headers (`./gradlew :core:test --tests "app.kaziyami.network.NetworkLeakTest"`).
   - [ ] Permanent Source IDs match [docs/SOURCE_IDS.md](SOURCE_IDS.md) (`./gradlew :core:test --tests "app.kaziyami.core.SourceIdContractTest"`).
   - [ ] Every extension has its original adaptive icon (`./gradlew checkExtensionIcons`).
2. **Run Full Test Suite**:
   ```bash
   ./gradlew check jacocoTestReport
   ```
   Ensure 100% of tests pass and core coverage exceeds 80%.

---

## 2. Version Bumping

1. Inspect affected extensions against your previous release:
   ```bash
   python tools/bump_changed.py --bump
   ```
2. Verify that `extVersionCode` in `build.gradle.kts` has been incremented for all modified modules.
3. Commit the version bump:
   ```bash
   git add .
   git commit -m "chore(release): bump extension version codes for release v1.4.X"
   ```

---

## 3. Assembling & Signing APKs

1. Run the release assembly task:
   ```bash
   ./gradlew assembleRelease
   ```
2. Confirm the outputs:
   - [ ] 6 APKs created under `repo/apk/` named `kaziyami-<lang>.<source>-v<version>.apk`.
   - [ ] `repo/sha256sums.txt` contains valid SHA-256 hashes for all 6 APKs.
   - [ ] `repo/index.json` and `repo/index.min.json` generated and schema-valid.
   - [ ] `repo/repo.json` contains your release certificate SHA-256 fingerprint.

---

## 4. Git Tagging & GitHub Actions Release

1. Push your changes to the `main` or `master` branch:
   ```bash
   git push origin master
   ```
2. Tag the release:
   ```bash
   git tag v1.4.1
   git push origin v1.4.1
   ```
3. GitHub Actions workflow `.github/workflows/publish.yml` will automatically:
   - Build signed release APKs using your encrypted repository secrets.
   - Deploy repository assets (`/apk`, `/icon`, `index.json`, `index.min.json`, `repo.json`, `sha256sums.txt`) to the `repo` branch.
   - Create a GitHub Release attached to `v1.4.1` with the signed APK binaries.

---

## 5. Physical Device Acceptance Test

On a real Android smartphone with Aniyomi installed:

1. **Refresh Repository**:
   - Go to **Aniyomi** -> **Browse** -> **Extensions**.
   - Pull down to refresh extension index.
2. **Verify Labels**:
   - Ensure extensions are displayed with the family prefix: `Kaziyami: Nekopoi`, `Kaziyami: IndoHentai`, etc.
3. **Install & Update Test**:
   - Tap **Install** or **Update** on any extension.
   - Verify Android installs the APK without signature mismatch errors.
4. **Playback Test**:
   - Open 1 anime from each of the 6 sources.
   - Verify details load, episode list populates, and tapping an episode starts video playback within 5 seconds.
