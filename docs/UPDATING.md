# Kaziyami Extension & Plugin Update Guide

This document explains how updates arrive for each distribution variant (Variant A: Aniyomi APKs and Variant B: Kototoro JAR plugins), how update detection works, how to force a refresh, how to roll back, and common troubleshooting steps.

---

## 1. How Updates Arrive

Kaziyami publishes dual distribution variants from a single unified engine:

| Variant | Target App(s) | Update Detection Rule | Where Updates Appear | Action Required |
|---|---|---|---|---|
| **Variant A (APKs)** | Aniyomi, Aniyomi Forks, Kototoro (Aniyomi repo) | `remote.code > installed.code` | **Extensions** tab -> "Update" badge next to extension | Tap **Update** button (or "Update all") |
| **Variant B (JARs)** | Kototoro (Desktop JVM & Android ART) | `remote.versionCode > installed.versionCode` | **Settings** -> **Extension management** -> **Packages** tab | Tap **Update** on package row (or "Update all") |

> [!NOTE]
> Background auto-installation does not occur silently in either app. In both Aniyomi and Kototoro, users must tap **Update** (or **Update all**). Kototoro does not run background update workers for extension catalogs; update checks happen when the management screen opens or upon manual pull-to-refresh.

---

## 2. Step-by-Step Update Testing

### In Kototoro (Variant B: JAR Repository)
1. Open Kototoro -> **Settings** -> **Extension management**.
2. Navigate to the **Packages** tab.
3. If an update is available (e.g. repo has version code `3` and installed has version code `2`):
   - An **Update all** button appears in the top header.
   - The package card displays a tag indicating the currently installed version: `Installed: 1.4.2`.
   - The primary button on the card reads **Update**.
4. Tap **Update**. Kototoro downloads the new JAR into its private plugin storage (`files/plugins/<pkg>.jar`), updates its version registry, and re-initializes parsers dynamically without restarting the application.

### In Aniyomi (Variant A: APK Repository)
1. Open Aniyomi -> **Browse** -> **Extensions** tab.
2. Pull down to refresh the repository index.
3. If an update is available:
   - An **Update** button appears next to the extension.
4. Tap **Update** to download and install the updated APK via Android Package Installer.

---

## 3. How to Force a Catalog Refresh

If a newly published update does not appear immediately:
- **Kototoro**:
  1. Open **Settings** -> **Extension management**.
  2. Switch to the **Repositories** tab.
  3. Locate the Kaziyami repository and tap the **Refresh** icon, or swipe down to pull-to-refresh on the Packages list.
- **Aniyomi**:
  1. Open **Browse** -> **Extensions**.
  2. Swipe down on the extensions list to trigger an index reload from GitHub raw CDN.

---

## 4. Rollback Using Previous Versions

Kaziyami retains the **previous 3 versions** of every APK and JAR file on the `repo` branch to allow immediate rollback if an unexpected site layout change or regression occurs:

1. **Kototoro (Local JAR Rollback)**:
   - Download the previous version JAR from GitHub Releases or the repository branch (e.g. `repo/jar/kaziyami-all-v1.4.1.jar`).
   - Open Kototoro -> **Settings** -> **Extension management** -> **Packages** tab.
   - Tap **Import local JAR** and select the downloaded file.
2. **Aniyomi (APK Rollback)**:
   - Uninstall the current version of the extension from Android Settings or Aniyomi.
   - Download and install the previous version APK (e.g. `kaziyami-id.nekopoi-v14.1.apk`) from GitHub Releases.

---

## 5. Troubleshooting

| Issue | Root Cause | Solution |
|---|---|---|
| **Update not visible after release** | GitHub raw CDN caching (`raw.githubusercontent.com` caches files for ~5 minutes). | Wait 5 minutes or use force pull-to-refresh in the app. Confirm the updated `index.min.json` is visible in your browser. |
| **"Same version re-published" not updating** | Upstream Kototoro and Aniyomi use strict greater-than (`versionCode > installedVersionCode`). Same version code never triggers an update. | Ensure `extVersionCode` was incremented via `./gradlew bumpChanged`. |
| **APK installation fails: "Package corrupt" or "Signature mismatch"** | Android OS blocks installing an APK signed with a different key over an existing install (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). | Uninstall the old test/debug extension first, or ensure official releases are signed with the consistent `kaziyami.keystore`. |
| **Wrong repository type selected in Kototoro** | User selected `ANIYOMI` for a JAR repo or `KOTOTORO` for an APK repo. | In Kototoro **Repositories** tab: select **ANIYOMI** when adding `.../repo/index.min.json` (for APKs); select default/JAR repo when adding `.../repo/jar/index.min.json`. |
| **Extension marked "Incompatible"** | Extension `libVersion` falls outside the host app's supported range. | Upstream Aniyomi/Kototoro supports library version `1.4`. Kaziyami extensions pin `extensionLib = 1.4`. |

