#!/usr/bin/env python3
"""
verify_no_host_classes.py:
Scans all built APKs in repo/apk/ to assert that NO host-provided classes
(eu.kanade.*, okhttp3.*, okio.*, kotlin.*, kotlinx.*, androidx.*, uy.kohesive.injekt.*, org.skepsun.*, org.jsoup.*)
are packaged inside classes.dex, preserving strict ClassLoader isolation.
"""

import glob
import os
import struct
import sys
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK_DIR = os.path.join(ROOT_DIR, "repo", "apk")

HOST_PREFIXES = (
    "Leu/kanade/",
    "Lokhttp3/",
    "Lokio/",
    "Lkotlin/",
    "Lkotlinx/",
    "Landroidx/",
    "Luy/kohesive/",
    "Lorg/skepsun/",
    "Lorg/jsoup/"
)

def inspect_apk(apk_path):
    with zipfile.ZipFile(apk_path, "r") as zf:
        if "classes.dex" not in zf.namelist():
            return [f"classes.dex missing in {os.path.basename(apk_path)}"]
        dex_data = zf.read("classes.dex")

    (
        string_ids_size, string_ids_off,
        type_ids_size, type_ids_off,
        proto_ids_size, proto_ids_off,
        field_ids_size, field_ids_off,
        method_ids_size, method_ids_off,
        class_defs_size, class_defs_off
    ) = struct.unpack_from("<12I", dex_data, 56)

    def get_string(idx):
        off = struct.unpack_from("<I", dex_data, string_ids_off + idx * 4)[0]
        pos = off
        length = 0
        shift = 0
        while True:
            b = dex_data[pos]
            pos += 1
            length |= (b & 0x7f) << shift
            if (b & 0x80) == 0:
                break
            shift += 7
        end = dex_data.find(b"\x00", pos)
        return dex_data[pos:end].decode("utf-8", errors="replace")

    def get_type(idx):
        str_idx = struct.unpack_from("<I", dex_data, type_ids_off + idx * 4)[0]
        return get_string(str_idx)

    violations = []
    for i in range(class_defs_size):
        class_idx = struct.unpack_from("<I", dex_data, class_defs_off + i * 32)[0]
        cname = get_type(class_idx)
        if any(cname.startswith(p) for p in HOST_PREFIXES):
            violations.append(cname)

    return violations

def main():
    apks = glob.glob(os.path.join(APK_DIR, "*.apk"))
    if not apks:
        print(f"[!] No APKs found in {APK_DIR}")
        sys.exit(1)

    all_violations = {}
    print(f"[+] Verifying no host classes packaged across {len(apks)} APKs...")
    for apk in sorted(apks):
        apk_name = os.path.basename(apk)
        v = inspect_apk(apk)
        if v:
            all_violations[apk_name] = v
        else:
            print(f"    [OK] {apk_name}: 0 host classes packaged")

    if all_violations:
        print("\n[!] FATAL: Host-provided classes were bundled into extension APKs:")
        for name, viols in all_violations.items():
            print(f"  {name} ({len(viols)} bundled host classes):")
            for c in viols[:10]:
                print(f"    - {c}")
            if len(viols) > 10:
                print(f"    ... and {len(viols) - 10} more")
        sys.exit(1)

    print("[+] PASS: All APKs strictly preserve ClassLoader isolation (0 host classes).")

if __name__ == "__main__":
    main()

