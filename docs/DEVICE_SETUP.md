# Physical Device Setup & Manual Testing Guide

Follow this guide to prepare your Android device for running Kaziyami extensions.

---

## 1. Safety & Data Backup
Before testing on your primary device:
1. Open Aniyomi -> **More -> Settings -> Backup and restore -> Create backup**.
2. Save the backup `.tachibk` file to an external folder (e.g. Google Drive or PC).
3. If using a compatible fork (e.g. Kuukiyomi or Dantotsu), consider testing on a secondary device or clean test profile.

---

## 2. System WebView & Browser Updates
Cloudflare Turnstile and anti-bot challenge solvers rely on the system WebView:
1. Open Google Play Store -> search for **Android System WebView** (or **Google Chrome**).
2. Ensure both are updated to the latest stable release (v120+).
3. Go to **Android Settings -> Developer Options -> WebView implementation** -> verify Chrome or Android System WebView is selected.

---

## 3. Aniyomi Configuration for Adult Extensions
1. Open Aniyomi -> **More -> Settings -> Browse**:
   - Turn **ON**: **Show NSFW source extensions**.
2. Go to **Android Settings -> Apps -> Aniyomi (or File Manager)**:
   - Grant permission: **Install unknown apps**.
   - Battery: Set to **Unrestricted** (prevents Android from terminating background video downloads).

---

## 4. Installing Debug Extensions & Untrusted Signature Prompt
Since debug APKs are signed with a test keystore, Aniyomi enforces a one-time trust prompt:
1. Run `./gradlew assembleDebugAll`.
2. Connect phone via USB with USB Debugging enabled, then run:
   ```bash
   # Windows:
   .\scripts\install-debug.ps1
   # Linux / macOS:
   ./scripts\install-debug.sh
   ```
   *(Or copy `.apk` files from `build/kaziyami-debug/` to phone and install from a file manager).*
3. Open Aniyomi -> go to **Browse -> Extensions**.
4. You will see 6 extensions labelled `Kaziyami: <Site>`.
5. Tap on the extension -> Aniyomi will display:
   > *"Untrusted extension: This extension was signed with an untrusted certificate. Do you want to trust it?"*
   - Tap **Trust**.
6. The extension is now active and ready for browsing.

---

## 5. Locating Logs & Redaction Protocol
If a source fails to load:
1. In Aniyomi, go to **More -> Settings -> Advanced -> Dump crash logs** or view system logcat:
   ```bash
   adb logcat -s Aniyomi:V AnimeExtension:V
   ```
2. **Redaction Protocol**:
   - Always run logs through `scripts/collect-diagnostics.py` or manually remove:
     - All `Cookie:` header contents.
     - Auth tokens (`token=...`, `bearer ...`).
     - Video stream query signatures (`&sig=...`).
     - Personal IP addresses and device serial numbers.

---

## 6. Rollback & Uninstallation
To restore your device to original state:
1. Go to **Android Settings -> Apps** -> find each `Kaziyami: <Site>` app -> tap **Uninstall**.
2. In Aniyomi, go to **More -> Settings -> Backup and restore -> Restore backup**.
3. Select the backup created in Step 1.
