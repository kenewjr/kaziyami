#!/usr/bin/env python3
"""
Generates index.json and index.min.json conforming to Aniyomi custom extension repository schema.
"""

import json
import os
import hashlib

EXTENSIONS = [
    {
        "name": "Nekopoi",
        "pkg": "eu.kanade.tachiyomi.animeextension.id.nekopoi",
        "lang": "id",
        "version": "1.0.0",
        "code": 1,
        "nsfw": 1,
        "baseUrl": "https://nekopoi.care"
    },
    {
        "name": "IndoHentai",
        "pkg": "eu.kanade.tachiyomi.animeextension.id.indohentai",
        "lang": "id",
        "version": "1.0.0",
        "code": 1,
        "nsfw": 1,
        "baseUrl": "https://indohentai.net"
    },
    {
        "name": "Kuronime",
        "pkg": "eu.kanade.tachiyomi.animeextension.id.kuronime",
        "lang": "id",
        "version": "1.0.0",
        "code": 1,
        "nsfw": 1,
        "baseUrl": "https://kuronime.sbs"
    },
    {
        "name": "Hentaimama",
        "pkg": "eu.kanade.tachiyomi.animeextension.en.hentaimama",
        "lang": "en",
        "version": "1.0.0",
        "code": 1,
        "nsfw": 1,
        "baseUrl": "https://hentaimama.io"
    },
    {
        "name": "JAVSubbed",
        "pkg": "eu.kanade.tachiyomi.animeextension.en.javsubbed",
        "lang": "en",
        "version": "1.0.0",
        "code": 1,
        "nsfw": 1,
        "baseUrl": "https://javsubbed.net"
    },
    {
        "name": "Hanime",
        "pkg": "eu.kanade.tachiyomi.animeextension.en.hanime",
        "lang": "en",
        "version": "1.0.0",
        "code": 1,
        "nsfw": 1,
        "baseUrl": "https://hanime.tv"
    }
]

def generate_source_id(name: str, lang: str, version_step: int = 1) -> str:
    key = f"{name.lower()}/{lang}/{version_step}".encode("utf-8")
    md5 = hashlib.md5(key).digest()
    val = int.from_bytes(md5[:8], byteorder="big", signed=False) & 0x7FFFFFFFFFFFFFFF
    return str(val)

def build_index():
    repo_entries = []
    output_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "repo")
    os.makedirs(output_dir, exist_ok=True)

    for ext in EXTENSIONS:
        source_id = generate_source_id(ext["name"], ext["lang"])
        apk_filename = f"aniyomi-{ext['lang']}-{ext['name'].lower()}-v{ext['version']}.apk"

        entry = {
            "name": ext["name"],
            "pkg": ext["pkg"],
            "apk": apk_filename,
            "lang": ext["lang"],
            "code": ext["code"],
            "version": ext["version"],
            "nsfw": ext["nsfw"],
            "hasReadme": 0,
            "hasChangelog": 0,
            "sources": [
                {
                    "name": ext["name"],
                    "lang": ext["lang"],
                    "id": source_id,
                    "baseUrl": ext["baseUrl"]
                }
            ]
        }
        repo_entries.append(entry)

    index_json_path = os.path.join(output_dir, "index.json")
    index_min_path = os.path.join(output_dir, "index.min.json")

    with open(index_json_path, "w", encoding="utf-8") as f:
        json.dump(repo_entries, f, indent=2)

    with open(index_min_path, "w", encoding="utf-8") as f:
        json.dump(repo_entries, f, separators=(",", ":"))

    print(f"[+] Successfully generated repository indexes:")
    print(f"    - {index_json_path}")
    print(f"    - {index_min_path}")

if __name__ == "__main__":
    build_index()
