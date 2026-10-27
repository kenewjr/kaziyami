#!/usr/bin/env python3
"""
tools/scan_secrets.py
Scans repository for accidentally committed private keys, tokens, and keystores.
"""
import os
import re
import subprocess
import sys

SUSPICIOUS_PATTERNS = [
    (r"-----BEGIN\s+(?:RSA\s+)?PRIVATE\s+KEY-----", "PEM Private Key"),
    (r"ghp_[a-zA-Z0-9]{36}", "GitHub Personal Access Token"),
    (r"gho_[a-zA-Z0-9]{36}", "GitHub OAuth Token"),
    (r"xox[baprs]-[0-9a-zA-Z]{10,48}", "Slack Token"),
    (r"AKIA[0-9A-Z]{16}", "AWS Access Key"),
    (r"AIza[0-9A-Za-z\\-_]{35}", "Google API Key"),
    (r"password\s*=\s*['\"][^'\"]{8,}['\"]", "Hardcoded Password")
]

IGNORED_DIRS = {".git", ".gradle", "build", "node_modules", ".idea", ".vscode", "__pycache__"}
IGNORED_FILES = {"scan_secrets.py", "test.keystore", "test.jks"}

def scan():
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    violations = []

    for dirpath, dirnames, filenames in os.walk(root_dir):
        # Exclude ignored directories
        dirnames[:] = [d for d in dirnames if d not in IGNORED_DIRS]

        for fname in filenames:
            if fname in IGNORED_FILES:
                continue
            if fname.endswith((".keystore", ".jks", ".p12", ".key")):
                # Check if ignored by git
                rel_path = os.path.relpath(os.path.join(dirpath, fname), root_dir)
                is_ignored = subprocess.run(["git", "check-ignore", "-q", rel_path], cwd=root_dir).returncode == 0
                if not rel_path.startswith("build") and not is_ignored:
                    violations.append((rel_path, "Forbidden binary key/keystore file"))

            full_path = os.path.join(dirpath, fname)
            try:
                with open(full_path, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                    for pattern, desc in SUSPICIOUS_PATTERNS:
                        match = re.search(pattern, content)
                        if match and "test_keystore" not in full_path and "mock" not in full_path.lower():
                            rel_path = os.path.relpath(full_path, root_dir)
                            violations.append((rel_path, f"{desc}: {match.group(0)[:12]}..."))
            except Exception:
                pass

    if violations:
        print("[-] Secret scanner found potential security leaks:")
        for path, desc in violations:
            print(f"    {path}: {desc}")
        sys.exit(1)
    else:
        print("[+] Secret scan complete: clean, zero leaks found.")
        sys.exit(0)

if __name__ == "__main__":
    scan()
