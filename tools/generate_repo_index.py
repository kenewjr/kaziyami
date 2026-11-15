#!/usr/bin/env python3
"""
generate_repo_index.py:
Generates repository indexes for Mode A (Aniyomi APK):
repo/index.json, repo/index.min.json, repo/repo.json, repo/sha256sums.txt
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO_DIR = os.path.join(ROOT_DIR, "repo")
APK_DIR = os.path.join(REPO_DIR, "apk")

KNOWN_BASE_URLS = {
    "Nekopoi": "https://nekopoi.care",
    "NontonHentai": "https://nontonhentai.net",
    "Hentaimama": "https://hentaimama.io",
    "Hanime": "https://hanime.tv",
    "RuleVid": "https://rulevid.com",
    "Rule34Video": "https://rule34video.com",
    "Hanime1": "https://hanime1.me"
}

SOURCES_DEF = [
    {"name": "Nekopoi", "lang": "id", "slug": "nekopoi", "url": "https://nekopoi.care"},
    {"name": "NontonHentai", "lang": "id", "slug": "nontonhentai", "url": "https://nontonhentai.net"},
    {"name": "Hentaimama", "lang": "en", "slug": "hentaimama", "url": "https://hentaimama.io"},
    {"name": "Hanime", "lang": "en", "slug": "hanime", "url": "https://hanime.tv"},
    {"name": "RuleVid", "lang": "en", "slug": "rulevid", "url": "https://rulevid.com"},
    {"name": "Rule34Video", "lang": "en", "slug": "rule34video", "url": "https://rule34video.com"},
    {"name": "Hanime1", "lang": "zh", "slug": "hanime1", "url": "https://hanime1.me"}
]

def load_source_ids():
    source_ids_file = os.path.join(ROOT_DIR, "docs", "SOURCE_IDS.md")
    ids = {}
    if os.path.exists(source_ids_file):
        with open(source_ids_file, "r", encoding="utf-8") as f:
            for line in f:
                # | **Nekopoi** | `id` | Active | `7073589375897212735L` |
                m = re.search(r'\|\s*\*\*([^\*]+)\*\*\s*\|\s*`([^`]+)`\s*\|\s*[^\|]*\|\s*`(\d+)L?`', line)
                if m:
                    name, lang, sid = m.group(1).strip(), m.group(2).strip(), m.group(3).strip()
                    ids[(name, lang)] = sid
    return ids

def parse_manifest_from_apk(apk_path):
    sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
    import android_sdk
    return android_sdk.parse_apk_manifest(apk_path)

def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def get_source_version(src_dir: str):
    build_file = os.path.join(ROOT_DIR, src_dir, "build.gradle.kts")
    if not os.path.exists(build_file):
        return {"versionCode": 2, "versionName": "1.4.2"}
    with open(build_file, "r", encoding="utf-8") as f:
        content = f.read()
    code_m = re.search(r'val\s+extVersionCode\s*=\s*(\d+)', content)
    version_code = int(code_m.group(1)) if code_m else 1
    ext_ver_m = re.search(r'val\s+extVersion\s*=\s*"([^"]+)"', content)
    if ext_ver_m:
        version_name = ext_ver_m.group(1)
    else:
        lib_m = re.search(r'val\s+libVersion\s*=\s*"([^"]+)"', content)
        lib_ver = lib_m.group(1) if lib_m else "1.0"
        version_name = f"{lib_ver}.{version_code}"
    return {
        "versionCode": version_code,
        "versionName": version_name
    }

def prune_old_artifacts(directory: str, extension: str, keep: int = 3):
    if not os.path.exists(directory):
        return
    files = [f for f in os.listdir(directory) if f.endswith(extension)]
    families = {}
    for fname in files:
        m = re.match(r'^(kaziyami-[^v]+)-v([\d\.]+)\.' + re.escape(extension.lstrip('.')) + '$', fname)
        if m:
            family = m.group(1)
            ver_str = m.group(2)
            try:
                ver_tuple = tuple(int(x) for x in ver_str.split('.'))
            except ValueError:
                ver_tuple = (0,)
            families.setdefault(family, []).append((ver_tuple, fname))
    
    for family, file_list in families.items():
        file_list.sort(key=lambda x: x[0], reverse=True)
        to_delete = file_list[keep:]
        for _, old_fname in to_delete:
            old_path = os.path.join(directory, old_fname)
            try:
                os.remove(old_path)
                print(f"[+] Pruned older artifact (kept last {keep}): {old_fname}")
            except Exception as e:
                print(f"[-] Failed to prune {old_fname}: {e}")

def generate_aniyomi_repo(fingerprint=None):
    """Generates Mode A Aniyomi APK repository metadata."""
    os.makedirs(REPO_DIR, exist_ok=True)
    os.makedirs(APK_DIR, exist_ok=True)
    source_ids = load_source_ids()

    apk_files = [f for f in os.listdir(APK_DIR) if f.endswith(".apk")] if os.path.exists(APK_DIR) else []
    apk_files.sort()

    repo_entries = []
    seen_pkgs = set()

    if apk_files:
        print(f"[+] Mode A (Aniyomi): Inspecting {len(apk_files)} APKs in {APK_DIR}...")
        for apk_name in apk_files:
            apk_path = os.path.join(APK_DIR, apk_name)
            manifest = parse_manifest_from_apk(apk_path)

            pkg = manifest["pkg"]
            if pkg in seen_pkgs:
                raise ValueError(f"Duplicate package name detected: {pkg}")
            seen_pkgs.add(pkg)

            parts = pkg.split(".")
            lang = parts[-2]
            raw_name = parts[-1]

            label = manifest["label"]
            source_name = label.replace("Kaziyami: ", "").strip()

            sid = source_ids.get((source_name, lang))
            if not sid:
                for (s_name, s_lang), s_id in source_ids.items():
                    if s_name.lower() == raw_name.lower() and s_lang == lang:
                        source_name = s_name
                        sid = s_id
                        break

            if not sid:
                raise ValueError(f"No permanent source ID found for ({source_name}, {lang})")

            base_url = KNOWN_BASE_URLS.get(source_name, "https://example.com")

            apk_full_path = os.path.join(APK_DIR, apk_name)
            entry = {
                "name": label,
                "pkg": pkg,
                "apk": apk_name,
                "lang": lang,
                "code": manifest["versionCode"],
                "version": manifest["versionName"],
                "nsfw": manifest["nsfw"],
                "hasReadme": 0,
                "hasChangelog": 0,
                "icon": f"{pkg}.png",
                "sha256": sha256_file(apk_full_path),
                "size": os.path.getsize(apk_full_path),
                "sources": [
                    {
                        "name": source_name,
                        "lang": lang,
                        "id": str(sid),
                        "baseUrl": base_url
                    }
                ]
            }
            repo_entries.append(entry)
    else:
        print("[*] Mode A (Aniyomi): No built APKs found, using fallback metadata...")
        for s in SOURCES_DEF:
            name, lang, slug, url = s["name"], s["lang"], s["slug"], s["url"]
            sid = source_ids.get((name, lang), "0")
            s_ver = get_source_version(f"src/{lang}/{slug}")
            entry = {
                "name": f"Kaziyami: {name}",
                "pkg": f"eu.kanade.tachiyomi.animeextension.{lang}.{slug}",
                "apk": f"kaziyami-{lang}.{slug}-v{s_ver['versionName']}.apk",
                "lang": lang,
                "code": s_ver["versionCode"],
                "version": s_ver["versionName"],
                "nsfw": 1,
                "hasReadme": 0,
                "hasChangelog": 0,
                "icon": f"eu.kanade.tachiyomi.animeextension.{lang}.{slug}.png",
                "sha256": "",
                "size": 0,
                "sources": [
                    {
                        "name": name,
                        "lang": lang,
                        "id": str(sid),
                        "baseUrl": url
                    }
                ]
            }
            repo_entries.append(entry)

    # Prune older APKs (keep latest 3)
    prune_old_artifacts(APK_DIR, ".apk", keep=3)

    # 1. repo/index.json
    with open(os.path.join(REPO_DIR, "index.json"), "w", encoding="utf-8") as f:
        json.dump(repo_entries, f, indent=2)

    # 2. repo/index.min.json
    with open(os.path.join(REPO_DIR, "index.min.json"), "w", encoding="utf-8") as f:
        json.dump(repo_entries, f, separators=(",", ":"))

    # 3. repo/repo.json
    fp = fingerprint or "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    repo_meta = {
        "meta": {
            "name": "Kaziyami",
            "shortName": "kaziyami",
            "website": "https://github.com/kenewjr/kaziyami",
            "signingKeyFingerprint": fp
        }
    }
    with open(os.path.join(REPO_DIR, "repo.json"), "w", encoding="utf-8") as f:
        json.dump(repo_meta, f, indent=2)

    # 4. repo/sha256sums.txt
    apk_sums = os.path.join(REPO_DIR, "sha256sums.txt")
    with open(apk_sums, "w", encoding="utf-8") as f:
        for fname in sorted(os.listdir(APK_DIR)):
            if fname.endswith(".apk"):
                h = sha256_file(os.path.join(APK_DIR, fname))
                f.write(f"{h}  apk/{fname}\n")

    print(f"[+] Mode A: Wrote Aniyomi repo index files ({len(repo_entries)} entries)")

build_index = generate_aniyomi_repo

def main():
    parser = argparse.ArgumentParser(description="Generate Kaziyami repository index (Aniyomi APK format).")
    parser.add_argument("--mode", choices=["all", "aniyomi"], default="aniyomi",
                        help="Repository mode to generate (default: aniyomi)")
    parser.add_argument("--fingerprint", help="Signing certificate SHA-256 fingerprint for repo.json")
    args = parser.parse_args()

    generate_aniyomi_repo(fingerprint=args.fingerprint)
    return 0

if __name__ == "__main__":
    sys.exit(main())
