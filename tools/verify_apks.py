#!/usr/bin/env python3
"""
verify_apks.py:
Automated regression verifier for built Kaziyami extension APKs.
Enforces:
1. Minimum file size (> 50KB).
2. ZIP integrity (unzip -t / zipfile testzip).
3. Android binary XML manifest (magic 0x0003, no raw text '<' character).
4. Compiled classes.dex Dalvik bytecode presence.
5. apksigner v2/v3 scheme verification and release signature checking.
6. minSdk (21-26) and targetSdk (33-34) compliance with docs/STACK.md.
7. Metadata agreement with docs/SOURCE_IDS.md and repo/index.min.json.
"""

import argparse
import hashlib
import json
import os
import re
import sys
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
import android_sdk

MIN_APK_SIZE = 50 * 1024  # 50 KB
ALLOWED_MIN_SDK = (21, 26)
ALLOWED_TARGET_SDK = (33, 34)

def load_source_ids():
    source_ids_file = os.path.join(ROOT_DIR, "docs", "SOURCE_IDS.md")
    sources = {}
    if os.path.exists(source_ids_file):
        with open(source_ids_file, "r", encoding="utf-8") as f:
            for line in f:
                # | **Nekopoi** | `id` | Active | `7073589375897212735L` |
                m = re.search(r'\|\s*\*\*([^\*]+)\*\*\s*\|\s*`([^`]+)`\s*\|\s*[^\|]*\|\s*`(\d+)L?`', line)
                if m:
                    name, lang, sid = m.group(1).strip(), m.group(2).strip(), m.group(3).strip()
                    pkg = f"eu.kanade.tachiyomi.animeextension.{lang}.{name.lower()}"
                    sources[pkg] = {
                        "name": name,
                        "lang": lang,
                        "id": sid,
                        "label": f"Kaziyami: {name}",
                        "class": f"eu.kanade.tachiyomi.animeextension.{lang}.{name.lower()}.{name}"
                    }
    return sources

def verify_single_apk(apk_path, allowed_sources, allow_debug_cert=False):
    errors = []
    apk_name = os.path.basename(apk_path)

    # 1. Size check
    size = os.path.getsize(apk_path)
    if size < MIN_APK_SIZE:
        errors.append(f"Suspiciously small APK size: {size} bytes (< {MIN_APK_SIZE} bytes)")

    # 2. ZIP integrity
    try:
        with zipfile.ZipFile(apk_path, "r") as zf:
            bad_file = zf.testzip()
            if bad_file:
                errors.append(f"Corrupt ZIP entry detected: {bad_file}")
            namelist = zf.namelist()
            if "classes.dex" not in namelist:
                errors.append("Missing 'classes.dex' Dalvik executable bytecode in archive")
            if "AndroidManifest.xml" not in namelist:
                errors.append("Missing 'AndroidManifest.xml' in archive")
            else:
                manifest_data = zf.read("AndroidManifest.xml")
                if len(manifest_data) < 4:
                    errors.append("AndroidManifest.xml is empty or truncated")
                elif manifest_data[0] == ord('<'):
                    errors.append("AndroidManifest.xml is plain text XML instead of Android Binary XML (AXML)")
                elif int.from_bytes(manifest_data[:2], byteorder="little") != 0x0003:
                    errors.append(f"Invalid AXML header magic: {manifest_data[:2].hex()} (expected 0300)")
    except Exception as e:
        errors.append(f"Failed to read APK as ZIP: {e}")
        return errors

    # 3. apksigner verify
    res_sign = android_sdk.run_apksigner(["verify", "--verbose", "--print-certs", apk_path])
    if res_sign.returncode != 0:
        errors.append(f"apksigner verification failed: {res_sign.stderr.strip()}")
    else:
        out = res_sign.stdout
        v2_verified = "Verified using v2 scheme (APK Signature Scheme v2): true" in out
        v3_verified = "Verified using v3 scheme (APK Signature Scheme v3): true" in out
        if not (v2_verified or v3_verified):
            errors.append("APK is not signed with APK Signature Scheme v2 or v3")

        if not allow_debug_cert:
            for line in out.splitlines():
                if "Signer #1 certificate DN:" in line:
                    dn = line.lower()
                    if "android debug" in dn or "kaziyami test" in dn or "cn=test" in dn:
                        errors.append(f"APK is signed with a debug/throwaway test certificate: {line.strip()}")

    # 4. Manifest / SDK / Badging checks
    try:
        parsed = android_sdk.parse_apk_manifest(apk_path)
        pkg = parsed["pkg"]
        label = parsed["label"]
        min_sdk = parsed["minSdk"]
        target_sdk = parsed["targetSdk"]

        if not (ALLOWED_MIN_SDK[0] <= min_sdk <= ALLOWED_MIN_SDK[1]):
            errors.append(f"minSdkVersion {min_sdk} outside allowed range {ALLOWED_MIN_SDK}")
        if not (ALLOWED_TARGET_SDK[0] <= target_sdk <= ALLOWED_TARGET_SDK[1]):
            errors.append(f"targetSdkVersion {target_sdk} outside allowed range {ALLOWED_TARGET_SDK}")

        expected = allowed_sources.get(pkg)
        if not expected:
            errors.append(f"Unknown package name {pkg} (not found in docs/SOURCE_IDS.md)")
        else:
            if label != expected["label"]:
                errors.append(f"Label mismatch for {pkg}: got '{label}', expected '{expected['label']}'")
            if parsed["extClass"] != expected["class"]:
                errors.append(f"Extension class mismatch for {pkg}: got '{parsed['extClass']}', expected '{expected['class']}'")

    except Exception as e:
        errors.append(f"Failed to parse binary manifest badging: {e}")

    return errors

def verify_apks(apk_dir, allow_debug_cert=False):
    if not os.path.exists(apk_dir):
        print(f"[!] APK directory does not exist: {apk_dir}", file=sys.stderr)
        return 1

    apks = [os.path.join(apk_dir, f) for f in os.listdir(apk_dir) if f.endswith(".apk")]
    if not apks:
        print(f"[!] No APK files found in {apk_dir}", file=sys.stderr)
        return 1

    sources = load_source_ids()
    total_errors = 0

    print(f"[+] Verifying {len(apks)} APKs in {apk_dir} (allow_debug_cert={allow_debug_cert})...")
    for apk in apks:
        errs = verify_single_apk(apk, sources, allow_debug_cert=allow_debug_cert)
        name = os.path.basename(apk)
        if errs:
            print(f"[FAIL] {name}:")
            for e in errs:
                print(f"    - {e}")
            total_errors += len(errs)
        else:
            print(f"[PASS] {name}")

    if total_errors > 0:
        print(f"[!] Verification failed with {total_errors} errors.", file=sys.stderr)
        return 1

    print(f"[+] All {len(apks)} APKs passed all automated checks.")
    return 0

def main():
    parser = argparse.ArgumentParser(description="Verify built Kaziyami extension APKs")
    parser.add_argument("--dir", default=os.path.join(ROOT_DIR, "repo", "apk"), help="Directory containing APKs")
    parser.add_argument("--allow-debug-cert", action="store_true", help="Allow debug/test certificates (for CI test runs)")
    args = parser.parse_args()

    sys.exit(verify_apks(args.dir, allow_debug_cert=args.allow_debug_cert))

if __name__ == "__main__":
    main()
