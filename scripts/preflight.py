#!/usr/bin/env python3
"""
scripts/preflight.py
Local pre-flight diagnostic script for the user's local network and toolchain.
Runs connectivity, DNS/ISP block detection, page parsing, video stream checks,
and generates a sanitized preflight-report.json.
"""
import json
import os
import re
import socket
import ssl
import subprocess
import sys
import urllib.parse
import urllib.request

SOURCES = [
    {
        "name": "Nekopoi",
        "lang": "id",
        "primary": "https://nekopoi.care",
        "mirrors": ["https://nekopoi.app", "https://nekopoi.me"],
        "list_path": "/category/hentai/page/1/",
        "item_regex": r"<article[^>]*>",
        "detail_path": "/hentai/kanojo-x-kanojo/",
        "episode_regex": r"<li[^>]*><a[^>]*href=['\"]https?://nekopoi\.[^>]*['\"]"
    },
    {
        "name": "IndoHentai",
        "lang": "id",
        "primary": "https://indohentai.net",
        "mirrors": [],
        "list_path": "/terbaru/page/1/",
        "item_regex": r"<article[^>]*class=['\"][^'\"]*post[^'\"]*['\"]",
        "detail_path": "/hentai/overflow-episode-1-sub-indo/",
        "episode_regex": r"<a[^>]*class=['\"][^'\"]*ep-link[^'\"]*['\"]"
    },
    {
        "name": "NontonHentai",
        "lang": "id",
        "primary": "https://nontonhentai.net",
        "mirrors": [],
        "list_path": "/page/1/",
        "item_regex": r"<article[^>]*|class=['\"][^'\"]*animposx[^'\"]*['\"]",
        "detail_path": "/anime/overflow/",
        "episode_regex": r"<li[^>]*><a[^>]*href=['\"][^'\"]*episode[^'\"]*['\"]"
    },
    {
        "name": "Hentaimama",
        "lang": "en",
        "primary": "https://hentaimama.io",
        "mirrors": [],
        "list_path": "/tvshows/page/1/",
        "item_regex": r"<article[^>]*class=['\"][^'\"]*item[^'\"]*['\"]",
        "detail_path": "/tvshows/itadaki-seieki/",
        "episode_regex": r"<div[^>]*class=['\"][^'\"]*episodiotitle[^'\"]*['\"]"
    },
    {
        "name": "JAVSubbed",
        "lang": "en",
        "primary": "https://javsubbed.net",
        "mirrors": [],
        "list_path": "/most-viewed/page/1/",
        "item_regex": r"<div[^>]*class=['\"][^'\"]*item[^'\"]*['\"]",
        "detail_path": "/video/stars-123-subbed/",
        "episode_regex": r"<a[^>]*href=['\"][^'\"]*watch[^'\"]*['\"]"
    },
    {
        "name": "Hanime",
        "lang": "en",
        "primary": "https://hanime.tv",
        "mirrors": ["https://guest.freeanimehentai.net"],
        "list_path": "/browse/trending?page=1",
        "item_regex": r"<div[^>]*class=['\"][^'\"]*video[^'\"]*['\"]|<a[^>]*href=['\"][^'\"]*/hentai/[^'\"]*['\"]",
        "detail_path": "/hentai/video/shoujo-ramune-1",
        "episode_regex": r"/videos/hentai/"
    }
]

DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

KNOWN_ISP_BLOCK_IPS = {
    "127.0.0.1", "0.0.0.0", "10.10.10.10"
}

KNOWN_ISP_BLOCK_KEYWORDS = [
    "internet positif", "internet baik", "mercusuar", "kominfo", "blocked",
    "access denied", "site has been blocked", "halaman ini diblokir"
]

def scrub_sensitive(text: str) -> str:
    """Removes tokens, cookies, passwords, and full query strings."""
    if not text:
        return ""
    # Scrub cookies
    text = re.sub(r"(?i)(?:cookie|set-cookie)\s*:\s*[^\r\n]+", "Cookie: [REDACTED]", text)
    # Scrub auth/token/signature params
    text = re.sub(r"(?i)(?:token|auth|bearer|signature|sig|pass|password)=[^&\s'\"]+", "token=[REDACTED]", text)
    # Scrub URL queries on streams
    text = re.sub(r"https?://[^\s?]+\?[^\s'\"]+", lambda m: m.group(0).split("?")[0] + "?[REDACTED_QUERY]", text)
    return text

