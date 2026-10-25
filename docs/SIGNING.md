# Kaziyami Release Signing Guide

> [!CAUTION]
> **CRITICAL SECURITY WARNING: NEVER COMMIT KEYSTORE FILES OR SECRETS TO GIT.**
> 
> **PERMANENT SIGNATURE WARNING**: Android extension updates verify that subsequent APKs match the original signing certificate. If you lose `kaziyami.keystore` or its passwords, **you will never be able to push updates to installed extensions**. Users will be forced to manually uninstall every extension and reinstall them from scratch, losing library history and bookmarks. Back up `kaziyami.keystore` securely offline.

---

## 1. Generating Your Release Keystore

Generate an RSA 4096-bit release keystore with a 30-year validity using standard JDK `keytool`:

```bash
keytool -genkeypair \
  -alias kaziyami \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10950 \
  -keystore kaziyami.keystore \
  -storetype PKCS12 \
  -dname "CN=Kaziyami Extensions, OU=Kaziyami, O=Kaziyami, L=Jakarta, ST=DKI, C=ID"
```

You will be prompted to enter a secure password. Save this password securely in your password manager.

---

## 2. Converting Keystore to Base64 for CI Secrets

To inject the release keystore into GitHub Actions CI workflows, encode the keystore file as a single-line Base64 string:

### On Linux / macOS:
```bash
base64 -w 0 kaziyami.keystore > keystore_base64.txt
# Copy the string inside keystore_base64.txt
```

### On Windows PowerShell:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("kaziyami.keystore")) | Out-File -Encoding ascii keystore_base64.txt
```

---

## 3. Configuring GitHub Repository Secrets

In your GitHub repository, navigate to **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**, and configure the following 4 secrets:

| Secret Name | Value Description | Example / Note |
|---|---|---|
| `KAZIYAMI_KEYSTORE_B64` | The complete Base64 encoded string of `kaziyami.keystore`. | Generated from Step 2. |
| `KAZIYAMI_KEYSTORE_PASSWORD` | The password you entered when creating the keystore. | Never commit to git. |
| `KAZIYAMI_KEY_ALIAS` | The key alias. | `kaziyami` |
| `KAZIYAMI_KEY_PASSWORD` | The private key password. | Same as keystore password for PKCS12. |

---

## 4. Local Signing via `keystore.properties` (Optional)

For local release builds without environment variables, create an untracked file named `keystore.properties` in the project root:

```properties
keystore.file=kaziyami.keystore
keystore.password=your_keystore_password
key.alias=kaziyami
key.password=your_key_password
```

> [!NOTE]
> `keystore.properties` and `*.keystore` are strictly included in `.gitignore` and will never be committed.

---

## 5. Extracting Certificate Fingerprint for `repo.json`

Once signed, Aniyomi repository trust requires the SHA-256 certificate fingerprint in `repo.json`. Extract it via our tool:

```bash
python tools/get_apk_fingerprint.py repo/apk/kaziyami-id.nekopoi-v1.4.1.apk
```

Or using `keytool` directly:
```bash
keytool -list -v -keystore kaziyami.keystore -alias kaziyami
```
Copy the `SHA256:` fingerprint (format: `e3b0c44298fc1c14...` without colons) and verify it matches the `signingKeyFingerprint` in `repo/repo.json`.
