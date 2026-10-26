#!/usr/bin/env python3
"""
android_sdk.py:
Helper to discover, bootstrap, and invoke Android SDK build tools (aapt2, d8, zipalign, apksigner, android.jar)
across Windows, Linux (including GitHub Actions), and macOS runners.
"""

import os
import platform
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCAL_SDK_DIR = os.path.join(ROOT_DIR, "build", "android-sdk")

BUILD_TOOLS_URLS = {
    "Windows": "https://dl.google.com/android/repository/build-tools_r33.0.2-windows.zip",
    "Linux": "https://dl.google.com/android/repository/build-tools_r33.0.2-linux.zip",
    "Darwin": "https://dl.google.com/android/repository/build-tools_r33.0.2-macosx.zip"
}
ANDROID_JAR_URL = "https://raw.githubusercontent.com/Sable/android-platforms/master/android-33/android.jar"

def _find_in_sdk(sdk_root):
    if not sdk_root or not os.path.exists(sdk_root):
        return None
    bt_dir = os.path.join(sdk_root, "build-tools")
    if not os.path.exists(bt_dir):
        return None
    versions = sorted(os.listdir(bt_dir), reverse=True)
    for v in versions:
        candidate = os.path.join(bt_dir, v)
        if os.path.isdir(candidate):
            return candidate
    return None

def bootstrap_sdk():
    """Ensures aapt2, d8.jar, apksigner.jar, zipalign, and android.jar are available."""
    os.makedirs(LOCAL_SDK_DIR, exist_ok=True)
    bt_dir = os.path.join(LOCAL_SDK_DIR, "build-tools")
    plat_jar = os.path.join(LOCAL_SDK_DIR, "platforms", "android-33", "android.jar")

    is_win = platform.system() == "Windows"
    aapt2_name = "aapt2.exe" if is_win else "aapt2"
    aapt2_path = os.path.join(bt_dir, aapt2_name)

    if not os.path.exists(aapt2_path):
        os_name = platform.system()
        url = BUILD_TOOLS_URLS.get(os_name, BUILD_TOOLS_URLS["Linux"])
        zip_path = os.path.join(LOCAL_SDK_DIR, "build-tools.zip")
        print(f"[*] Bootstrapping Android SDK build-tools for {os_name}...")
        urllib.request.urlretrieve(url, zip_path)
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(LOCAL_SDK_DIR)
        extracted = os.path.join(LOCAL_SDK_DIR, "android-13")
        if os.path.exists(extracted):
            if os.path.exists(bt_dir):
                shutil.rmtree(bt_dir)
            os.rename(extracted, bt_dir)
        if os.path.exists(zip_path):
            os.remove(zip_path)
        if not is_win:
            for binary in ["aapt2", "zipalign"]:
                bp = os.path.join(bt_dir, binary)
                if os.path.exists(bp):
                    os.chmod(bp, 0o755)

    if not os.path.exists(plat_jar):
        print("[*] Downloading android.jar (API 33 baseline)...")
        os.makedirs(os.path.dirname(plat_jar), exist_ok=True)
        urllib.request.urlretrieve(ANDROID_JAR_URL, plat_jar)

    return bt_dir, plat_jar

def get_sdk_tools():
    """
    Returns dict with paths to:
    aapt2, zipalign, d8_jar, apksigner_jar, android_jar
    """
    is_win = platform.system() == "Windows"
    exe_suffix = ".exe" if is_win else ""

    # 1. Check preinstalled system Android SDK
    candidates = []
    for env_var in ["ANDROID_HOME", "ANDROID_SDK_ROOT"]:
        val = os.environ.get(env_var)
        if val:
            candidates.append(val)
    candidates.extend([
        "/usr/local/lib/android/sdk",
        "/opt/android-sdk",
        os.path.expanduser("~/Android/Sdk"),
        os.path.expandvars("%LOCALAPPDATA%/Android/Sdk")
    ])

    bt_path = None
    plat_jar = None

    for cand in candidates:
        bt = _find_in_sdk(cand)
        if bt and os.path.exists(os.path.join(bt, f"aapt2{exe_suffix}")):
            bt_path = bt
            # Find android.jar
            for api in ["android-33", "android-34", "android-32", "android-31"]:
                pj = os.path.join(cand, "platforms", api, "android.jar")
                if os.path.exists(pj):
                    plat_jar = pj
                    break
            break

    # 2. Check local SDK directory or bootstrap if missing
    local_bt = os.path.join(LOCAL_SDK_DIR, "build-tools")
    local_jar = os.path.join(LOCAL_SDK_DIR, "platforms", "android-33", "android.jar")
    if not bt_path or not os.path.exists(os.path.join(bt_path, f"aapt2{exe_suffix}")):
        if os.path.exists(os.path.join(local_bt, f"aapt2{exe_suffix}")):
            bt_path = local_bt
        else:
            bt_path, _ = bootstrap_sdk()

    if not plat_jar or not os.path.exists(plat_jar):
        if os.path.exists(local_jar):
            plat_jar = local_jar
        else:
            _, plat_jar = bootstrap_sdk()

    aapt2 = os.path.join(bt_path, f"aapt2{exe_suffix}")
    zipalign = os.path.join(bt_path, f"zipalign{exe_suffix}")
    d8_jar = os.path.join(bt_path, "lib", "d8.jar")
    apksigner_jar = os.path.join(bt_path, "lib", "apksigner.jar")

    # If zipalign is not in build-tools directory, search PATH
    if not os.path.exists(zipalign):
        which_z = shutil.which("zipalign")
        if which_z:
            zipalign = which_z

    return {
        "aapt2": aapt2,
        "zipalign": zipalign,
        "d8_jar": d8_jar,
        "apksigner_jar": apksigner_jar,
        "android_jar": plat_jar
    }