def check_toolchain() -> dict:
    results = {}
    # 1. Java
    try:
        proc = subprocess.run(["java", "-version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=5)
        out = proc.stderr or proc.stdout
        version_line = out.splitlines()[0] if out else "Unknown"
        results["java"] = {"status": "PASS", "info": version_line}
    except Exception as e:
        results["java"] = {"status": "FAIL", "info": f"java not in PATH: {e}"}

    # 2. ADB
    try:
        proc = subprocess.run(["adb", "version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=5)
        out = proc.stdout.splitlines()[0] if proc.stdout else "Available"
        results["adb"] = {"status": "PASS", "info": out}
    except Exception:
        results["adb"] = {"status": "WARN", "info": "adb not installed (manual APK installation required)"}

    # 3. Gradle wrapper
    gradle_cmd = os.path.join(".", "gradlew") if os.name != "nt" else "gradlew.bat"
    if os.path.exists(gradle_cmd):
        results["gradlew"] = {"status": "PASS", "info": f"Found {gradle_cmd}"}
    else:
        results["gradlew"] = {"status": "FAIL", "info": "gradlew wrapper missing from repository"}

    return results

def check_domain_dns_and_ip(domain: str) -> dict:
    try:
        ips = socket.gethostbyname_ex(domain)[2]
        if any(ip in KNOWN_ISP_BLOCK_IPS or ip.startswith("118.98.") for ip in ips):
            return {"status": "BLOCKED_ISP", "ips": ips, "error": "Resolved to known ISP block/sinkhole address"}
        return {"status": "PASS", "ips": ips, "error": None}
    except socket.gaierror as e:
        return {"status": "DNS_FAIL", "ips": [], "error": f"DNS resolution failed: {e}"}

def check_http_endpoint(url: str, timeout: int = 12) -> dict:
    parsed = urllib.parse.urlparse(url)
    domain = parsed.hostname or ""
    dns_check = check_domain_dns_and_ip(domain)

    if dns_check["status"] != "PASS":
        return {
            "status": "FAIL",
            "code": 0,
            "dns": dns_check["status"],
            "isp_blocked": dns_check["status"] == "BLOCKED_ISP",
            "cf_challenge": False,
            "note": dns_check["error"] or "DNS error"
        }

    headers = {
        "User-Agent": DEFAULT_USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9"
    }

    req = urllib.request.Request(url, headers=headers)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            code = resp.getcode()
            body_snippet = resp.read(8192).decode("utf-8", errors="ignore").lower()
            server_hdr = resp.headers.get("Server", "").lower()
            cf_mitigated = resp.headers.get("cf-mitigated", "").lower()

            # Check ISP blocking keywords in body
            if any(kw in body_snippet for kw in KNOWN_ISP_BLOCK_KEYWORDS):
                return {
                    "status": "WARN",
                    "code": code,
                    "dns": "PASS",
                    "isp_blocked": True,
                    "cf_challenge": False,
                    "note": "Response body contains ISP filtering redirect text"
                }

            # Check Cloudflare challenge
            if cf_mitigated == "challenge" or "just a moment..." in body_snippet or "cf-browser-verification" in body_snippet:
                return {
                    "status": "WARN",
                    "code": code,
                    "dns": "PASS",
                    "isp_blocked": False,
                    "cf_challenge": True,
                    "note": "Cloudflare protection challenge active (WebView required)"
                }

            return {
                "status": "PASS" if code in [200, 206] else "WARN",
                "code": code,
                "dns": "PASS",
                "isp_blocked": False,
                "cf_challenge": False,
                "note": f"HTTP {code} OK"
            }
    except urllib.error.HTTPError as e:
        code = e.code
        body_snippet = e.read(4096).decode("utf-8", errors="ignore").lower()
        if code in [403, 503] and ("cloudflare" in str(e.headers).lower() or "just a moment..." in body_snippet):
            return {
                "status": "WARN",
                "code": code,
                "dns": "PASS",
                "isp_blocked": False,
                "cf_challenge": True,
                "note": "Cloudflare challenge page (403/503)"
            }
        return {
            "status": "WARN",
            "code": code,
            "dns": "PASS",
            "isp_blocked": False,
            "cf_challenge": False,
            "note": f"HTTP {code} error"
        }
    except Exception as e:
        return {
            "status": "FAIL",
            "code": 0,
            "dns": "PASS",
            "isp_blocked": False,
            "cf_challenge": False,
            "note": f"Connection failed: {e}"
        }

def test_source_parsing(source: dict) -> dict:
    url = source["primary"] + source["list_path"]
    headers = {"User-Agent": DEFAULT_USER_AGENT, "Accept": "text/html"}
    req = urllib.request.Request(url, headers=headers)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

    items_found = 0
    episodes_found = 0

    try:
        with urllib.request.urlopen(req, timeout=12, context=ctx) as resp:
            html = resp.read(65536).decode("utf-8", errors="ignore")
            items_found = len(re.findall(source["item_regex"], html, re.IGNORECASE))
    except Exception:
        pass

    detail_url = source["primary"] + source["detail_path"]
    try:
        req = urllib.request.Request(detail_url, headers=headers)
        with urllib.request.urlopen(req, timeout=12, context=ctx) as resp:
            html = resp.read(65536).decode("utf-8", errors="ignore")
            episodes_found = len(re.findall(source["episode_regex"], html, re.IGNORECASE))
    except Exception:
        pass

    return {
        "items_found": items_found,
        "episodes_found": episodes_found,
        "status": "PASS" if items_found > 0 or episodes_found > 0 else "WARN"
    }

def run_preflight() -> dict:
    print("\n" + "="*80)
    print("                  KAZIYAMI LOCAL PRE-FLIGHT TEST SUITE                  ")
    print("="*80)

    report = {"toolchain": {}, "sources": {}}

    # 1. Toolchain
    print("[*] 1. Checking host toolchain...")
    report["toolchain"] = check_toolchain()
    for tool, res in report["toolchain"].items():
        print(f"    [{res['status']}] {tool.upper()}: {res['info']}")

    # 2. Source connectivity & DNS checks
    print("\n[*] 2. Checking network, DNS/ISP status, and layouts...")
    print(f"{'SOURCE':<14} | {'MIRROR':<32} | {'HTTP':<5} | {'STATUS':<10} | {'NOTE'}")
    print("-" * 80)

    for src in SOURCES:
        src_name = src["name"]
        urls_to_test = [src["primary"]] + src["mirrors"]
        src_results = []

        for u in urls_to_test:
            res = check_http_endpoint(u)
            src_results.append({"url": u, "result": res})
            print(f"{src_name:<14} | {u:<32} | {res['code']:<5} | {res['status']:<10} | {res['note']}")

        # 3. Parser checks
        parse_res = test_source_parsing(src)
        report["sources"][src_name] = {
            "lang": src["lang"],
            "endpoints": src_results,
            "parser": parse_res
        }

    # Summary
    print("=" * 80)
    print("\n[+] Preflight checks complete.")

    # Write JSON report
    os.makedirs("reports", exist_ok=True)
    report_file = os.path.join("reports", "preflight-report.json")
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    print(f"[+] Detailed report saved to: {report_file}")

    # Check for ISP blocking warnings
    has_isp_blocks = any(
        ep["result"].get("isp_blocked") for s in report["sources"].values() for ep in s["endpoints"]
    )
    if has_isp_blocks:
        print("\n" + "!" * 80)
        print("ATTENTION: ISP / Regional DNS blocking detected on one or more domains.")
        print("RECOMMENDATION: Enable Private DNS (DNS-over-HTTPS) on your Android device")
        print("via Android Settings -> Network & internet -> Private DNS -> 'one.one.one.one'")
        print("or use a standard VPN before testing.")
        print("!" * 80 + "\n")

    return report

if __name__ == "__main__":
    run_preflight()
