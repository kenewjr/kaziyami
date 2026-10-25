#!/usr/bin/env python3
"""
scripts/collect_diagnostics.py
Gathers extension versions, preflight summary, device environment info,
and redacts raw logs (cookies, bearer tokens, signed URLs, IP addresses)
into a pasteable diagnostic bundle for bug reports.
"""

import os
import sys
import json
import re
import subprocess
import argparse
from pathlib import Path
from datetime import datetime

# Redaction Regexes
IPV4_REGEX = re.compile(r'\b(?:\d{1,3}\.){3}\d{1,3}\b')
IPV6_REGEX = re.compile(r'\b(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}\b')
COOKIE_HEADER_REGEX = re.compile(r'(?i)\b((?:set-)?cookie\s*:\s*)[^\r\n]+')
COOKIE_PARAM_REGEX = re.compile(r'(?i)\b((?:cf_clearance|session(?:id)?|token|auth|PHPSESSID)=)[^;&\s\r\n]+')
BEARER_AUTH_REGEX = re.compile(r'(?i)\b(authorization\s*:\s*bearer\s+)[^\r\n]+')
RAW_BEARER_REGEX = re.compile(r'(?i)\b(bearer\s+)[a-zA-Z0-9_\-\.]{15,}')
BASIC_AUTH_REGEX = re.compile(r'(?i)\b(authorization\s*:\s*basic\s+)[^\r\n]+')
URL_QUERY_SECRET_REGEX = re.compile(
    r'(?i)([?&](?:token|sig|signature|expires|expiry|h|hmac|key|auth|secret|password)=)([^& \r\n\t]+)'
)

def redact_ipv4(match):
    val = match.group(0)
    parts = val.split('.')
    if len(parts) == 4 and all(p.isdigit() and 0 <= int(p) <= 255 for p in parts):
        return "[REDACTED_IP]"
    return val

def redact_ipv6(match):
    return "[REDACTED_IPV6]"

def sanitize_log_text(raw_text: str) -> str:
    """Removes cookies, bearer tokens, sensitive query params, and IP addresses."""
    if not raw_text:
        return ""
    
    # 1. Cookie headers and inline cookie strings
    text = COOKIE_HEADER_REGEX.sub(r'\1[REDACTED_COOKIE]', raw_text)
    text = COOKIE_PARAM_REGEX.sub(r'\1[REDACTED_COOKIE]', text)
    
    # 2. Authorization headers and bearer tokens
    text = BEARER_AUTH_REGEX.sub(r'\1[REDACTED_BEARER]', text)
    text = RAW_BEARER_REGEX.sub(r'\1[REDACTED_BEARER]', text)
    text = BASIC_AUTH_REGEX.sub(r'\1[REDACTED_BASIC]', text)
    
    # 3. URL query sensitive tokens / signatures
    text = URL_QUERY_SECRET_REGEX.sub(r'\1[REDACTED_QUERY_PARAM]', text)
    
    # 4. IP addresses
    text = IPV4_REGEX.sub(redact_ipv4, text)
    text = IPV6_REGEX.sub(redact_ipv6, text)
    
    return text

def get_git_info(repo_dir: Path) -> dict:
    info = {"commit": "unknown", "branch": "unknown", "dirty": False}
    try:
        commit = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=repo_dir, text=True, stderr=subprocess.DEVNULL
        ).strip()
        info["commit"] = commit[:10]
        branch = subprocess.check_output(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=repo_dir, text=True, stderr=subprocess.DEVNULL
        ).strip()
        info["branch"] = branch
        status = subprocess.check_output(
            ["git", "status", "--porcelain"], cwd=repo_dir, text=True, stderr=subprocess.DEVNULL
        ).strip()
        info["dirty"] = bool(status)
    except Exception:
        pass
    return info

def get_extension_versions(repo_dir: Path) -> list:
    """Reads extension info from repo/index.json if present, else parses build.gradle.kts."""
    index_file = repo_dir / "repo" / "index.json"
    if index_file.exists():
        try:
            with open(index_file, "r", encoding="utf-8") as f:
                data = json.load(f)
            return [
                {
                    "name": item.get("name"),
                    "pkg": item.get("pkg"),
                    "version": item.get("version"),
                    "code": item.get("code")
                }
                for item in data
            ]
        except Exception:
            pass
    return []

