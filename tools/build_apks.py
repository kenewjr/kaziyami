#!/usr/bin/env python3
"""
build_apks.py:
Packages and signs release APKs for all Kaziyami extensions, generates sha256sums.txt,
and triggers repo index generation.
"""

import base64
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

EXTENSIONS = [
    {
        "lang": "id",
        "name": "Nekopoi",
        "dir": "src/id/nekopoi",
        "multisrc": "lib-multisrc/generic"
    },
    {
        "lang": "id",
        "name": "IndoHentai",
        "dir": "src/id/indohentai",
        "multisrc": "lib-multisrc/generic"
    },
    {
        "lang": "id",
        "name": "NontonHentai",
        "dir": "src/id/nontonhentai",
        "multisrc": "lib-multisrc/animestream"
    },
    {
        "lang": "en",
        "name": "Hentaimama",
        "dir": "src/en/hentaimama",
        "multisrc": "lib-multisrc/dooplay"
    },
    {
        "lang": "en",
        "name": "JAVSubbed",
        "dir": "src/en/javsubbed",
        "multisrc": "lib-multisrc/retrotube"
    },
    {
        "lang": "en",
        "name": "Hanime",
        "dir": "src/en/hanime",
        "multisrc": "lib-multisrc/generic"
    }
]

def parse_extension_metadata(dir_path):
    build_file = os.path.join(ROOT_DIR, dir_path, "build.gradle.kts")
    with open(build_file, "r", encoding="utf-8") as f:
        content = f.read()

    ext_name = re.search(r'val\s+extName\s*=\s*"([^"]+)"', content).group(1)
    ext_class = re.search(r'val\s+extClass\s*=\s*"([^"]+)"', content).group(1)
    ext_version_code = int(re.search(r'val\s+extVersionCode\s*=\s*(\d+)', content).group(1))
    lib_version = re.search(r'val\s+libVersion\s*=\s*"([^"]+)"', content).group(1)
    is_nsfw = "true" in re.search(r'val\s+isNsfw\s*=\s*(true|false)', content).group(1)

    return {
        "extName": ext_name,
        "extClass": ext_class,
        "extVersionCode": ext_version_code,
        "libVersion": lib_version,
        "version": f"{lib_version}.{ext_version_code}",
        "isNsfw": is_nsfw
    }

def check_icons():
    print("[+] Checking extension icons...")
    for ext in EXTENSIONS:
        src_dir = os.path.join(ROOT_DIR, ext["dir"])
        xml_icon = os.path.join(src_dir, "src", "main", "res", "drawable", "ic_launcher.xml")
        png_icon = os.path.join(ROOT_DIR, "art", "icons", f"{ext['name'].lower()}.png")
        if not os.path.exists(xml_icon) and not os.path.exists(png_icon):
            raise FileNotFoundError(f"[!] Extension {ext['name']} lacks an icon! Missing {xml_icon}")
    print("[+] All extension icons verified.")

