#!/usr/bin/env python3
"""
package_hello_jar.py:
Packages the minimal 'kototoro-hello' sample into the verified Kototoro JAR format:
- Layout: dual-mode containing both Dalvik bytecode (classes.dex) for Android ART and Java .class files for Desktop JVM.
- Entrypoint: org.skepsun.kototoro.parsers.ContentParserFactoryKt
- Source Enum: org.skepsun.kototoro.parsers.model.ContentParserSource
- Generates repository index under build/kototoro-hello-repo/ matching Kototoro's ExtensionIndexDto schema.
"""

import hashlib
import json
import os
import shutil
import sys
import tempfile
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
import android_sdk

HELLO_DIR = os.path.join(ROOT_DIR, "samples", "kototoro-hello")
HELLO_CLASSES_DIR = os.path.join(HELLO_DIR, "build", "classes", "kotlin", "main")
HELLO_REPO_DIR = os.path.join(ROOT_DIR, "build", "kototoro-hello-repo")
HELLO_APK_DIR = os.path.join(HELLO_REPO_DIR, "apk")
HELLO_BUILD_LIBS = os.path.join(HELLO_DIR, "build", "libs")

def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def collect_classes(dir_path: str):
    classes = {}
    if not os.path.exists(dir_path):
        return classes
    for root, _, files in os.walk(dir_path):
        for f in files:
            if f.endswith(".class") and not f.endswith("Test.class"):
                full = os.path.join(root, f)
                rel = os.path.relpath(full, dir_path).replace("\\", "/")
                classes[rel] = full
    return classes

def build():
    if not os.path.exists(HELLO_CLASSES_DIR):
        print(f"[-] Classes dir not found: {HELLO_CLASSES_DIR}. Please run ./gradlew :samples:kototoro-hello:classes first.")
        return 1

    os.makedirs(HELLO_APK_DIR, exist_ok=True)
    os.makedirs(HELLO_BUILD_LIBS, exist_ok=True)

    classes = collect_classes(HELLO_CLASSES_DIR)
    if not classes:
        print("[-] No compiled classes found in hello sample")
        return 1

    jar_filename = "kaziyami-hello.jar"
    repo_jar_path = os.path.join(HELLO_APK_DIR, jar_filename)
    libs_jar_path = os.path.join(HELLO_BUILD_LIBS, jar_filename)

    with tempfile.TemporaryDirectory() as tmp_dir:
        class_files = list(classes.values())
        print(f"[*] Dexing {len(class_files)} hello sample classes via D8...")
        try:
            tools = android_sdk.get_sdk_tools()
            android_jar = tools["android_jar"]
            d8_args = ["--release", "--min-api", "21", "--lib", android_jar, "--output", tmp_dir] + class_files
            res = android_sdk.run_d8(d8_args)
            dex_path = os.path.join(tmp_dir, "classes.dex")
            has_dex = res.returncode == 0 and os.path.exists(dex_path)
            if not has_dex:
                print(f"[-] Warning: D8 failed ({res.stderr.strip()}), building class-only jar")
                dex_path = None
        except Exception as e:
            print(f"[-] D8 execution exception: {e}")
            dex_path = None

        with zipfile.ZipFile(repo_jar_path, "w", zipfile.ZIP_DEFLATED) as zout:
            if dex_path and os.path.exists(dex_path):
                zout.write(dex_path, "classes.dex")
                print(f"[+] Added classes.dex ({os.path.getsize(dex_path)} bytes)")
            for rel, full in classes.items():
                zout.write(full, rel)

    shutil.copy2(repo_jar_path, libs_jar_path)
    jar_hash = sha256_file(repo_jar_path)
    jar_size = os.path.getsize(repo_jar_path)
    print(f"[+] Packaged {jar_filename} ({len(classes)} classes, {jar_size} bytes, sha256: {jar_hash[:16]}...)")

    # Generate repository index matching Kototoro ExtensionIndexDto
    index_entry = {
        "name": "Kaziyami Demo",
        "pkg": "app.kaziyami.kototoro.hello",
        "version": "1.0.0",
        "code": 1,
        "apk": jar_filename,
        "lang": "all",
        "nsfw": 1
    }
    index_list = [index_entry]

    with open(os.path.join(HELLO_REPO_DIR, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index_list, f, indent=2)

    with open(os.path.join(HELLO_REPO_DIR, "index.min.json"), "w", encoding="utf-8") as f:
        json.dump(index_list, f, separators=(",", ":"))

    repo_meta = {
        "meta": {
            "name": "Kaziyami Demo Repository",
            "shortName": "kaziyami-demo",
            "website": "https://github.com/kenewjr/kaziyami",
            "signingKeyFingerprint": "0" * 64
        },
        "index_v2": "index.min.json"
    }
    with open(os.path.join(HELLO_REPO_DIR, "repo.json"), "w", encoding="utf-8") as f:
        json.dump(repo_meta, f, indent=2)

    sums_path = os.path.join(HELLO_REPO_DIR, "sha256sums.txt")
    with open(sums_path, "w", encoding="utf-8") as f:
        f.write(f"{jar_hash}  apk/{jar_filename}\n")

    print(f"[+] Generated Hello repository index at {HELLO_REPO_DIR}")
    return 0

if __name__ == "__main__":
    sys.exit(build())
