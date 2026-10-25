#!/usr/bin/env python3
"""
scripts/install_debug.py
Installs debug APKs to connected Android devices via ADB, or provides manual install instructions.
"""
import argparse
import os
import subprocess
import sys

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEBUG_DIR = os.path.join(ROOT_DIR, "build", "kaziyami-debug")

def check_adb():
    try:
        proc = subprocess.run(["adb", "devices"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        lines = [line.strip() for line in proc.stdout.splitlines() if line.strip() and not line.startswith("List of devices")]
        devices = [line.split()[0] for line in lines if "device" in line]
        return devices
    except Exception:
        return None

def install_apks(source_filter=None, mock_adb=False):
    if not os.path.exists(DEBUG_DIR):
        print(f"[-] Debug APK directory not found: {DEBUG_DIR}")
        print("[*] Run './gradlew assembleDebugAll' first to assemble debug APKs.")
        return False

    apk_files = [f for f in os.listdir(DEBUG_DIR) if f.endswith(".apk")]
    if not apk_files:
        print(f"[-] No APKs found in {DEBUG_DIR}. Run './gradlew assembleDebugAll' first.")
        return False

    if source_filter:
        apk_files = [f for f in apk_files if source_filter.lower() in f.lower()]
        if not apk_files:
            print(f"[-] No APK matched filter '{source_filter}'. Available: {[f for f in os.listdir(DEBUG_DIR) if f.endswith('.apk')]}")
            return False

    if mock_adb:
        print(f"[+] (Mock ADB) Found 1 mock device. Installing {len(apk_files)} APKs:")
        for apk in apk_files:
            print(f"    adb install -r {os.path.join(DEBUG_DIR, apk)} -> Success")
        return True

    devices = check_adb()
    if devices is None:
        print("[-] ADB tool not found in PATH.")
        print("\n" + "="*70)
        print("MANUAL INSTALLATION INSTRUCTIONS:")
        print(f"1. Connect phone via USB or copy APK files from: {DEBUG_DIR}")
        print("2. Copy all .apk files to your phone's 'Download' folder.")
        print("3. Open Android File Manager on your phone and tap each APK to install.")
        print("4. Allow 'Install from unknown sources' if prompted.")
        print("="*70 + "\n")
        return False

    if not devices:
        print("[-] No Android devices connected via ADB.")
        print("    Enable USB Debugging on your phone and authorize this computer.")
        return False

    print(f"[+] Found {len(devices)} connected device(s): {devices}")
    success_count = 0

    for apk in apk_files:
        apk_path = os.path.join(DEBUG_DIR, apk)
        print(f"[*] Installing {apk} ...")
        cmd = ["adb", "install", "-r", apk_path]
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if proc.returncode == 0 and "Success" in proc.stdout:
            print(f"    -> [SUCCESS] {apk} installed.")
            success_count += 1
        else:
            print(f"    -> [FAILED] {proc.stderr.strip() or proc.stdout.strip()}")

    print(f"\n[+] Installed {success_count}/{len(apk_files)} APKs successfully.")
    return success_count == len(apk_files)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Install Kaziyami debug APKs to Android device.")
    parser.add_argument("--source", help="Filter by source name (e.g. hentaimama, nekopoi)")
    parser.add_argument("--mock", action="store_true", help="Run with mock ADB for verification")

    args = parser.parse_args()
    success = install_apks(source_filter=args.source, mock_adb=args.mock)
    if not success and not args.mock:
        sys.exit(1)
