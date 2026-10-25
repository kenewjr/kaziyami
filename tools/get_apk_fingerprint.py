#!/usr/bin/env python3
"""
get_apk_fingerprint.py:
Prints the signing certificate SHA-256 fingerprint of a built APK or JAR file
using keytool (or apksigner if available), returning the lowercase hex string.
"""

import argparse
import os
import re
import subprocess
import sys

def get_fingerprint_from_apksigner(apk_path):
    try:
        out = subprocess.check_output(
            ["apksigner", "verify", "--print-certs", apk_path],
            stderr=subprocess.DEVNULL,
            text=True
        )
        for line in out.splitlines():
            line_str = line.strip()
            if "SHA-256 digest:" in line_str or "SHA256:" in line_str:
                parts = line_str.split(":", 1)
                if len(parts) > 1:
                    clean_fp = parts[1].replace(":", "").replace(" ", "").strip().lower()
                    if len(clean_fp) == 64:
                        return clean_fp
    except Exception:
        pass
    return None

def get_fingerprint_from_keytool(apk_path):
    try:
        out = subprocess.check_output(
            ["keytool", "-printcert", "-jarfile", apk_path],
            stderr=subprocess.DEVNULL,
            text=True
        )
        for line in out.splitlines():
            line_str = line.strip()
            if line_str.startswith("SHA256:") or line_str.startswith("SHA-256:"):
                parts = line_str.split(":", 1)
                if len(parts) > 1:
                    clean_fp = parts[1].replace(":", "").replace(" ", "").strip().lower()
                    if len(clean_fp) == 64:
                        return clean_fp
    except Exception as e:
        print(f"[!] keytool error: {e}", file=sys.stderr)
    return None

def get_apk_fingerprint(apk_path):
    if not os.path.exists(apk_path):
        raise FileNotFoundError(f"APK file not found: {apk_path}")

    fp = get_fingerprint_from_apksigner(apk_path)
    if fp:
        return fp

    fp = get_fingerprint_from_keytool(apk_path)
    if fp:
        return fp

    return None

def main():
    parser = argparse.ArgumentParser(description="Extract SHA-256 certificate fingerprint from APK")
    parser.add_argument("apk", help="Path to signed APK or JAR file")
    args = parser.parse_args()

    fp = get_apk_fingerprint(args.apk)
    if not fp:
        print(f"[!] Could not extract signing fingerprint from {args.apk} (is it signed?)", file=sys.stderr)
        sys.exit(1)

    print(fp)
    return 0

if __name__ == "__main__":
    main()
