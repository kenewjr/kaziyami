#!/usr/bin/env python3
"""
verify_published_repo.py:
Verifies live repository assets on GitHub after publishing.
Asserts:
1. index.min.json, repo.json, and sha256sums.txt exist and return HTTP 200.
2. No URL contains deprecated 'kaziyami-extensions'.
3. Every APK URL returns HTTP 200, valid ZIP magic (PK\x03\x04), correct SHA-256, and matches local size.
4. Every icon URL returns HTTP 200.
5. Retries with backoff for CDN propagation.
"""

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.request

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def fetch_url(url, retries=5, delay=5):
    last_err = None
    for attempt in range(1, retries + 1):
        try:
            req = urllib.request.Request(
                url,
                headers={"User-Agent": "Kaziyami-Repo-Verifier/1.0", "Cache-Control": "no-cache"}
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                if resp.status == 200:
                    return resp.read(), resp.status
        except Exception as e:
            last_err = e
            if attempt < retries:
                time.sleep(delay)
    raise RuntimeError(f"Failed to fetch {url} after {retries} attempts: {last_err}")

def verify_published_repo(owner_repo, branch="repo", retries=5, delay=5):
    base_raw = f"https://raw.githubusercontent.com/{owner_repo}/{branch}"
    index_url = f"{base_raw}/index.min.json"
    repo_url = f"{base_raw}/repo.json"
    sha_url = f"{base_raw}/sha256sums.txt"

    print(f"[+] Verifying published repo at {base_raw} ...")

    # 1. Fetch repo.json
    print(f"[+] Checking repo.json: {repo_url}")
    repo_bytes, _ = fetch_url(repo_url, retries=retries, delay=delay)
    repo_text = repo_bytes.decode("utf-8")
    if "kaziyami-extensions" in repo_text:
        raise ValueError(f"repo.json contains deprecated URL substring 'kaziyami-extensions'")

    # 2. Fetch index.min.json
    print(f"[+] Checking index.min.json: {index_url}")
    index_bytes, _ = fetch_url(index_url, retries=retries, delay=delay)
    index_text = index_bytes.decode("utf-8")
    if "kaziyami-extensions" in index_text:
        raise ValueError(f"index.min.json contains deprecated URL substring 'kaziyami-extensions'")
    index_data = json.loads(index_text)

    # 3. Fetch sha256sums.txt
    print(f"[+] Checking sha256sums.txt: {sha_url}")
    sha_bytes, _ = fetch_url(sha_url, retries=retries, delay=delay)
    sha_lines = sha_bytes.decode("utf-8").strip().splitlines()
    sha_map = {}
    for line in sha_lines:
        line = line.strip()
        if line:
            parts = line.split(None, 1)
            if len(parts) == 2:
                sha_map[parts[1].strip()] = parts[0].strip()

    # 4. Verify each extension entry
    print(f"[+] Verifying {len(index_data)} extensions in index...")
    for entry in index_data:
        apk_name = entry["apk"]
        apk_url = f"{base_raw}/apk/{apk_name}"
        icon_name = entry.get("icon", "")

        print(f"  -> Verifying APK: {apk_name} ...")
        apk_bytes, apk_status = fetch_url(apk_url, retries=retries, delay=delay)
        if apk_status != 200:
            raise ValueError(f"APK {apk_name} returned HTTP {apk_status}")

        if not apk_bytes.startswith(b"PK\x03\x04"):
            raise ValueError(f"APK {apk_name} has invalid ZIP magic: {apk_bytes[:4].hex()}")

        actual_sha = hashlib.sha256(apk_bytes).hexdigest()
        expected_sha = sha_map.get(apk_name)
        if not expected_sha:
            raise ValueError(f"APK {apk_name} missing from sha256sums.txt")
        if actual_sha != expected_sha:
            raise ValueError(f"SHA-256 mismatch for {apk_name}: got {actual_sha}, expected {expected_sha}")

        # Check local file size if available
        local_apk = os.path.join(ROOT_DIR, "repo", "apk", apk_name)
        if os.path.exists(local_apk):
            local_size = os.path.getsize(local_apk)
            if len(apk_bytes) != local_size:
                raise ValueError(f"Size mismatch for {apk_name}: downloaded {len(apk_bytes)}, local {local_size}")

        # Verify icon URL if present
        if icon_name:
            icon_url = f"{base_raw}/icon/{icon_name}"
            print(f"  -> Verifying Icon: {icon_name} ...")
            icon_bytes, icon_status = fetch_url(icon_url, retries=retries, delay=delay)
            if icon_status != 200:
                raise ValueError(f"Icon {icon_name} returned HTTP {icon_status}")
            if len(icon_bytes) == 0:
                raise ValueError(f"Icon {icon_name} is empty")

    print("[+] All published Aniyomi APK repository assets successfully verified!")
    return 0

def verify_published_kototoro_repo(owner_repo, branch="repo", retries=5, delay=5):
    base_raw = f"https://raw.githubusercontent.com/{owner_repo}/{branch}/jar"
    index_url = f"{base_raw}/index.min.json"
    repo_url = f"{base_raw}/repo.json"
    sha_url = f"{base_raw}/sha256sums.txt"

    print(f"[+] Verifying published Kototoro JAR repo at {base_raw} ...")

    # 1. Fetch repo.json
    print(f"[+] Checking Kototoro repo.json: {repo_url}")
    repo_bytes, _ = fetch_url(repo_url, retries=retries, delay=delay)
    if b"kaziyami-extensions" in repo_bytes:
        raise ValueError("Kototoro repo.json contains deprecated URL substring 'kaziyami-extensions'")

    # 2. Fetch index.min.json
    print(f"[+] Checking Kototoro index.min.json: {index_url}")
    index_bytes, _ = fetch_url(index_url, retries=retries, delay=delay)
    if b"kaziyami-extensions" in index_bytes:
        raise ValueError("Kototoro index.min.json contains deprecated URL substring 'kaziyami-extensions'")
    index_data = json.loads(index_bytes.decode("utf-8"))

    # 3. Fetch sha256sums.txt
    print(f"[+] Checking Kototoro sha256sums.txt: {sha_url}")
    sha_bytes, _ = fetch_url(sha_url, retries=retries, delay=delay)
    sha_lines = sha_bytes.decode("utf-8").strip().splitlines()
    sha_map = {}
    for line in sha_lines:
        line = line.strip()
        if line:
            parts = line.split(None, 1)
            if len(parts) == 2:
                sha_map[parts[1].strip()] = parts[0].strip()

    # 4. Verify each JAR listed in sha256sums
    print(f"[+] Verifying {len(sha_map)} JARs in Kototoro repo...")
    for jar_name, expected_sha in sha_map.items():
        jar_url = f"{base_raw}/{jar_name}"
        print(f"  -> Verifying JAR: {jar_name} ...")
        jar_bytes, status = fetch_url(jar_url, retries=retries, delay=delay)
        if status != 200:
            raise ValueError(f"JAR {jar_name} returned HTTP {status}")
        if not jar_bytes.startswith(b"PK\x03\x04"):
            raise ValueError(f"JAR {jar_name} has invalid ZIP magic: {jar_bytes[:4].hex()}")

        actual_sha = hashlib.sha256(jar_bytes).hexdigest()
        if actual_sha != expected_sha:
            raise ValueError(f"SHA-256 mismatch for {jar_name}: got {actual_sha}, expected {expected_sha}")

        local_jar = os.path.join(ROOT_DIR, "repo", "jar", jar_name)
        if os.path.exists(local_jar):
            local_size = os.path.getsize(local_jar)
            if len(jar_bytes) != local_size:
                raise ValueError(f"Size mismatch for {jar_name}: downloaded {len(jar_bytes)}, local {local_size}")

    print("[+] All published Kototoro JAR repository assets successfully verified!")
    return 0

def main():
    parser = argparse.ArgumentParser(description="Verify published Kaziyami repository (Variants A and B)")
    parser.add_argument("--repo", default="kenewjr/kaziyami", help="GitHub repository (owner/repo)")
    parser.add_argument("--branch", default="repo", help="Branch serving repository assets")
    parser.add_argument("--mode", choices=["all", "aniyomi", "kototoro"], default="all", help="Repository variant to verify")
    parser.add_argument("--retries", type=int, default=5, help="Number of retry attempts")
    parser.add_argument("--delay", type=int, default=5, help="Delay between retries in seconds")
    args = parser.parse_args()

    try:
        if args.mode in ("all", "aniyomi"):
            verify_published_repo(args.repo, branch=args.branch, retries=args.retries, delay=args.delay)
        if args.mode in ("all", "kototoro"):
            verify_published_kototoro_repo(args.repo, branch=args.branch, retries=args.retries, delay=args.delay)
        return 0
    except Exception as e:
        print(f"[!] verifyPublishedRepo FAILED: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
