#!/usr/bin/env python3
"""
Tool script to refresh HTML fixtures for all 6 adult anime sources.
Uses polite browser headers and saves minimal stripped fixtures under src/test/resources/<source>/.
"""

import os
import sys
import urllib.request
import re

SOURCES = {
    "nekopoi": {
        "popular": "https://nekopoi.care/category/hentai/page/1/",
        "latest": "https://nekopoi.care/page/1/",
        "search": "https://nekopoi.care/?s=kanojo&page=1",
        "search_empty": "https://nekopoi.care/?s=zzzzz_not_found&page=1",
        "details": "https://nekopoi.care/hentai/kanojo-x-kanojo-x-kanojo-episode-1-sub-indo/",
        "episodes": "https://nekopoi.care/hentai/kanojo-x-kanojo-x-kanojo-episode-1-sub-indo/",
        "video_player": "https://nekopoi.care/hentai/kanojo-x-kanojo-x-kanojo-episode-1-sub-indo/"
    },
    "indohentai": {
        "popular": "https://indohentai.net/terbaru/page/1/",
        "latest": "https://indohentai.net/terbaru/page/1/",
        "search": "https://indohentai.net/?s=overflow&page=1",
        "search_empty": "https://indohentai.net/?s=zzzzz_not_found&page=1",
        "details": "https://indohentai.net/hentai/overflow-episode-1-sub-indo/",
        "episodes": "https://indohentai.net/hentai/overflow-episode-1-sub-indo/",
        "video_player": "https://indohentai.net/hentai/overflow-episode-1-sub-indo/"
    },
    "kuronime": {
        "popular": "https://kuronime.sbs/anime/?order=popular&page=1",
        "latest": "https://kuronime.sbs/anime/?order=update&page=1",
        "search": "https://kuronime.sbs/?s=tate&page=1",
        "search_empty": "https://kuronime.sbs/?s=zzzzz_not_found&page=1",
        "details": "https://kuronime.sbs/anime/tate-no-yuusha-no-nariagari/",
        "episodes": "https://kuronime.sbs/anime/tate-no-yuusha-no-nariagari/",
        "video_player": "https://kuronime.sbs/tate-no-yuusha-no-nariagari-episode-1/"
    },
    "hentaimama": {
        "popular": "https://hentaimama.io/tvshows/page/1/",
        "latest": "https://hentaimama.io/episodes/page/1/",
        "search": "https://hentaimama.io/?s=itadaki&page=1",
        "search_empty": "https://hentaimama.io/?s=zzzzz_not_found&page=1",
        "details": "https://hentaimama.io/tvshows/itadaki-seieki/",
        "episodes": "https://hentaimama.io/tvshows/itadaki-seieki/",
        "video_player": "https://hentaimama.io/episodes/itadaki-seieki-episode-1/"
    },
    "javsubbed": {
        "popular": "https://javsubbed.net/most-viewed/page/1/",
        "latest": "https://javsubbed.net/page/1/",
        "search": "https://javsubbed.net/?s=stars&page=1",
        "search_empty": "https://javsubbed.net/?s=zzzzz_not_found&page=1",
        "details": "https://javsubbed.net/video/stars-123-subbed/",
        "episodes": "https://javsubbed.net/video/stars-123-subbed/",
        "video_player": "https://javsubbed.net/video/stars-123-subbed/"
    },
    "hanime": {
        "popular": "https://hanime.tv/browse/trending?page=1",
        "latest": "https://hanime.tv/browse/recent?page=1",
        "search": "https://hanime.tv/search?q=ramune&page=1",
        "search_empty": "https://hanime.tv/search?q=zzzzz_not_found&page=1",
        "details": "https://hanime.tv/hentai/video/shoujo-ramune-1",
        "episodes": "https://hanime.tv/hentai/video/shoujo-ramune-1",
        "video_player": "https://hanime.tv/hentai/video/shoujo-ramune-1"
    }
}

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9,id;q=0.8"
}

def clean_html(html: str) -> str:
    # Strip tracking scripts, inline styles, analytics
    html = re.sub(r"<script[\s\S]*?</script>", "", html, flags=re.IGNORECASE)
    html = re.sub(r"<!--[\s\S]*?-->", "", html)
    return html.strip()

def refresh_source(name: str, endpoints: dict):
    base_dir = os.path.join("src", "id" if name in ["nekopoi", "indohentai", "kuronime"] else "en", name, "src", "test", "resources", name)
    os.makedirs(base_dir, exist_ok=True)

    print(f"[*] Refreshing fixtures for {name} -> {base_dir}")
    for key, url in endpoints.items():
        out_file = os.path.join(base_dir, f"{key}.html")
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=15) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
                cleaned = clean_html(raw)
                with open(out_file, "w", encoding="utf-8") as f:
                    f.write(cleaned)
                print(f"    [+] Saved {key}.html ({len(cleaned)} bytes)")
        except Exception as e:
            print(f"    [-] Could not fetch {url}: {e} (keeping existing fixture)")

def main():
    target = sys.argv[1] if len(sys.argv) > 1 else "all"
    if target == "all":
        for name, endpoints in SOURCES.items():
            refresh_source(name, endpoints)
    elif target in SOURCES:
        refresh_source(target, SOURCES[target])
    else:
        print(f"Unknown source: {target}. Available: {list(SOURCES.keys())}")

if __name__ == "__main__":
    main()