def get_signing_config():
    # 1. Environment secrets
    b64_keystore = os.environ.get("KAZIYAMI_KEYSTORE_B64")
    keystore_pw = os.environ.get("KAZIYAMI_KEYSTORE_PASSWORD")
    key_alias = os.environ.get("KAZIYAMI_KEY_ALIAS", "kaziyami")
    key_pw = os.environ.get("KAZIYAMI_KEY_PASSWORD", keystore_pw)

    if b64_keystore and keystore_pw:
        tmp_dir = os.path.join(ROOT_DIR, "build", "tmp_signing")
        os.makedirs(tmp_dir, exist_ok=True)
        keystore_path = os.path.join(tmp_dir, "release.keystore")
        with open(keystore_path, "wb") as f:
            f.write(base64.b64decode(b64_keystore.strip()))
        return {
            "path": keystore_path,
            "storepass": keystore_pw,
            "alias": key_alias,
            "keypass": key_pw,
            "is_release": True
        }

    # 2. Local keystore.properties
    props_path = os.path.join(ROOT_DIR, "keystore.properties")
    if os.path.exists(props_path):
        props = {}
        with open(props_path, "r", encoding="utf-8") as f:
            for line in f:
                if "=" in line and not line.strip().startswith("#"):
                    k, v = line.strip().split("=", 1)
                    props[k.strip()] = v.strip()
        ks_file = props.get("keystore.file", "kaziyami.keystore")
        if not os.path.isabs(ks_file):
            ks_file = os.path.join(ROOT_DIR, ks_file)
        if os.path.exists(ks_file):
            return {
                "path": ks_file,
                "storepass": props.get("keystore.password", ""),
                "alias": props.get("key.alias", "kaziyami"),
                "keypass": props.get("key.password", props.get("keystore.password", "")),
                "is_release": True
            }

    # 3. Fallback: generate test keystore for build simulation / PR
    test_dir = os.path.join(ROOT_DIR, "build", "test_keystore")
    os.makedirs(test_dir, exist_ok=True)
    test_ks = os.path.join(test_dir, "test.keystore")
    if not os.path.exists(test_ks):
        print("[*] Generating throwaway test keystore for build simulation...")
        subprocess.check_call([
            "keytool", "-genkeypair",
            "-alias", "kaziyami",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "365",
            "-keystore", test_ks,
            "-storepass", "kaziyamitest",
            "-keypass", "kaziyamitest",
            "-dname", "CN=Kaziyami Test, OU=Kaziyami, O=Kaziyami, C=ID"
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return {
        "path": test_ks,
        "storepass": "kaziyamitest",
        "alias": "kaziyami",
        "keypass": "kaziyamitest",
        "is_release": False
    }

def sign_apk(apk_path, signing_cfg):
    cmd = [
        "jarsigner",
        "-sigalg", "SHA256withRSA",
        "-digestalg", "SHA-256",
        "-keystore", signing_cfg["path"],
        "-storepass", signing_cfg["storepass"],
        "-keypass", signing_cfg["keypass"],
        apk_path,
        signing_cfg["alias"]
    ]
    subprocess.check_call(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def create_apk(ext, meta, signing_cfg, output_apk_path):
    pkg_name = f"eu.kanade.tachiyomi.animeextension.{ext['lang']}.{ext['name'].lower()}"
    manifest_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{pkg_name}"
    android:versionCode="{meta['extVersionCode']}"
    android:versionName="{meta['version']}">
    <uses-feature android:name="tachiyomi.animeextension" />
    <application
        android:label="Kaziyami: {meta['extName']}"
        android:icon="@drawable/ic_launcher"
        android:hasCode="true">
        <meta-data
            android:name="tachiyomi.animeextension.class"
            android:value="{meta['extClass']}" />
        <meta-data
            android:name="tachiyomi.animeextension.version"
            android:value="{meta['libVersion']}" />
        <meta-data
            android:name="tachiyomi.animeextension.nsfw"
            android:value="{"1" if meta['isNsfw'] else "0"}" />
    </application>
</manifest>
"""

    os.makedirs(os.path.dirname(output_apk_path), exist_ok=True)
    if os.path.exists(output_apk_path):
        os.remove(output_apk_path)

    with zipfile.ZipFile(output_apk_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        # 1. Manifest
        zf.writestr("AndroidManifest.xml", manifest_xml.strip())

        # 2. Icon
        icon_path = os.path.join(ROOT_DIR, ext["dir"], "src", "main", "res", "drawable", "ic_launcher.xml")
        if os.path.exists(icon_path):
            with open(icon_path, "rb") as f:
                zf.writestr("res/drawable/ic_launcher.xml", f.read())

        # 3. Classes from extension
        ext_classes_dir = os.path.join(ROOT_DIR, ext["dir"], "build", "classes", "kotlin", "main")
        if os.path.exists(ext_classes_dir):
            for root, _, files in os.walk(ext_classes_dir):
                for file in files:
                    full_p = os.path.join(root, file)
                    rel_p = os.path.relpath(full_p, ext_classes_dir)
                    zf.write(full_p, rel_p)

        # 4. Classes from core
        core_classes_dir = os.path.join(ROOT_DIR, "core", "build", "classes", "kotlin", "main")
        if os.path.exists(core_classes_dir):
            for root, _, files in os.walk(core_classes_dir):
                for file in files:
                    full_p = os.path.join(root, file)
                    rel_p = os.path.relpath(full_p, core_classes_dir)
                    if rel_p not in zf.namelist():
                        zf.write(full_p, rel_p)

        # 5. Classes from multisrc
        if ext.get("multisrc"):
            multi_dir = os.path.join(ROOT_DIR, ext["multisrc"], "build", "classes", "kotlin", "main")
            if os.path.exists(multi_dir):
                for root, _, files in os.walk(multi_dir):
                    for file in files:
                        full_p = os.path.join(root, file)
                        rel_p = os.path.relpath(full_p, multi_dir)
                        if rel_p not in zf.namelist():
                            zf.write(full_p, rel_p)

    # Sign the APK
    sign_apk(output_apk_path, signing_cfg)

def compute_sha256(file_path):
    h = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def main():
    check_icons()
    signing_cfg = get_signing_config()
    print(f"[+] Using signing keystore: {signing_cfg['path']} (release: {signing_cfg['is_release']})")

    repo_apk_dir = os.path.join(ROOT_DIR, "repo", "apk")
    os.makedirs(repo_apk_dir, exist_ok=True)

    built_apks = []
    sha256_lines = []

    for ext in EXTENSIONS:
        meta = parse_extension_metadata(ext["dir"])
        apk_name = f"kaziyami-{ext['lang']}.{ext['name'].lower()}-v{meta['version']}.apk"
        apk_path = os.path.join(repo_apk_dir, apk_name)

        print(f"[+] Assembling APK: {apk_name} ...")
        create_apk(ext, meta, signing_cfg, apk_path)

        apk_hash = compute_sha256(apk_path)
        sha256_lines.append(f"{apk_hash}  {apk_name}")
        built_apks.append((ext, meta, apk_path, apk_hash))
        print(f"    -> SHA256: {apk_hash}")

    # Write sha256sums.txt
    sha256_file = os.path.join(ROOT_DIR, "repo", "sha256sums.txt")
    with open(sha256_file, "w", encoding="utf-8") as f:
        f.write("\n".join(sha256_lines) + "\n")
    print(f"[+] Wrote {sha256_file}")

    # Extract certificate fingerprint from the first built APK
    sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
    from get_apk_fingerprint import get_apk_fingerprint
    first_apk = built_apks[0][2]
    fingerprint = get_apk_fingerprint(first_apk)
    print(f"[+] Certificate SHA-256 Fingerprint: {fingerprint}")

    # Run generate_repo_index.py with the detected fingerprint
    from generate_repo_index import build_index
    build_index(fingerprint=fingerprint)

if __name__ == "__main__":
    main()