def run_aapt2(args):
    tools = get_sdk_tools()
    return subprocess.run([tools["aapt2"]] + args, capture_output=True, text=True)

def run_d8(args):
    tools = get_sdk_tools()
    cmd = ["java", "-cp", tools["d8_jar"], "com.android.tools.r8.D8"] + args
    return subprocess.run(cmd, capture_output=True, text=True)

def run_zipalign(args):
    tools = get_sdk_tools()
    return subprocess.run([tools["zipalign"]] + args, capture_output=True, text=True)

def run_apksigner(args):
    tools = get_sdk_tools()
    cmd = ["java", "-jar", tools["apksigner_jar"]] + args
    return subprocess.run(cmd, capture_output=True, text=True)

def parse_apk_manifest(apk_path):
    """
    Parses compiled binary AndroidManifest.xml from APK using aapt2 dump badging & xmltree.
    Returns dict: {pkg, versionCode, versionName, label, nsfw, extClass, minSdk, targetSdk}
    """
    tools = get_sdk_tools()
    badge_res = subprocess.run([tools["aapt2"], "dump", "badging", apk_path], capture_output=True, text=True)
    if badge_res.returncode != 0:
        raise ValueError(f"aapt2 dump badging failed on {apk_path}: {badge_res.stderr.strip()}")
    badge = badge_res.stdout

    tree_res = subprocess.run([tools["aapt2"], "dump", "xmltree", apk_path, "--file", "AndroidManifest.xml"], capture_output=True, text=True)
    if tree_res.returncode != 0:
        raise ValueError(f"aapt2 dump xmltree failed on {apk_path}: {tree_res.stderr.strip()}")
    tree = tree_res.stdout

    pkg = re.search(r"package: name='([^']+)'", badge).group(1)
    vcode = int(re.search(r"versionCode='(\d+)'", badge).group(1))
    vname = re.search(r"versionName='([^']+)'", badge).group(1)
    
    label_m = re.search(r"application: label='([^']+)'", badge)
    label = label_m.group(1) if label_m else ""

    minsdk_m = re.search(r"(?:sdkVersion|minSdkVersion)[\s:']*(\d+)", badge)
    if not minsdk_m:
        minsdk_m = re.search(r"minSdkVersion.*?=(\d+)", tree)
    minsdk = int(minsdk_m.group(1)) if minsdk_m else 0

    targetsdk_m = re.search(r"targetSdkVersion[\s:']*(\d+)", badge)
    if not targetsdk_m:
        targetsdk_m = re.search(r"targetSdkVersion.*?=(\d+)", tree)
    targetsdk = int(targetsdk_m.group(1)) if targetsdk_m else 0

    nsfw_m = re.search(r'tachiyomi\.animeextension\.nsfw\".*?value.*?(\d+)', tree, re.DOTALL)
    nsfw = int(nsfw_m.group(1)) if nsfw_m else 1

    class_m = re.search(r'tachiyomi\.animeextension\.class\".*?value.*?\"([^\"]+)\"', tree, re.DOTALL)
    ext_class = class_m.group(1) if class_m else ""

    return {
        "pkg": pkg,
        "versionCode": vcode,
        "versionName": vname,
        "label": label,
        "minSdk": minsdk,
        "targetSdk": targetsdk,
        "nsfw": nsfw,
        "extClass": ext_class
    }
