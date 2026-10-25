#!/usr/bin/env python3
"""
Downloads and normalizes HTML fixtures for offline testing.
Uses realistic browser navigation headers, strips scripts, trackers, and ad iframes,
and writes to src/test/resources/<source>/<page>.html.
"""

import argparse
import os
import re
import sys
import urllib.request

BROWSER_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9,id;q=0.8",
    "Accept-Encoding": "identity",
    "Sec-Ch-Ua": '"Not/A)Brand";v="8", "Chromium";v="126", "Google Chrome";v="126"',
    "Sec-Ch-Ua-Mobile": "?0",
    "Sec-Ch-Ua-Platform": '"Windows"',
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
    "Upgrade-Insecure-Requests": "1"
}

def clean_html(raw_html: str) -> str:
    # Strip script blocks
    cleaned = re.sub(r"(?is)<script\b.*?</script>", "", raw_html)
    # Strip tracking pixels and ad iframes
    cleaned = re.sub(r"(?is)<iframe\b[^>]*(?:googleads|doubleclick|adservice|disqus)[^>]*>.*?</iframe>", "", cleaned)
    # Strip unnecessary comments (keep conditional comments)
    cleaned = re.sub(r"(?is)<!--(?!\[if).*?-->", "", cleaned)
    # Collapse multiple blank lines
    cleaned = re.sub(r"\n\s*\n\s*\n", "\n\n", cleaned)
    return cleaned.strip()

def fetch_fixture(url: str, output_path: str):
    print(f"[*] Fetching: {url} -> {output_path}")
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    try:
        req = urllib.request.Request(url, headers=BROWSER_HEADERS)
        with urllib.request.urlopen(req, timeout=15) as resp:
            content = resp.read().decode("utf-8", errors="replace")
            cleaned = clean_html(content)
            with open(output_path, "w", encoding="utf-8") as f:
                f.write(cleaned)
            print(f"[+] Saved {len(cleaned)} bytes to {output_path}")
    except Exception as e:
        print(f"[-] FAILED to fetch {url}: {e}", file=sys.stderr)
        with open(output_path, "w", encoding="utf-8") as f:
            f.write(f"<!-- TODO: Unreachable in environment: {url} - Error: {e} -->\n<html><body><p>Offline placeholder</p></body></html>")

def main():
    parser = argparse.ArgumentParser(description="Fetch and normalize HTML fixtures for Aniyomi extensions.")
    parser.add_argument("--source", help="Source name (e.g. nekopoi, hentaimama, javsubbed)")
    parser.add_argument("--page", help="Page type (popular, latest, details, episodes, video_player, search)")
    parser.add_argument("--url", help="URL to fetch")
    parser.add_argument("--out", help="Custom output filepath")

    args = parser.parse_args()

    if args.url and args.out:
        fetch_fixture(args.url, args.out)
    elif args.source and args.page and args.url:
        target = os.path.join("src", "test", "resources", args.source, f"{args.page}.html")
        fetch_fixture(args.url, target)
    else:
        # Run standard refresh
        from tools.refresh_fixtures import main as refresh_all
        refresh_all()

if __name__ == "__main__":
    main()
