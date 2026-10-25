#!/usr/bin/env python3
"""
bump_changed.py:
Finds extensions affected by git diff against base tag/branch, verifies that their
extVersionCode was bumped, or automatically bumps them.
"""

import argparse
import os
import re
import subprocess
import sys

EXTENSIONS = {
    "src/id/nekopoi": ["core", "lib-multisrc/generic", "src/id/nekopoi"],
    "src/id/indohentai": ["core", "lib-multisrc/generic", "src/id/indohentai"],
    "src/id/nontonhentai": ["core", "lib-multisrc/animestream", "lib-multisrc/generic", "src/id/nontonhentai"],
    "src/en/hentaimama": ["core", "lib-multisrc/dooplay", "lib-multisrc/generic", "src/en/hentaimama"],
    "src/en/javsubbed": ["core", "lib-multisrc/retrotube", "lib-multisrc/generic", "src/en/javsubbed"],
    "src/en/hanime": ["core", "lib-multisrc/generic", "src/en/hanime"],
}

def get_affected_extensions(changed_files):
    affected = set()
    for ext_path, deps in EXTENSIONS.items():
        for f in changed_files:
            # Normalize path
            norm_f = f.replace("\\", "/")
            for dep in deps:
                if norm_f.startswith(dep + "/") or norm_f == dep:
                    affected.add(ext_path)
                    break
    return sorted(list(affected))

def get_base_ref():
    # Try finding last tag, else origin/main, origin/master, or HEAD~1
    try:
        tag = subprocess.check_output(
            ["git", "describe", "--tags", "--abbrev=0"],
            stderr=subprocess.DEVNULL,
            text=True
        ).strip()
        if tag:
            return tag
    except Exception:
        pass

    for candidate in ["origin/main", "origin/master", "main", "master", "HEAD~1"]:
        try:
            subprocess.check_output(
                ["git", "rev-parse", "--verify", candidate],
                stderr=subprocess.DEVNULL,
                text=True
            )
            return candidate
        except Exception:
            continue
    return "HEAD"

def get_changed_files(base_ref):
    try:
        out = subprocess.check_output(
            ["git", "diff", "--name-only", f"{base_ref}...HEAD"],
            stderr=subprocess.DEVNULL,
            text=True
        )
        return [line.strip() for line in out.strip().splitlines() if line.strip()]
    except Exception:
        # Fallback to diff against HEAD~1 or working directory
        try:
            out = subprocess.check_output(["git", "diff", "--name-only", "HEAD"], text=True)
            return [line.strip() for line in out.strip().splitlines() if line.strip()]
        except Exception:
            return []

def get_version_code_at_ref(file_path, ref):
    norm_path = file_path.replace("\\", "/")
    try:
        content = subprocess.check_output(
            ["git", "show", f"{ref}:{norm_path}"],
            stderr=subprocess.DEVNULL,
            text=True
        )
        m = re.search(r'extVersionCode\s*=\s*(\d+)', content)
        return int(m.group(1)) if m else None
    except Exception:
        return None

def get_current_version_code(build_file):
    if not os.path.exists(build_file):
        return None
    with open(build_file, "r", encoding="utf-8") as f:
        content = f.read()
    m = re.search(r'extVersionCode\s*=\s*(\d+)', content)
    return int(m.group(1)) if m else None

def bump_extension(build_file):
    with open(build_file, "r", encoding="utf-8") as f:
        content = f.read()
    m = re.search(r'(extVersionCode\s*=\s*)(\d+)', content)
    if not m:
        raise ValueError(f"extVersionCode not found in {build_file}")
    current = int(m.group(2))
    new_val = current + 1
    new_content = re.sub(
        r'extVersionCode\s*=\s*\d+',
        f"extVersionCode = {new_val}",
        content
    )
    with open(build_file, "w", encoding="utf-8") as f:
        f.write(new_content)
    print(f"[+] Bumped {build_file}: {current} -> {new_val}")
    return new_val

def main():
    parser = argparse.ArgumentParser(description="Kaziyami extension version bump manager")
    parser.add_argument("--base", default=None, help="Base git ref to compare against")
    parser.add_argument("--bump", action="store_true", help="Bump affected extensions")
    parser.add_argument("--check", action="store_true", help="Verify that affected extensions were bumped")
    args = parser.parse_args()

    base_ref = args.base or get_base_ref()
    print(f"[+] Comparing against base ref: {base_ref}")

    changed_files = get_changed_files(base_ref)
    affected = get_affected_extensions(changed_files)

    print(f"[+] Found {len(changed_files)} changed files.")
    print(f"[+] Affected extensions ({len(affected)}): {affected}")

    if not affected:
        print("[+] No extensions affected.")
        return 0

    unbumped = []
    for ext in affected:
        build_file = os.path.join(ext, "build.gradle.kts")
        cur_code = get_current_version_code(build_file)
        base_code = get_version_code_at_ref(build_file, base_ref)

        print(f"    - {ext}: baseCode={base_code}, curCode={cur_code}")

        if args.bump:
            bump_extension(build_file)
        elif args.check:
            # If base_code is available, cur_code must be greater
            if base_code is not None and cur_code is not None and cur_code <= base_code:
                unbumped.append((ext, base_code, cur_code))

    if args.check and unbumped:
        print("\n[!] ERROR: The following extensions have code changes but their extVersionCode was not bumped:")
        for ext, base_code, cur_code in unbumped:
            print(f"    - {ext} (current: {cur_code}, base: {base_code})")
        print("[!] Please increment extVersionCode in their build.gradle.kts or run './gradlew bumpChanged'")
        return 1

    return 0

if __name__ == "__main__":
    sys.exit(main())
