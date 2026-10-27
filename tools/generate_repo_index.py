#!/usr/bin/env python3
"""
generate_repo_index.py:
Generates repository indexes for both distribution variants:
- Mode A (Aniyomi): repo/index.json, repo/index.min.json, repo/repo.json, repo/sha256sums.txt
- Mode B (Kototoro JAR): repo/jar/index.json, repo/jar/index.min.json, repo/jar/repo.json, repo/jar/sha256sums.txt
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
JAR_DIR = os.path.join(REPO_DIR, "jar")

KNOWN_BASE_URLS = {
    "Nekopoi": "https://nekopoi.care",
    "IndoHentai": "https://indohentai.net",
    "NontonHentai": "https://nontonhentai.net",
    "Hentaimama": "https://hentaimama.io",
    "JAVSubbed": "https://javsubbed.net",
    "Hanime": "https://hanime.tv"
}

SOURCES_DEF = [
    {"name": "Nekopoi", "lang": "id", "slug": "nekopoi", "url": "https://nekopoi.care"},
    {"name": "IndoHentai", "lang": "id", "slug": "indohentai", "url": "https://indohentai.net"},
    {"name": "NontonHentai", "lang": "id", "slug": "nontonhentai", "url": "https://nontonhentai.net"},
    {"name": "Hentaimama", "lang": "en", "slug": "hentaimama", "url": "https://hentaimama.io"},
    {"name": "JAVSubbed", "lang": "en", "slug": "javsubbed", "url": "https://javsubbed.net"},
    {"name": "Hanime", "lang": "en", "slug": "hanime", "url": "https://hanime.tv"}
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
    version_code = int(code_m.group(1)) if code_m else 2
    lib_m = re.search(r'val\s+libVersion\s*=\s*"([^"]+)"', content)
    lib_ver = lib_m.group(1) if lib_m else "1.4"
    if lib_ver == "14":
        lib_ver = "1.4"
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
        },
        "index_v2": "index.min.json"
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

def generate_kototoro_repo():
    """Generates Mode B Kototoro JAR repository metadata."""
    os.makedirs(JAR_DIR, exist_ok=True)
    source_ids = load_source_ids()

    # Prune older JARs (keep latest 3)
    prune_old_artifacts(JAR_DIR, ".jar", keep=3)

    jar_files = [f for f in os.listdir(JAR_DIR) if f.endswith(".jar")] if os.path.exists(JAR_DIR) else []
    jar_files.sort()

    print(f"[+] Mode B (Kototoro JAR): Inspecting JARs in {JAR_DIR}...")

    # Build ExtensionStoreIndex schema
    extensions = []

    # 1. Main all-in-one suite entry
    suite_code = max(get_source_version(f"src/{s['lang']}/{s['slug']}")["versionCode"] for s in SOURCES_DEF)
    suite_ver = f"1.4.{suite_code}"
    all_jar = f"kaziyami-all-v{suite_ver}.jar"
    all_jar_path = os.path.join(JAR_DIR, all_jar)
    all_sha = sha256_file(all_jar_path) if os.path.exists(all_jar_path) else ""
    all_size = os.path.getsize(all_jar_path) if os.path.exists(all_jar_path) else 0

    all_sources = []
    for s in SOURCES_DEF:
        name, lang, url = s["name"], s["lang"], s["url"]
        sid = int(source_ids.get((name, lang), "0"))
        all_sources.append({
            "id": sid,
            "name": name,
            "language": lang,
            "homeUrl": url
        })

    extensions.append({
        "name": "Kaziyami: Adult Anime Suite",
        "packageName": "app.kaziyami.kototoro",
        "resources": {
            "apkUrl": "",
            "iconUrl": "icon/app.kaziyami.kototoro.png",
            "jarUrl": f"jar/{all_jar}"
        },
        "extensionLib": "1.4",
        "versionCode": suite_code,
        "versionName": suite_ver,
        "sha256": all_sha,
        "size": all_size,
        "contentWarning": "NSFW",
        "sources": all_sources
    })

    # 2. Individual source entries
    for s in SOURCES_DEF:
        name, lang, slug, url = s["name"], s["lang"], s["slug"], s["url"]
        sid = int(source_ids.get((name, lang), "0"))
        s_ver = get_source_version(f"src/{lang}/{slug}")
        s_jar = f"kaziyami-{slug}-v{s_ver['versionName']}.jar"
        s_jar_path = os.path.join(JAR_DIR, s_jar)
        s_sha = sha256_file(s_jar_path) if os.path.exists(s_jar_path) else ""
        s_size = os.path.getsize(s_jar_path) if os.path.exists(s_jar_path) else 0

        extensions.append({
            "name": f"Kaziyami: {name}",
            "packageName": f"eu.kanade.tachiyomi.animeextension.{lang}.{slug}",
            "resources": {
                "apkUrl": "",
                "iconUrl": f"icon/eu.kanade.tachiyomi.animeextension.{lang}.{slug}.png",
                "jarUrl": f"jar/{s_jar}"
            },
            "extensionLib": "1.4",
            "versionCode": s_ver["versionCode"],
            "versionName": s_ver["versionName"],
            "sha256": s_sha,
            "size": s_size,
            "contentWarning": "NSFW",
            "sources": [
                {
                    "id": sid,
                    "name": name,
                    "language": lang,
                    "homeUrl": url
                }
            ]
        })

    kototoro_index = {
        "name": "Kaziyami Kototoro JAR Repository",
        "badgeLabel": "kaziyami-jar",
        "signingKey": "",
        "contact": {
            "website": "https://github.com/kenewjr/kaziyami"
        },
        "extensionList": {
            "extensions": extensions
        }
    }

    # 1. repo/jar/index.json
    jar_index_path = os.path.join(JAR_DIR, "index.json")
    with open(jar_index_path, "w", encoding="utf-8") as f:
        json.dump(kototoro_index, f, indent=2)

    # 2. repo/jar/index.min.json
    jar_min_path = os.path.join(JAR_DIR, "index.min.json")
    with open(jar_min_path, "w", encoding="utf-8") as f:
        json.dump(kototoro_index, f, separators=(",", ":"))

    # 3. repo/jar/repo.json
    jar_repo_meta = {
        "meta": {
            "name": "Kaziyami Kototoro JARs",
            "shortName": "kaziyami-jar",
            "website": "https://github.com/kenewjr/kaziyami",
            "signingKeyFingerprint": ""
        },
        "index_v2": "index.min.json"
    }
    with open(os.path.join(JAR_DIR, "repo.json"), "w", encoding="utf-8") as f:
        json.dump(jar_repo_meta, f, indent=2)

    # 4. repo/jar/sha256sums.txt
    jar_sums = os.path.join(JAR_DIR, "sha256sums.txt")
    with open(jar_sums, "w", encoding="utf-8") as f:
        for fname in sorted(os.listdir(JAR_DIR)):
            if fname.endswith(".jar"):
                h = sha256_file(os.path.join(JAR_DIR, fname))
                f.write(f"{h}  {fname}\n")

    print(f"[+] Mode B: Wrote Kototoro JAR repo index files ({len(extensions)} extensions)")

def main():
    parser = argparse.ArgumentParser(description="Generate Kaziyami repository indexes (Modes A and B).")
    parser.add_argument("--mode", choices=["all", "aniyomi", "kototoro"], default="all",
                        help="Repository mode to generate: aniyomi, kototoro, or all (default: all)")
    parser.add_argument("--fingerprint", help="Signing certificate SHA-256 fingerprint for repo.json")
    args = parser.parse_args()

    if args.mode in ("all", "aniyomi"):
        generate_aniyomi_repo(fingerprint=args.fingerprint)

    if args.mode in ("all", "kototoro"):
        generate_kototoro_repo()

    return 0

if __name__ == "__main__":
    sys.exit(main())