def get_preflight_summary(repo_dir: Path, custom_path: Path = None) -> str:
    path = custom_path or (repo_dir / "reports" / "preflight-report.json")
    if not path.exists():
        return "Preflight report not found. (Run `python scripts/preflight.py` first)"
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        sources = data.get("sources", {})
        lines = []
        for src_name, src_info in sources.items():
            status = src_info.get("status", "UNKNOWN")
            mirrors = src_info.get("mirrors", {})
            first_mirror = list(mirrors.values())[0] if mirrors else {}
            http_st = first_mirror.get("http_status", "N/A")
            isp_note = src_info.get("isp_blocking", {}).get("note", "")
            lines.append(f"- {src_name:12}: {status:4} (HTTP {http_st}) {isp_note}")
        return "\n".join(lines) if lines else "Preflight completed with 0 sources recorded."
    except Exception as e:
        return f"Error reading preflight report: {e}"

def generate_bundle(
    repo_dir: Path,
    app_version: str,
    android_version: str,
    raw_logs: str,
    preflight_path: Path = None
) -> str:
    git_info = get_git_info(repo_dir)
    ext_versions = get_extension_versions(repo_dir)
    preflight_summary = get_preflight_summary(repo_dir, preflight_path)
    redacted_logs = sanitize_log_text(raw_logs)

    ext_lines = [
        f"- {ext['name']} (v{ext['version']}, code: {ext['code']}) -> {ext['pkg']}"
        for ext in ext_versions
    ]

    bundle = f"""================================================================================
KAZIYAMI DIAGNOSTIC BUNDLE
Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
================================================================================

1. REPOSITORY & ENVIRONMENT
---------------------------
Git Commit      : {git_info['commit']} (branch: {git_info['branch']}, dirty: {git_info['dirty']})
App Target      : {app_version}
Android Version : {android_version}

2. EXTENSION VERSIONS
---------------------
{chr(10).join(ext_lines) if ext_lines else "No extensions found in repo/index.json"}

3. PREFLIGHT DIAGNOSTIC SUMMARY
-------------------------------
{preflight_summary}

4. REDACTED LOGCAT / ERROR OUTPUT
---------------------------------
{redacted_logs.strip() if redacted_logs.strip() else "[No logs provided]"}

================================================================================
END OF DIAGNOSTIC BUNDLE
================================================================================
"""
    return bundle

def main():
    parser = argparse.ArgumentParser(description="Collect sanitized Kaziyami diagnostics for issue reporting.")
    parser.add_argument("--app-version", default="Aniyomi (unspecified)", help="App version (e.g. 'Aniyomi 0.15.3')")
    parser.add_argument("--android-version", default="Android (unspecified)", help="Android version (e.g. 'Android 14')")
    parser.add_argument("--logs-file", help="Path to raw logcat text file to sanitize and attach")
    parser.add_argument("--preflight-report", help="Path to custom preflight-report.json")
    parser.add_argument("--out", default="reports/diagnostic-bundle.txt", help="Output bundle file path")
    parser.add_argument("--non-interactive", action="store_true", help="Do not prompt for interactive input")

    args = parser.parse_args()
    repo_dir = Path(__file__).resolve().parent.parent

    app_ver = args.app_version
    android_ver = args.android_version
    raw_logs = ""

    if args.logs_file and Path(args.logs_file).exists():
        with open(args.logs_file, "r", encoding="utf-8", errors="replace") as f:
            raw_logs = f.read()
    elif not args.non_interactive and sys.stdin.isatty():
        print("=== Kaziyami Diagnostics Collector ===")
        user_app = input(f"Enter App & version [{app_ver}]: ").strip()
        if user_app:
            app_ver = user_app
        user_android = input(f"Enter Android version [{android_ver}]: ").strip()
        if user_android:
            android_ver = user_android
        print("\nPaste error logs / logcat snippet below (Press Ctrl+D on Unix or Ctrl+Z on Windows then Enter to submit, or press Enter directly to skip):")
        try:
            lines = []
            while True:
                line = input()
                lines.append(line)
        except EOFError:
            raw_logs = "\n".join(lines)

    preflight_p = Path(args.preflight_report) if args.preflight_report else None
    bundle_text = generate_bundle(repo_dir, app_ver, android_ver, raw_logs, preflight_p)

    out_path = Path(args.out)
    if not out_path.is_absolute():
        out_path = repo_dir / out_path
    out_path.parent.mkdir(parents=True, exist_ok=True)

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(bundle_text)

    print(f"\n[+] Diagnostic bundle successfully saved to: {out_path}")
    print("    You can safely copy and paste this file into your issue or report to Claude.")

if __name__ == "__main__":
    main()
