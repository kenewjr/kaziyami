#!/usr/bin/env python3
"""
generate_repo_index.py:
Reads built APKs and their AndroidManifest.xml as the single source of truth to generate
repo/index.json, repo/index.min.json, and repo/repo.json conforming to Kaziyami Aniyomi-compatible repository schema.
"""

import json
import os
import re
import sys
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO_DIR = os.path.join(ROOT_DIR, "repo")
APK_DIR = os.path.join(REPO_DIR, "apk")

KNOWN_BASE_URLS = {
    "Nekopoi": "https://nekopoi.care",
    "IndoHentai": "https://indohentai.net",
    "NontonHentai": "https://nontonhentai.net",
    "Hentaimama": "https://hentaimama.io",
    "JAVSubbed": "https://javsubbed.net",
    "Hanime": "https://hanime.tv"
}

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

def build_index(fingerprint=None):
    os.makedirs(REPO_DIR, exist_ok=True)
    source_ids = load_source_ids()

    # Find APKs
    apk_files = []
    if os.path.exists(APK_DIR):
        apk_files = [f for f in os.listdir(APK_DIR) if f.endswith(".apk")]
        apk_files.sort()

    repo_entries = []
    seen_pkgs = set()

    if apk_files:
        print(f"[+] Inspecting {len(apk_files)} built APKs in {APK_DIR} as single source of truth...")
        for apk_name in apk_files:
            apk_path = os.path.join(APK_DIR, apk_name)
            manifest = parse_manifest_from_apk(apk_path)

            pkg = manifest["pkg"]
            if pkg in seen_pkgs:
                raise ValueError(f"Duplicate package name detected: {pkg}")
            seen_pkgs.add(pkg)

            # e.g. eu.kanade.tachiyomi.animeextension.id.nekopoi -> lang=id, raw_name=nekopoi
            parts = pkg.split(".")
            lang = parts[-2]
            raw_name = parts[-1]

            # Match label e.g. "Kaziyami: Nekopoi" -> Source Name "Nekopoi"
            label = manifest["label"]
            source_name = label.replace("Kaziyami: ", "").strip()

            sid = source_ids.get((source_name, lang))
            if not sid:
                # Fallback search case-insensitive
                for (s_name, s_lang), s_id in source_ids.items():
                    if s_name.lower() == raw_name.lower() and s_lang == lang:
                        source_name = s_name
                        sid = s_id
                        break

            if not sid:
                raise ValueError(f"No permanent source ID found in docs/SOURCE_IDS.md for ({source_name}, {lang})")

            base_url = KNOWN_BASE_URLS.get(source_name, "https://example.com")

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
        # Fallback to source descriptors if APKs not built yet
        print("[*] No built APKs found in repo/apk/, using fallback metadata...")
        fallback_extensions = [
            ("Nekopoi", "id", "14.1", 1, "https://nekopoi.care"),
            ("IndoHentai", "id", "14.1", 1, "https://indohentai.net"),
            ("NontonHentai", "id", "14.1", 1, "https://nontonhentai.net"),
            ("Hentaimama", "en", "14.1", 1, "https://hentaimama.io"),
            ("JAVSubbed", "en", "14.1", 1, "https://javsubbed.net"),
            ("Hanime", "en", "14.1", 1, "https://hanime.tv"),
        ]
        for name, lang, ver, code, url in fallback_extensions:
            sid = source_ids.get((name, lang), "0")
            entry = {
                "name": f"Kaziyami: {name}",
                "pkg": f"eu.kanade.tachiyomi.animeextension.{lang}.{name.lower()}",
                "apk": f"kaziyami-{lang}.{name.lower()}-v{ver}.apk",
                "lang": lang,
                "code": code,
                "version": ver,
                "nsfw": 1,
                "hasReadme": 0,
                "hasChangelog": 0,
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

    # 1. Pretty index.json
    index_path = os.path.join(REPO_DIR, "index.json")
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(repo_entries, f, indent=2)
    print(f"[+] Wrote {index_path} ({len(repo_entries)} entries)")

    # 2. Minified index.min.json
    min_index_path = os.path.join(REPO_DIR, "index.min.json")
    with open(min_index_path, "w", encoding="utf-8") as f:
        json.dump(repo_entries, f, separators=(",", ":"))
    print(f"[+] Wrote {min_index_path}")

    # 3. repo.json metadata block
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
    repo_meta_path = os.path.join(REPO_DIR, "repo.json")
    with open(repo_meta_path, "w", encoding="utf-8") as f:
        json.dump(repo_meta, f, indent=2)
    print(f"[+] Wrote {repo_meta_path} (fingerprint: {fp})")

    # 4. Copy mirrors.json to repo if present in tools/
    src_mirrors = os.path.join(ROOT_DIR, "tools", "mirrors.json")
    if os.path.exists(src_mirrors):
        import shutil
        shutil.copy2(src_mirrors, os.path.join(REPO_DIR, "mirrors.json"))
        print("[+] Copied tools/mirrors.json to repo/mirrors.json")

if __name__ == "__main__":
    fp = sys.argv[1] if len(sys.argv) > 1 else None
    build_index(fingerprint=fp)
