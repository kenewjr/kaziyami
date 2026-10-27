#!/usr/bin/env python3
"""
tools/check_test_counts.py
Parses JUnit XML test results from all modules, verifies per-module test execution,
enforces non-zero test counts for all 6 sources and core, and outputs summary to
GitHub Step Summary and console.
"""

import os
import sys
import xml.etree.ElementTree as ET

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

REQUIRED_MODULES = [
    ("core", "core"),
    ("lib-multisrc:generic", "lib-multisrc/generic"),
    ("src:en:hanime", "src/en/hanime"),
    ("src:en:hentaimama", "src/en/hentaimama"),
    ("src:en:javsubbed", "src/en/javsubbed"),
    ("src:id:indohentai", "src/id/indohentai"),
    ("src:id:nekopoi", "src/id/nekopoi"),
    ("src:id:nontonhentai", "src/id/nontonhentai"),
]

def scan_module_results(rel_dir: str):
    results_dir = os.path.join(ROOT_DIR, rel_dir, "build", "test-results", "test")
    if not os.path.exists(results_dir):
        return {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}

    total_tests = 0
    total_failures = 0
    total_errors = 0
    total_skipped = 0

    for fname in os.listdir(results_dir):
        if fname.startswith("TEST-") and fname.endswith(".xml"):
            fpath = os.path.join(results_dir, fname)
            try:
                tree = ET.parse(fpath)
                root = tree.getroot()
                total_tests += int(root.attrib.get("tests", 0))
                total_failures += int(root.attrib.get("failures", 0))
                total_errors += int(root.attrib.get("errors", 0))
                total_skipped += int(root.attrib.get("skipped", 0))
            except Exception as e:
                print(f"[-] Error reading {fname}: {e}", file=sys.stderr)

    return {
        "tests": total_tests,
        "failures": total_failures,
        "errors": total_errors,
        "skipped": total_skipped
    }

def main():
    table_rows = []
    failed_requirements = []
    grand_total_tests = 0
    grand_total_failures = 0

    print("=== Kaziyami Test Suite Execution Audit ===")
    for mod_name, mod_dir in REQUIRED_MODULES:
        res = scan_module_results(mod_dir)
        tests = res["tests"]
        failures = res["failures"] + res["errors"]
        status = "PASS" if tests > 0 and failures == 0 else "FAIL"

        grand_total_tests += tests
        grand_total_failures += failures

        if tests == 0:
            failed_requirements.append(f"Module '{mod_name}' has 0 executed tests (must execute fixture/unit tests)")
        elif failures > 0:
            failed_requirements.append(f"Module '{mod_name}' has {failures} test failures")

        table_rows.append((mod_name, tests, failures, res["skipped"], status))
        print(f"  [{status}] {mod_name:<25}: tests={tests:>3}, failures={failures:>2}, skipped={res['skipped']:>2}")

    print("-------------------------------------------------------------")
    print(f"  TOTAL: tests={grand_total_tests}, failures={grand_total_failures}")

    # Generate Markdown Summary
    md = "## 🧪 Kaziyami Test Execution Summary\n\n"
    md += "| Module | Tests Run | Failures | Skipped | Status |\n"
    md += "| :--- | :---: | :---: | :---: | :---: |\n"
    for mod, tests, fails, skipped, status in table_rows:
        icon = "✅" if status == "PASS" else "❌"
        md += f"| `{mod}` | {tests} | {fails} | {skipped} | {icon} {status} |\n"
    md += f"| **Total** | **{grand_total_tests}** | **{grand_total_failures}** | - | **{'✅ PASS' if grand_total_failures == 0 and not failed_requirements else '❌ FAIL'}** |\n\n"

    step_summary_path = os.getenv("GITHUB_STEP_SUMMARY")
    if step_summary_path:
        try:
            with open(step_summary_path, "a", encoding="utf-8") as f:
                f.write(md)
        except Exception as e:
            print(f"[-] Could not write to GITHUB_STEP_SUMMARY: {e}", file=sys.stderr)

    if failed_requirements:
        print("\n[-] TEST COUNT / INTEGRITY GATE FAILED:")
        for err in failed_requirements:
            print(f"    - {err}")
        return 1

    print("\n[+] All required modules executed tests successfully.")
    return 0

if __name__ == "__main__":
    sys.exit(main())

