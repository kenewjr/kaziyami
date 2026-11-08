#!/usr/bin/env python3
"""
kototoro_self_test.py:
Faithfully replicates Kototoro's "Test All" source test harness
(app/src/main/kotlin/org/skepsun/kototoro/settings/sources/test/SourceTester.kt)
against built extension artifacts and offline mock fixtures (or live sites with --live).

Outputs identical JSON schema as emitted by Kototoro's SourceTestViewModel.exportJson().
"""

import datetime
import glob
import json
import os
import subprocess
import sys

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
import android_sdk

SOURCES_CONFIG = [
    {
        "slug": "nekopoi",
        "name": "Nekopoi",
        "lang": "id",
        "ext_class": "eu.kanade.tachiyomi.animeextension.id.nekopoi.Nekopoi",
        "fixture_dir": os.path.join(ROOT_DIR, "src", "id", "nekopoi", "src", "test", "resources", "nekopoi"),
    },
    {
        "slug": "indohentai",
        "name": "IndoHentai",
        "lang": "id",
        "ext_class": "eu.kanade.tachiyomi.animeextension.id.indohentai.IndoHentai",
        "fixture_dir": os.path.join(ROOT_DIR, "src", "id", "indohentai", "src", "test", "resources", "indohentai"),
    },
    {
        "slug": "nontonhentai",
        "name": "NontonHentai",
        "lang": "id",
        "ext_class": "eu.kanade.tachiyomi.animeextension.id.nontonhentai.NontonHentai",
        "fixture_dir": os.path.join(ROOT_DIR, "src", "id", "nontonhentai", "src", "test", "resources", "nontonhentai"),
    },
    {
        "slug": "hentaimama",
        "name": "Hentaimama",
        "lang": "en",
        "ext_class": "eu.kanade.tachiyomi.animeextension.en.hentaimama.Hentaimama",
        "fixture_dir": os.path.join(ROOT_DIR, "src", "en", "hentaimama", "src", "test", "resources", "hentaimama"),
    },
    {
        "slug": "javsubbed",
        "name": "JAVSubbed",
        "lang": "en",
        "ext_class": "eu.kanade.tachiyomi.animeextension.en.javsubbed.JAVSubbed",
        "fixture_dir": os.path.join(ROOT_DIR, "src", "en", "javsubbed", "src", "test", "resources", "javsubbed"),
    },
    {
        "slug": "hanime",
        "name": "Hanime",
        "lang": "en",
        "ext_class": "eu.kanade.tachiyomi.animeextension.en.hanime.Hanime",
        "fixture_dir": os.path.join(ROOT_DIR, "src", "en", "hanime", "src", "test", "resources", "hanime"),
    },
]

def compile_runner(runner_dir):
    os.makedirs(runner_dir, exist_ok=True)
    runner_src = os.path.join(ROOT_DIR, "tools", "KototoroTestRunner.java")
    ext_lib = os.path.join(ROOT_DIR, "libs", "extensions-lib-14.jar")
    host_dir = os.path.join(ROOT_DIR, "build", "host-libs")
    host_libs = [os.path.join(host_dir, f) for f in os.listdir(host_dir) if f.endswith(".jar")] if os.path.exists(host_dir) else []

    cp = os.pathsep.join([ext_lib] + host_libs)
    cmd = ["javac", "-cp", cp, "-d", runner_dir, runner_src]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"Failed to compile KototoroTestRunner.java:\n{res.stderr}")

def run_kototoro_self_test(is_live=False):
    runner_dir = os.path.join(ROOT_DIR, "build", "kototoro_runner")
    compile_runner(runner_dir)

    sdk_tools = android_sdk.get_sdk_tools()
    android_jar = sdk_tools["android_jar"]
    ext_lib = os.path.join(ROOT_DIR, "libs", "extensions-lib-14.jar")
    core_test_classes = os.path.join(ROOT_DIR, "core", "build", "classes", "kotlin", "test")
    host_dir = os.path.join(ROOT_DIR, "build", "host-libs")
    host_libs = [os.path.join(host_dir, f) for f in os.listdir(host_dir) if f.endswith(".jar")] if os.path.exists(host_dir) else []

    report_sources = []
    all_passed = True

    for item in SOURCES_CONFIG:
        slug = item["slug"]
        ext_class = item["ext_class"]
        fixture_dir = item["fixture_dir"]
        packaged_jar = os.path.join(ROOT_DIR, "build", "packaged-jars", f"{slug}.jar")

        if not os.path.exists(packaged_jar):
            print(f"[!] Warning: Packaged jar missing for {slug}: {packaged_jar}")
            all_passed = False
            continue

        cp_parts = [
            runner_dir,
            packaged_jar,
            core_test_classes,
            ext_lib,
            android_jar
        ] + host_libs

        cp = os.pathsep.join(cp_parts)
        cmd = ["java", "-cp", cp, "app.kaziyami.test.KototoroTestRunner", ext_class, fixture_dir]
        if is_live:
            cmd.append("--live")

        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            print(f"[-] Execution error for {slug}:\nSTDOUT: {res.stdout}\nSTDERR: {res.stderr}")
            all_passed = False
            continue

        raw_output = res.stdout.strip()
        # Find json object in stdout
        try:
            # Grab last line or json line
            json_line = None
            for line in raw_output.splitlines():
                if line.strip().startswith("{") and line.strip().endswith("}"):
                    json_line = line.strip()
            if not json_line:
                raise ValueError("No JSON object found in output")
            source_obj = json.loads(json_line)
            report_sources.append(source_obj)
            if source_obj.get("status") != "pass":
                all_passed = False
        except Exception as e:
            print(f"[-] Failed to parse JSON result for {slug}: {e}\nRaw output: {raw_output}")
            all_passed = False

    iso_timestamp = datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")
    final_report = {
        "generated": iso_timestamp,
        "sources": report_sources
    }

    report_dir = os.path.join(ROOT_DIR, "build", "reports")
    os.makedirs(report_dir, exist_ok=True)
    report_file = os.path.join(report_dir, "kototoro-test-report.json")
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(final_report, f, indent=2)

    formatted_json = json.dumps(final_report, indent=2)
    print(formatted_json)
    print(f"\n[+] Kototoro self-test report saved to: {report_file}")

    if not all_passed:
        print("[-] One or more sources failed Kototoro self-test!")
        return 1

    print("[+] All 6 sources passed Kototoro self-test (100% green)!")
    return 0

if __name__ == "__main__":
    is_live = "--live" in sys.argv
    sys.exit(run_kototoro_self_test(is_live=is_live))

