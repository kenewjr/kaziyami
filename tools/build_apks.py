#!/usr/bin/env python3
"""
build_apks.py:
Packages and signs release APKs for all Kaziyami extensions, compiles Dalvik executable (classes.dex)
and Android Binary XML (AXML) manifests using D8 and AAPT2, aligns 4-byte boundaries using zipalign,
signs using apksigner with v2 and v3 signature schemes, generates sha256sums.txt,
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
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
import android_sdk

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

def get_signing_config(require_release=False):
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

    if require_release:
        raise RuntimeError("FATAL: Release signing secrets (KAZIYAMI_KEYSTORE_B64, KAZIYAMI_KEYSTORE_PASSWORD) are missing! Refusing to build unsigned or mock-signed release APKs.")

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

def create_apk(ext, meta, signing_cfg, output_apk_path, sdk_tools):
    pkg_name = f"eu.kanade.tachiyomi.animeextension.{ext['lang']}.{ext['name'].lower()}"
    manifest_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{pkg_name}"
    android:versionCode="{meta['extVersionCode']}"
    android:versionName="{meta['version']}">
    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="33" />
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
            android:name="tachiyomix.extensionLib"
            android:value="{meta['libVersion']}" />
        <meta-data
            android:name="tachiyomi.animeextension.nsfw"
            android:value="{"1" if meta['isNsfw'] else "0"}" />
    </application>
</manifest>
"""

    with tempfile.TemporaryDirectory() as tmp_dir:
        # 1. Compile resources (icon)
        res_dir = os.path.join(tmp_dir, "res", "drawable")
        os.makedirs(res_dir, exist_ok=True)
        
        # Check source icon
        ext_icon_xml = os.path.join(ROOT_DIR, ext["dir"], "src", "main", "res", "drawable", "ic_launcher.xml")
        png_icon = os.path.join(ROOT_DIR, "art", "icons", f"{ext['name'].lower()}.png")
        
        if os.path.exists(png_icon):
            shutil.copy(png_icon, os.path.join(res_dir, "ic_launcher.png"))
        elif os.path.exists(ext_icon_xml):
            shutil.copy(ext_icon_xml, os.path.join(res_dir, "ic_launcher.xml"))

        compiled_dir = os.path.join(tmp_dir, "compiled")
        os.makedirs(compiled_dir, exist_ok=True)
        res_compile = subprocess.run([
            sdk_tools["aapt2"], "compile",
            "--dir", os.path.join(tmp_dir, "res"),
            "-o", compiled_dir
        ], capture_output=True, text=True)
        if res_compile.returncode != 0:
            raise RuntimeError(f"aapt2 compile failed for {ext['name']}: {res_compile.stderr}")

        flat_files = [os.path.join(compiled_dir, f) for f in os.listdir(compiled_dir) if f.endswith(".flat")]

        # 2. Link binary AndroidManifest.xml and resources into base.apk
        manifest_path = os.path.join(tmp_dir, "AndroidManifest.xml")
        with open(manifest_path, "w", encoding="utf-8") as f:
            f.write(manifest_xml)

        base_apk = os.path.join(tmp_dir, "base.apk")
        link_cmd = [
            sdk_tools["aapt2"], "link",
            "-I", sdk_tools["android_jar"],
            "--manifest", manifest_path,
            "-o", base_apk
        ] + flat_files

        res_link = subprocess.run(link_cmd, capture_output=True, text=True)
        if res_link.returncode != 0:
            raise RuntimeError(f"aapt2 link failed for {ext['name']}: {res_link.stderr}")

        # 3. Compile classes into classes.dex using D8
        class_dirs = [
            os.path.join(ROOT_DIR, ext["dir"], "build", "classes", "kotlin", "main"),
            os.path.join(ROOT_DIR, "core", "build", "classes", "kotlin", "main")
        ]
        if ext.get("multisrc"):
            class_dirs.append(os.path.join(ROOT_DIR, ext["multisrc"], "build", "classes", "kotlin", "main"))

        # Bundle class files into a temporary jar for D8 input
        input_jar = os.path.join(tmp_dir, "input.jar")
        with zipfile.ZipFile(input_jar, "w") as zf:
            for c_dir in class_dirs:
                if os.path.exists(c_dir):
                    for root, _, files in os.walk(c_dir):
                        for file in files:
                            if file.endswith(".class"):
                                full_p = os.path.join(root, file)
                                rel_p = os.path.relpath(full_p, c_dir)
                                if rel_p not in zf.namelist():
                                    zf.write(full_p, rel_p)

        dex_out_dir = os.path.join(tmp_dir, "dex")
        os.makedirs(dex_out_dir, exist_ok=True)
        d8_cmd = [
            "java", "-cp", sdk_tools["d8_jar"], "com.android.tools.r8.D8",
            "--release", "--min-api", "21",
            "--lib", sdk_tools["android_jar"],
            "--output", dex_out_dir,
            input_jar
        ]
        res_d8 = subprocess.run(d8_cmd, capture_output=True, text=True)
        if res_d8.returncode != 0:
            raise RuntimeError(f"D8 failed for {ext['name']}: {res_d8.stderr}")

        classes_dex = os.path.join(dex_out_dir, "classes.dex")
        if not os.path.exists(classes_dex):
            raise FileNotFoundError(f"classes.dex was not generated for {ext['name']}")

        # 4. Add classes.dex into base_apk
        with zipfile.ZipFile(base_apk, "a") as zf:
            zf.write(classes_dex, "classes.dex")

        # 5. Zipalign to 4-byte boundaries
        aligned_apk = os.path.join(tmp_dir, "aligned.apk")
        align_cmd = [sdk_tools["zipalign"], "-p", "-f", "4", base_apk, aligned_apk]
        res_align = subprocess.run(align_cmd, capture_output=True, text=True)
        if res_align.returncode != 0:
            raise RuntimeError(f"zipalign failed for {ext['name']}: {res_align.stderr}")

        # 6. Sign using apksigner with v2 and v3 schemes
        os.makedirs(os.path.dirname(output_apk_path), exist_ok=True)
        if os.path.exists(output_apk_path):
            os.remove(output_apk_path)

        sign_cmd = [
            "java", "-jar", sdk_tools["apksigner_jar"], "sign",
            "--ks", signing_cfg["path"],
            "--ks-key-alias", signing_cfg["alias"],
            "--ks-pass", f"pass:{signing_cfg['storepass']}",
            "--key-pass", f"pass:{signing_cfg['keypass']}",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "true",
            "--out", output_apk_path,
            aligned_apk
        ]
        res_sign = subprocess.run(sign_cmd, capture_output=True, text=True)
        if res_sign.returncode != 0:
            raise RuntimeError(f"apksigner sign failed for {ext['name']}: {res_sign.stderr}")

        # 7. Verify signature
        verify_cmd = ["java", "-jar", sdk_tools["apksigner_jar"], "verify", "--verbose", output_apk_path]
        res_verify = subprocess.run(verify_cmd, capture_output=True, text=True)
        if res_verify.returncode != 0:
            raise RuntimeError(f"apksigner verify failed on generated {output_apk_path}: {res_verify.stderr}")

