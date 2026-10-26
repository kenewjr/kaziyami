#!/usr/bin/env python3
"""
verify_remote_repo.py:
Verifies that the remote GitHub repository index.min.json is valid JSON
and all extension APKs listed within it resolve with HTTP 200.
"""
import json
import sys
import urllib.request

REPO_INDEX_URL = "https://raw.githubusercontent.com/kenewjr/kaziyami/repo/index.min.json"
BASE_APK_URL = "https://raw.githubusercontent.com/kenewjr/kaziyami/repo/apk/"

def verify():
    print(f"[*] Checking repo index: {REPO_INDEX_URL}")
    req = urllib.request.Request(REPO_INDEX_URL, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            if resp.status != 200:
                print(f"[-] Index fetch failed: HTTP {resp.status}")
                return 1
            data = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"[-] Failed to fetch or parse index: {e}")
        return 1

    print(f"[+] Index is valid JSON containing {len(data)} extensions.")
    all_ok = True

    for item in data:
        name = item.get("name", "Unknown")
        apk = item.get("apk")
        if not apk:
            print(f"[-] Entry '{name}' has no 'apk' field")
            all_ok = False
            continue

        apk_url = f"{BASE_APK_URL}{apk}"
        apk_req = urllib.request.Request(apk_url, headers={"User-Agent": "Mozilla/5.0"}, method="HEAD")
        try:
            with urllib.request.urlopen(apk_req, timeout=15) as apk_resp:
                if apk_resp.status == 200:
                    cl = apk_resp.headers.get("Content-Length", "unknown")
                    print(f"  [+] {name} -> {apk} (HTTP 200, {cl} bytes)")
                else:
                    print(f"  [-] {name} -> {apk} (HTTP {apk_resp.status})")
                    all_ok = False
        except Exception as e:
            print(f"  [-] {name} -> {apk} (Error: {e})")
            all_ok = False

    if all_ok:
        print("[+] All extension APKs resolved successfully.")
        return 0
    else:
        print("[-] Verification failed for one or more APKs.")
        return 1

if __name__ == "__main__":
    sys.exit(verify())
