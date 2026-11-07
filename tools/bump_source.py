#!/usr/bin/env python3
"""
bump_source.py:
CLI helper to bump a single Kaziyami extension version in one step.
Usage:
  python tools/bump_source.py <source_slug>
"""

import os
import re
import sys

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def bump_source(slug: str):
    found = None
    for lang in ["id", "en"]:
        path = os.path.join(ROOT_DIR, "src", lang, slug, "build.gradle.kts")
        if os.path.exists(path):
            found = path
            break

    if not found:
        print(f"[-] Source '{slug}' not found under src/id/ or src/en/")
        sys.exit(1)

    with open(found, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Bump extVersionCode
    m_code = re.search(r'val\s+extVersionCode\s*=\s*(\d+)', content)
    if not m_code:
        print(f"[-] extVersionCode not found in {found}")
        sys.exit(1)

    current_code = int(m_code.group(1))
    new_code = current_code + 1
    content = re.sub(r'val\s+extVersionCode\s*=\s*\d+', f"val extVersionCode = {new_code}", content)

    # 2. Bump extVersion if present
    m_ver = re.search(r'val\s+extVersion\s*=\s*"([^"]+)"', content)
    if m_ver:
        ver_str = m_ver.group(1)
        parts = ver_str.split(".")
        if len(parts) >= 3:
            parts[-1] = str(int(parts[-1]) + 1)
            new_ver = ".".join(parts)
        else:
            new_ver = f"{ver_str}.{new_code}"
        content = re.sub(r'val\s+extVersion\s*=\s*"[^"]+"', f'val extVersion = "{new_ver}"', content)
        print(f"[+] Bumped {slug}: version={new_ver}, code={new_code}")
    else:
        print(f"[+] Bumped {slug}: code={new_code}")

    with open(found, "w", encoding="utf-8") as f:
        f.write(content)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python tools/bump_source.py <source_slug>")
        sys.exit(1)
    bump_source(sys.argv[1].strip().lower())

