#!/usr/bin/env python3
"""
scripts/new_source.py
Scaffolds a new Kaziyami extension source following repository invariants.
"""
import argparse
import hashlib
import json
import os
import re
import sys

def calculate_source_id(name: str, lang: str) -> int:
    key = f"{name.lower()}/{lang}/1"
    digest = hashlib.md5(key.encode("utf-8")).digest()
    raw_id = int.from_bytes(digest[:8], byteorder="big", signed=True)
    return abs(raw_id)

def create_source(name: str, lang: str, engine: str, base_url: str):
    slug = re.sub(r"[^a-z0-9]", "", name.lower())
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    source_dir = os.path.join(root_dir, "src", lang, slug)
    pkg = f"eu.kanade.tachiyomi.animeextension.{lang}.{slug}"
    pkg_dir = os.path.join(source_dir, "src", "main", "kotlin", *pkg.split("."))
    test_pkg_dir = os.path.join(source_dir, "src", "test", "kotlin", *pkg.split("."))
    res_dir = os.path.join(source_dir, "src", "test", "resources", slug)

    os.makedirs(pkg_dir, exist_ok=True)
    os.makedirs(test_pkg_dir, exist_ok=True)
    os.makedirs(res_dir, exist_ok=True)

    # 1. Config JSON in sources/
    sources_dir = os.path.join(root_dir, "sources")
    os.makedirs(sources_dir, exist_ok=True)
    config_file = os.path.join(sources_dir, f"{slug}.json")
    config_data = {
        "name": name,
        "lang": lang,
        "baseUrl": base_url,
        "mirrors": [],
        "isNsfw": True,
        "popularPath": "/popular/page/%d/",
        "latestPath": "/latest/page/%d/",
        "searchPath": "/?s=%s&page=%d",
        "selectors": {
            "item": "article",
            "itemTitle": "h2",
            "itemUrl": "a[href]",
            "itemThumbnail": "img",
            "nextPage": ".pagination .next",
            "detailsTitle": "h1",
            "detailsThumbnail": ".poster img",
            "detailsDescription": ".desc",
            "detailsGenre": ".genre a",
            "detailsStatus": ".status",
            "detailsAuthor": ".studio",
            "episodeList": "ul.episodes li",
            "episodeUrl": "a[href]",
            "episodeName": ".epl-num",
            "episodeDate": ".epl-date",
            "playerIframes": "iframe[src]"
        },
        "quirks": {
            "episodesReversed": False,
            "usesAjaxPlayer": False,
            "ajaxPlayerEndpoint": "/wp-admin/admin-ajax.php",
            "dateFormat": "yyyy-MM-dd"
        }
    }
    with open(config_file, "w", encoding="utf-8") as f:
        json.dump(config_data, f, indent=2)

    # 2. build.gradle.kts
    gradle_content = f"""plugins {{
    id("kaziyami-source")
}}

extVersionCode = 1
val extName = "{name}"
val extClass = "{pkg}.{name}"
val baseEngine = "{engine}"

dependencies {{
    implementation(project(":lib-multisrc:{engine}"))
}}
"""
    with open(os.path.join(source_dir, "build.gradle.kts"), "w", encoding="utf-8") as f:
        f.write(gradle_content)

    # 3. Source Kotlin Class
    source_kt = f"""package {pkg}

import app.kaziyami.multisrc.{engine}.{engine.capitalize()}Theme
import app.kaziyami.core.SourceConfigLoader

class {name} : {engine.capitalize()}Theme(
    name = "{name}",
    lang = "{lang}",
    baseUrl = "{base_url}"
)
"""
    with open(os.path.join(pkg_dir, f"{name}.kt"), "w", encoding="utf-8") as f:
        f.write(source_kt)

    # 4. Test Skeleton
    test_kt = f"""package {pkg}

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class {name}Test {{
    private val source = {name}()

    @Test
    fun verifySourceMetadata() {{
        assertEquals("{name}", source.name)
        assertEquals("{lang}", source.lang)
        assertEquals("{base_url}", source.baseUrl)
    }}
}}
"""
    with open(os.path.join(test_pkg_dir, f"{name}Test.kt"), "w", encoding="utf-8") as f:
        f.write(test_kt)

    # 5. Checklist enforcing policies
    source_id = calculate_source_id(name, lang)
    checklist = f"""# Compliance & Quality Checklist: {name}

- [ ] **Adult (NSFW) Only**: Source strictly contains 18+ adult content.
- [ ] **Zero Illegal Content**: No depiction of minors or non-consensual material.
- [ ] **No Login / Paywall / DRM**: Content accessible without paid accounts or DRM.
- [ ] **Verified Video Hosts**: Playback verified across working video embed servers.
- [ ] **Evidence Collected**: HAR traces and HTML captures stored in `docs/evidence/{slug}/`.
- [ ] **Permanent Source ID**: Locked at `{source_id}L` in `docs/SOURCE_IDS.md`.
- [ ] **Offline Tests Passing**: Popular, details, episodes, and videoList unit tests green.
"""
    with open(os.path.join(source_dir, "CHECKLIST.md"), "w", encoding="utf-8") as f:
        f.write(checklist)

    print(f"[+] Successfully scaffolded {name} ({lang}) in {source_dir}")
    print(f"[+] Computed permanent Source ID: {source_id}L")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Scaffold a new Kaziyami extension source.")
    parser.add_argument("--name", required=True, help="Display name of the source (e.g. Nekopoi)")
    parser.add_argument("--lang", required=True, help="Language code (e.g. id, en)")
    parser.add_argument("--engine", default="generic", choices=["generic", "dooplay", "animestream", "retrotube"], help="Multisrc base engine")
    parser.add_argument("--url", required=True, help="Primary HTTPS base URL")

    args = parser.parse_args()
    create_source(args.name, args.lang, args.engine, args.url)
