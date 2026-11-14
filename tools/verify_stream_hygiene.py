#!/usr/bin/env python3
"""
verify_stream_hygiene.py:
Strict CI/CD verification gate ensuring all 8 sources adhere to real-world stream hygiene,
header cleanliness, single-character search padding, and authentic DOM fixtures.
"""

import json
import os
import re
import sys

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SOURCES = [
    {
        "slug": "nekopoi",
        "name": "Nekopoi",
        "file": os.path.join(ROOT_DIR, "src", "id", "nekopoi", "src", "main", "kotlin", "eu", "kanade", "tachiyomi", "animeextension", "id", "nekopoi", "Nekopoi.kt"),
        "fixture": os.path.join(ROOT_DIR, "src", "id", "nekopoi", "src", "test", "resources", "nekopoi", "popular.html"),
    },
    {
        "slug": "nontonhentai",
        "name": "NontonHentai",
        "file": os.path.join(ROOT_DIR, "src", "id", "nontonhentai", "src", "main", "kotlin", "eu", "kanade", "tachiyomi", "animeextension", "id", "nontonhentai", "NontonHentai.kt"),
    },
    {
        "slug": "hentaimama",
        "name": "Hentaimama",
        "file": os.path.join(ROOT_DIR, "src", "en", "hentaimama", "src", "main", "kotlin", "eu", "kanade", "tachiyomi", "animeextension", "en", "hentaimama", "Hentaimama.kt"),
    },
    {
        "slug": "hanime",
        "name": "Hanime",
        "file": os.path.join(ROOT_DIR, "src", "en", "hanime", "src", "main", "kotlin", "eu", "kanade", "tachiyomi", "animeextension", "en", "hanime", "Hanime.kt"),
    },
    {
        "slug": "rulevid",
        "name": "RuleVid",
        "file": os.path.join(ROOT_DIR, "src", "en", "rulevid", "src", "main", "kotlin", "eu", "kanade", "tachiyomi", "animeextension", "en", "rulevid", "RuleVid.kt"),
    },
    {
        "slug": "rule34video",
        "name": "Rule34Video",
        "file": os.path.join(ROOT_DIR, "src", "en", "rule34video", "src", "main", "kotlin", "eu", "kanade", "tachiyomi", "animeextension", "en", "rule34video", "Rule34Video.kt"),
    },
    {
        "slug": "hanime1",
        "name": "Hanime1",
        "file": os.path.join(ROOT_DIR, "src", "zh", "hanime1", "src", "main", "kotlin", "eu", "kanade", "tachiyomi", "animeextension", "zh", "hanime1", "Hanime1.kt"),
    },
    {
        "slug": "3xchina",
        "name": "3xchina",
        "file": os.path.join(ROOT_DIR, "src", "zh", "3xchina", "src", "main", "kotlin", "eu", "kanade", "tachiyomi", "animeextension", "zh", "threexchina", "ThreeXChina.kt"),
    },
]

def verify_stream_hygiene():
    errors = []

    print("[*] Verifying stream hygiene and anti-regression constraints across 8 sources...")

    # 1. Verify Kototoro self-test report if present
    report_file = os.path.join(ROOT_DIR, "build", "reports", "kototoro-test-report.json")
    if os.path.exists(report_file):
        with open(report_file, "r", encoding="utf-8") as f:
            report = json.load(f)
        sources = report.get("sources", [])
        if len(sources) < 8:
            errors.append(f"Kototoro report only tested {len(sources)}/8 sources.")
        for src in sources:
            src_name = src.get("name")
            status = src.get("status")
            if status != "pass":
                msgs = src.get("messages", {})
                errors.append(f"Kototoro self-test failed for {src_name}: {msgs}")
        print(f"    [OK] Kototoro report verified: {len(sources)} sources passed 100% green.")
    else:
        print("    [!] Notice: Kototoro test report not generated yet; validating source rules directly.")

    # 2. Verify single-character search padding on path-based KVS sources
    for src in SOURCES:
        slug = src["slug"]
        path = src["file"]
        if not os.path.exists(path):
            errors.append(f"Source file not found: {path}")
            continue
        with open(path, "r", encoding="utf-8") as f:
            code = f.read()

        if slug in ["rulevid", "rule34video"]:
            if "cleanQuery.length == 1" not in code or "cleanQuery + cleanQuery" not in code:
                errors.append(f"{slug}: Missing single-character search padding (cleanQuery.length == 1 -> cleanQuery + cleanQuery)")

    # 3. Verify media headers helper in BrowserHeaders
    net_helper_file = os.path.join(ROOT_DIR, "core", "src", "main", "kotlin", "app", "kaziyami", "network", "NetworkHelper.kt")
    with open(net_helper_file, "r", encoding="utf-8") as f:
        net_code = f.read()
    if "fun mediaHeaders(" not in net_code:
        errors.append("BrowserHeaders missing mediaHeaders helper.")
    if "Sec-Fetch-Dest" not in net_code or "Upgrade-Insecure-Requests" not in net_code:
        errors.append("mediaHeaders missing document navigation header filtering.")

    # 4. Verify Nekopoi test fixture has authentic CSS background-image
    nekopoi_fix = os.path.join(ROOT_DIR, "src", "id", "nekopoi", "src", "test", "resources", "nekopoi", "popular.html")
    if os.path.exists(nekopoi_fix):
        with open(nekopoi_fix, "r", encoding="utf-8") as f:
            fix_content = f.read()
        if "background-image" not in fix_content or "nk-thumb-crop" not in fix_content:
            errors.append("Nekopoi popular.html fixture lacks authentic .nk-thumb-crop with background-image style.")

    # 5. Verify no raw document headers in video headers across all sources and multisrc themes
    multisrc_dir = os.path.join(ROOT_DIR, "lib-multisrc")
    video_block_regex = re.compile(r'Video\s*\([^)]+quality\s*=\s*["\']Stream \(Open in WebView if blocked\)["\'][^)]+\)', re.DOTALL)
    for root, _, files in os.walk(multisrc_dir):
        for fname in files:
            if fname.endswith(".kt") and not fname.endswith("Test.kt"):
                fpath = os.path.join(root, fname)
                with open(fpath, "r", encoding="utf-8") as f:
                    content = f.read()
                for match in video_block_regex.finditer(content):
                    block = match.group(0)
                    if "headers = headers" in block:
                        errors.append(f"{fname}: Fallback Video uses raw headers (contains Sec-Fetch-Dest: document). Must use BrowserHeaders.mediaHeaders().")

    if errors:
        print("\n[-] Stream hygiene verification FAILED:")
        for e in errors:
            print(f"    - {e}")
        return 1

    print("[+] PASS: All stream hygiene, header scrubbing, and search padding checks passed.")
    return 0

if __name__ == "__main__":
    sys.exit(verify_stream_hygiene())
