#!/usr/bin/env python3
"""
verify_runtime_compat.py:
Validates that Gradle dependencies across all modules conform strictly
to the host runtime compatibility matrix defined in docs/STACK.md.
Fails CI if breaking major versions (Kotlin 2+, OkHttp 5+, JUnit 6+) are declared.
"""
import os
import re
import sys

# Semver constraints aligned with docs/STACK.md
FORBIDDEN_PATTERNS = [
    (re.compile(r'org\.jetbrains\.kotlin:kotlin-[a-z-]+:([2-9]\.[0-9]+(?:\.[0-9]+)?)'), "Kotlin 2+ is blocked (host runtime is Kotlin 1.9.x)"),
    (re.compile(r'com\.squareup\.okhttp3:(?:okhttp|mockwebserver):([5-9]\.[0-9]+(?:\.[0-9]+)?.*)'), "OkHttp 5+ is blocked (host runtime provides OkHttp 4.12.x)"),
    (re.compile(r'org\.junit\.jupiter:junit-jupiter:([6-9]\.[0-9]+(?:\.[0-9]+)?)'), "JUnit 6+ is invalid (use JUnit 5.x)"),
    (re.compile(r'kotlin\("jvm"\)\s+version\s+["\']([2-9]\.[0-9]+(?:\.[0-9]+)?)["\']'), "Kotlin JVM plugin 2+ is blocked"),
]

ALLOWED_HOST_RANGES = {
    "okhttp": ("4.", "Must be OkHttp 4.x"),
    "kotlin": ("1.8.", "1.9."),
}

def scan_file(file_path):
    violations = []
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()
        for pattern, reason in FORBIDDEN_PATTERNS:
            match = pattern.search(content)
            if match:
                violations.append(f"{match.group(0)} -> {reason}")
    return violations

def main():
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    all_violations = {}

    for dirpath, _, filenames in os.walk(root_dir):
        # Skip build dirs and git
        if any(ignored in dirpath for ignored in [".git", "build", ".gradle"]):
            continue
        for fname in filenames:
            if fname.endswith("build.gradle.kts") or fname == "gradle.properties":
                full_path = os.path.join(dirpath, fname)
                rel_path = os.path.relpath(full_path, root_dir)
                v = scan_file(full_path)
                if v:
                    all_violations[rel_path] = v

    if all_violations:
        print("[-] Runtime compatibility check FAILED!")
        print("    Dependencies violated the host runtime matrix in docs/STACK.md:")
        for path, issues in all_violations.items():
            print(f"    In {path}:")
            for issue in issues:
                print(f"      - {issue}")
        return 1

    print("[+] Runtime compatibility check PASSED: All dependencies match host runtime matrix.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
