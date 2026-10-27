#!/usr/bin/env python3
"""
verify_jars.py:
Validates Kototoro plugin JARs against the verified contract:
1. Valid ZIP archive.
2. Dalvik executable (classes.dex) present for Android ART.
3. Desktop JVM .class files present (dual-mode).
4. Entrypoint org/skepsun/kototoro/parsers/ContentParserFactoryKt.class present.
5. Source enum org/skepsun/kototoro/parsers/model/ContentParserSource.class present.
6. Bytecode version <= Java 17 (class version 61).
7. Zero test classes (*Test.class), zero test fixtures (*.html), zero keystores (*.jks, *.keystore).
8. Zero host parser-api interfaces bundled (ContentParser.class, ContentLoaderContext.class).
9. Repository index (index.min.json) schema compliance.
"""

import argparse
import json
import os
import struct
import sys
import zipfile

def verify_jar(jar_path: str) -> bool:
    print(f"[*] Verifying JAR: {jar_path}")
    if not os.path.exists(jar_path):
        print(f"[-] Error: file does not exist: {jar_path}")
        return False

    try:
        with zipfile.ZipFile(jar_path, "r") as z:
            entries = z.namelist()
            entry_set = set(entries)

            # 1. Dalvik classes.dex
            if "classes.dex" not in entry_set:
                print(f"[-] FAILED {jar_path}: missing classes.dex for Android ART")
                return False

            # 2. Entrypoint and Source Enum
            factory_entry = "org/skepsun/kototoro/parsers/ContentParserFactoryKt.class"
            if factory_entry not in entry_set:
                print(f"[-] FAILED {jar_path}: missing entrypoint {factory_entry}")
                return False

            source_entry = "org/skepsun/kototoro/parsers/model/ContentParserSource.class"
            if source_entry not in entry_set:
                print(f"[-] FAILED {jar_path}: missing source enum {source_entry}")
                return False

            # 3. Class versions
            class_count = 0
            for name in entries:
                if name.endswith(".class"):
                    class_count += 1
                    data = z.read(name)
                    if len(data) >= 8:
                        magic, minor, major = struct.unpack(">IHH", data[:8])
                        if magic != 0xCAFEBABE:
                            print(f"[-] FAILED {jar_path}: invalid class magic in {name}")
                            return False
                        if major > 61 or major < 45:
                            print(f"[-] FAILED {jar_path}: unsupported bytecode version {major} in {name}")
                            return False

            if class_count == 0:
                print(f"[-] FAILED {jar_path}: no .class files found for Desktop JVM")
                return False

            # 4. Prohibited entries
            for name in entries:
                if name.endswith("Test.class") or name.endswith("Tests.class"):
                    print(f"[-] FAILED {jar_path}: contains test class {name}")
                    return False
                if name.endswith(".html"):
                    print(f"[-] FAILED {jar_path}: contains HTML fixture {name}")
                    return False
                if name.endswith(".jks") or name.endswith(".keystore"):
                    print(f"[-] FAILED {jar_path}: contains keystore {name}")
                    return False
                if name == "org/skepsun/kototoro/parsers/ContentParser.class":
                    print(f"[-] FAILED {jar_path}: bundles host interface ContentParser.class")
                    return False
                if name == "org/skepsun/kototoro/parsers/ContentLoaderContext.class":
                    print(f"[-] FAILED {jar_path}: bundles host interface ContentLoaderContext.class")
                    return False

            print(f"[+] PASS: {jar_path} (classes.dex present, {class_count} classes, clean)")
            return True

    except Exception as e:
        print(f"[-] FAILED {jar_path}: exception reading archive: {e}")
        return False

def verify_repo_index(index_path: str, apk_dir: str) -> bool:
    print(f"[*] Verifying repo index: {index_path}")
    if not os.path.exists(index_path):
        print(f"[-] Error: index does not exist: {index_path}")
        return False

    try:
        with open(index_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        if not isinstance(data, list):
            print(f"[-] FAILED {index_path}: root must be a JSON array")
            return False

        if len(data) == 0:
            print(f"[-] FAILED {index_path}: array must not be empty")
            return False

        required_keys = {"name", "pkg", "version", "code", "apk", "lang", "nsfw"}
        for idx, item in enumerate(data):
            if not isinstance(item, dict):
                print(f"[-] FAILED {index_path}: entry #{idx} is not an object")
                return False
            missing = required_keys - set(item.keys())
            if missing:
                print(f"[-] FAILED {index_path}: entry #{idx} missing keys: {missing}")
                return False
            if not isinstance(item["code"], int):
                print(f"[-] FAILED {index_path}: entry #{idx} 'code' must be an integer")
                return False
            if item["nsfw"] not in (0, 1):
                print(f"[-] FAILED {index_path}: entry #{idx} 'nsfw' must be 0 or 1")
                return False

            apk_file = os.path.join(apk_dir, item["apk"])
            if not os.path.exists(apk_file):
                print(f"[-] FAILED {index_path}: entry #{idx} points to non-existent apk: {apk_file}")
                return False

        print(f"[+] PASS: {index_path} ({len(data)} valid entries)")
        return True

    except Exception as e:
        print(f"[-] FAILED {index_path}: error: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Verify Kototoro JAR format and repo index")
    parser.add_argument("--jars", nargs="*", default=[], help="JAR files to verify")
    parser.add_argument("--repo-dir", default=None, help="Repository directory containing index.min.json and apk/")
    args = parser.parse_args()

    success = True
    for jar in args.jars:
        if not verify_jar(jar):
            success = False

    if args.repo_dir:
        index_file = os.path.join(args.repo_dir, "index.min.json")
        apk_dir = os.path.join(args.repo_dir, "apk")
        if not verify_repo_index(index_file, apk_dir):
            success = False

    if not success:
        sys.exit(1)
    print("[+] All Kototoro JAR checks PASSED.")
    sys.exit(0)

if __name__ == "__main__":
    main()
