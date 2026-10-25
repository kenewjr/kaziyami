#!/usr/bin/env python3
"""
tools/monitor_sources.py
Fetches live key pages, computes selector hit metrics, and alerts on layout drift.
"""
import json
import os
import re
import sys
import urllib.request

SOURCES = [
    {
        "name": "Nekopoi",
        "url": "https://nekopoi.care",
        "item_regex": r"<article[^>]*>",
        "episode_regex": r"<li[^>]*><a[^>]*href=['\"]https?://nekopoi\.[^>]*['\"]",
        "iframe_regex": r"<iframe[^>]*src=['\"]([^'\"]+)['\"]"
    },
    {
        "name": "IndoHentai",
        "url": "https://indohentai.net",
        "item_regex": r"<article[^>]*class=['\"][^'\"]*post[^'\"]*['\"]",
        "episode_regex": r"<a[^>]*class=['\"][^'\"]*ep-link[^'\"]*['\"]",
        "iframe_regex": r"<iframe[^>]*src=['\"]([^'\"]+)['\"]"
    },
    {
        "name": "NontonHentai",
        "url": "https://nontonhentai.net",
        "item_regex": r"<article[^>]*|class=['\"][^'\"]*animposx[^'\"]*['\"]",
        "episode_regex": r"<li[^>]*><a[^>]*href=['\"][^'\"]*episode[^'\"]*['\"]",
        "iframe_regex": r"<iframe[^>]*src=['\"]([^'\"]+)['\"]"
    },
    {
        "name": "Hentaimama",
        "url": "https://hentaimama.io",
        "item_regex": r"<article[^>]*class=['\"][^'\"]*item[^'\"]*['\"]",
        "episode_regex": r"<div[^>]*class=['\"][^'\"]*episodiotitle[^'\"]*['\"]",
        "iframe_regex": r"<iframe[^>]*src=['\"]([^'\"]+)['\"]"
    },
    {
        "name": "JAVSubbed",
        "url": "https://javsubbed.net",
        "item_regex": r"<div[^>]*class=['\"][^'\"]*item[^'\"]*['\"]",
        "episode_regex": r"<a[^>]*href=['\"][^'\"]*watch[^'\"]*['\"]",
        "iframe_regex": r"<iframe[^>]*src=['\"]([^'\"]+)['\"]"
    },
    {
        "name": "Hanime",
        "url": "https://hanime.tv",
        "item_regex": r"<div[^>]*class=['\"][^'\"]*video[^'\"]*['\"]|<a[^>]*href=['\"][^'\"]*/hentai/[^'\"]*['\"]",
        "episode_regex": r"/videos/hentai/",
        "iframe_regex": r"<iframe[^>]*src=['\"]([^'\"]+)['\"]"
    }
]

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9"
}

def monitor():
    report = {}
    alerts = []

    print("[*] Running structure health monitor on 6 sources...")
    for s in SOURCES:
        name = s["name"]
        url = s["url"]
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=15) as resp:
                code = resp.getcode()
                html = resp.read().decode("utf-8", errors="ignore")

                item_count = len(re.findall(s["item_regex"], html, re.IGNORECASE))
                report[name] = {
                    "http_code": code,
                    "item_matches": item_count,
                    "status": "HEALTHY" if item_count > 0 else "DEGRADED"
                }

                if item_count == 0:
                    alerts.append(f"Source {name} at {url} returned 0 items matching layout selector.")
        except Exception as e:
            report[name] = {
                "http_code": 0,
                "error": str(e),
                "status": "UNREACHABLE"
            }
            # Note regarding Cloudflare from hosted environments
            alerts.append(f"Source {name} failed with: {e}")

    os.makedirs("reports", exist_ok=True)
    with open("reports/structure_report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print("[+] Structure report written to reports/structure_report.json")

    if alerts:
        print("\n" + "="*70)
        print("ATTENTION: Potential structure drift or network challenge detected:")
        for a in alerts:
            print(f"  - {a}")
        print("\nNOTE: Hosted CI / datacenter IPs can encounter Cloudflare challenges")
        print("even when a mobile device works normally. Confirm on a physical device")
        print("before treating as broken.")
        print("="*70 + "\n")

if __name__ == "__main__":
    monitor()