def compute_sha256(file_path):
    h = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def main():
    check_icons()
    debug_dir = None
    require_release = False
    
    if "--debug-dir" in sys.argv:
        idx = sys.argv.index("--debug-dir")
        if idx + 1 < len(sys.argv):
            debug_dir = sys.argv[idx + 1]
    if "--require-release" in sys.argv:
        require_release = True

    sdk_tools = android_sdk.get_sdk_tools()
    signing_cfg = get_signing_config(require_release=require_release)
    print(f"[+] Using signing keystore: {signing_cfg['path']} (release: {signing_cfg['is_release']})")

    out_apk_dir = os.path.abspath(debug_dir) if debug_dir else os.path.join(ROOT_DIR, "repo", "apk")
    if os.path.exists(out_apk_dir):
        for f in os.listdir(out_apk_dir):
            if f.endswith(".apk"):
                os.remove(os.path.join(out_apk_dir, f))
    os.makedirs(out_apk_dir, exist_ok=True)

    built_apks = []
    sha256_lines = []

    for ext in EXTENSIONS:
        meta = parse_extension_metadata(ext["dir"])
        apk_name = f"kaziyami-{ext['lang']}.{ext['name'].lower()}-v{meta['version']}.apk"
        apk_path = os.path.join(out_apk_dir, apk_name)

        print(f"[+] Assembling APK: {apk_name} ...")
        create_apk(ext, meta, signing_cfg, apk_path, sdk_tools)

        apk_hash = compute_sha256(apk_path)
        sha256_lines.append(f"{apk_hash}  {apk_name}")
        built_apks.append((ext, meta, apk_path, apk_hash))
        print(f"    -> SHA256: {apk_hash}")

    if debug_dir:
        print(f"[+] Debug APKs assembled in: {out_apk_dir}")
        return

    # Write sha256sums.txt
    sha256_file = os.path.join(ROOT_DIR, "repo", "sha256sums.txt")
    with open(sha256_file, "w", encoding="utf-8") as f:
        f.write("\n".join(sha256_lines) + "\n")
    print(f"[+] Wrote {sha256_file}")

    # Extract certificate fingerprint from the first built APK
    from get_apk_fingerprint import get_apk_fingerprint
    first_apk = built_apks[0][2]
    fingerprint = get_apk_fingerprint(first_apk)
    print(f"[+] Certificate SHA-256 Fingerprint: {fingerprint}")

    # Run generate_repo_index.py with the detected fingerprint
    from generate_repo_index import build_index
    build_index(fingerprint=fingerprint)

if __name__ == "__main__":
    main()
