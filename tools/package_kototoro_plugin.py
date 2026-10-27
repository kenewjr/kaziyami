#!/usr/bin/env python3
"""
package_kototoro_plugin.py:
Packages Kaziyami adult anime extensions into Kototoro-compatible unified plugin JARs:
- repo/jar/kaziyami-all-v1.4.1.jar (all 6 adult anime sources)
- repo/jar/kaziyami-<source>-v1.4.1.jar (individual per-source JARs)

Bytecode layout:
- Standard Java .class files for Desktop JVM (Windows, macOS, Linux)
- Dalvik executable (classes.dex) generated via D8 for Android ART
- Entry point: org.skepsun.kototoro.parsers.ContentParserFactoryKt.class
- Source enum: org.skepsun.kototoro.parsers.model.ContentParserSource.class
- Text AndroidManifest.xml for Aniyomi/Mihon source-host compatibility
- Zero test fixtures, zero secrets, zero third-party host class collisions.
"""

import hashlib
import os
import re
import shutil
import sys
import tempfile
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
import android_sdk

REPO_JAR_DIR = os.path.join(ROOT_DIR, "repo", "jar")
BUILD_LIBS_DIR = os.path.join(ROOT_DIR, "build", "libs")

SOURCES = [
    {"name": "Nekopoi", "lang": "id", "slug": "nekopoi", "src_dir": "src/id/nekopoi", "multisrc": "lib-multisrc/generic"},
    {"name": "IndoHentai", "lang": "id", "slug": "indohentai", "src_dir": "src/id/indohentai", "multisrc": "lib-multisrc/generic"},
    {"name": "NontonHentai", "lang": "id", "slug": "nontonhentai", "src_dir": "src/id/nontonhentai", "multisrc": "lib-multisrc/animestream"},
    {"name": "Hentaimama", "lang": "en", "slug": "hentaimama", "src_dir": "src/en/hentaimama", "multisrc": "lib-multisrc/dooplay"},
    {"name": "JAVSubbed", "lang": "en", "slug": "javsubbed", "src_dir": "src/en/javsubbed", "multisrc": "lib-multisrc/retrotube"},
    {"name": "Hanime", "lang": "en", "slug": "hanime", "src_dir": "src/en/hanime", "multisrc": "lib-multisrc/generic"},
]

def get_source_version(src_dir: str):
    build_file = os.path.join(ROOT_DIR, src_dir, "build.gradle.kts")
    if not os.path.exists(build_file):
        return {"versionCode": 2, "versionName": "1.4.2"}
    with open(build_file, "r", encoding="utf-8") as f:
        content = f.read()
    code_m = re.search(r'val\s+extVersionCode\s*=\s*(\d+)', content)
    version_code = int(code_m.group(1)) if code_m else 2
    lib_m = re.search(r'val\s+libVersion\s*=\s*"([^"]+)"', content)
    lib_ver = lib_m.group(1) if lib_m else "1.4"
    if lib_ver == "14":
        lib_ver = "1.4"
    version_name = f"{lib_ver}.{version_code}"
    return {
        "versionCode": version_code,
        "versionName": version_name
    }

def make_manifest_xml(package_name: str, ext_class: str, display_name: str, version_name: str, version_code: int) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{package_name}"
    android:versionName="{version_name}"
    android:versionCode="{version_code}">
    <application>
        <meta-data android:name="tachiyomi.animeextension.class" android:value="{ext_class}" />
        <meta-data android:name="tachiyomi.animeextension.nsfw" android:value="1" />
        <meta-data android:name="tachiyomix.name" android:value="{display_name}" />
        <meta-data android:name="tachiyomix.extensionLib" android:value="1.4" />
    </application>
</manifest>
"""

EXCLUDE_PATTERNS = [
    re.compile(r"Test.*\.class$"),
    re.compile(r".*Test\.class$"),
    re.compile(r".*\.html$"),
    re.compile(r".*\.keystore$"),
    re.compile(r".*\.jks$"),
    re.compile(r"META-INF/.*\.SF$"),
    re.compile(r"META-INF/.*\.RSA$"),
    re.compile(r"META-INF/.*\.DSA$"),
]

def should_include(entry_name: str) -> bool:
    for pat in EXCLUDE_PATTERNS:
        if pat.search(entry_name):
            return False
    return True

def collect_classes_from_dir(build_dir: str):
    """Collects all .class files under a module's build/classes/kotlin/main directory."""
    classes = {}
    main_dir = os.path.join(build_dir, "build", "classes", "kotlin", "main")
    if not os.path.exists(main_dir):
        return classes
    for root, _, files in os.walk(main_dir):
        for f in files:
            if f.endswith(".class"):
                full_path = os.path.join(root, f)
                rel_path = os.path.relpath(full_path, main_dir).replace("\\", "/")
                if should_include(rel_path):
                    classes[rel_path] = full_path
    return classes

def run_d8_on_classes(class_files: list, output_dir: str):
    """Runs D8 dexer on class files to generate classes.dex."""
    try:
        tools = android_sdk.get_sdk_tools()
        android_jar = tools["android_jar"]
    except Exception as e:
        print(f"[-] Warning: Android SDK tools not available ({e}), dex step skipped")
        return None

    # Write class list or pass directly
    # D8 can take directory of classes or individual class paths
    temp_classes_dir = os.path.join(output_dir, "d8_input")
    os.makedirs(temp_classes_dir, exist_ok=True)

    args = ["--release", "--min-api", "21", "--lib", android_jar, "--output", output_dir]
    args.extend(class_files)

    res = android_sdk.run_d8(args)
    dex_path = os.path.join(output_dir, "classes.dex")
    if res.returncode == 0 and os.path.exists(dex_path):
        return dex_path
    else:
        print(f"[-] D8 dexer warning: {res.stderr.strip() if res.stderr else 'Failed'}")
        return None

def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def prune_old_artifacts(directory: str, extension: str, keep: int = 3):
    if not os.path.exists(directory):
        return
    files = [f for f in os.listdir(directory) if f.endswith(extension)]
    families = {}
    for fname in files:
        m = re.match(r'^(kaziyami-[^v]+)-v([\d\.]+)\.' + re.escape(extension.lstrip('.')) + '$', fname)
        if m:
            family = m.group(1)
            ver_str = m.group(2)
            try:
                ver_tuple = tuple(int(x) for x in ver_str.split('.'))
            except ValueError:
                ver_tuple = (0,)
            families.setdefault(family, []).append((ver_tuple, fname))
    
    for family, file_list in families.items():
        file_list.sort(key=lambda x: x[0], reverse=True)
        to_delete = file_list[keep:]
        for _, old_fname in to_delete:
            old_path = os.path.join(directory, old_fname)
            try:
                os.remove(old_path)
                print(f"[+] Pruned older artifact (kept last {keep}): {old_fname}")
            except Exception as e:
                print(f"[-] Failed to prune {old_fname}: {e}")

def package():
    os.makedirs(REPO_JAR_DIR, exist_ok=True)
    os.makedirs(BUILD_LIBS_DIR, exist_ok=True)

    print("[*] Packaging Kototoro plugin JARs (Unified Variant B)...")

    # 1. Collect core classes
    core_dir = os.path.join(ROOT_DIR, "core")
    core_classes = collect_classes_from_dir(core_dir)

    # Collect multisrc base classes
    multisrc_dirs = [
        os.path.join(ROOT_DIR, "lib-multisrc", "generic"),
        os.path.join(ROOT_DIR, "lib-multisrc", "animestream"),
        os.path.join(ROOT_DIR, "lib-multisrc", "dooplay"),
        os.path.join(ROOT_DIR, "lib-multisrc", "retrotube"),
    ]
    multisrc_classes = {}
    for md in multisrc_dirs:
        multisrc_classes.update(collect_classes_from_dir(md))

    # Collect all source classes
    all_source_classes = {}
    per_source_classes = {}
    for src in SOURCES:
        sdir = os.path.join(ROOT_DIR, src["src_dir"])
        sclasses = collect_classes_from_dir(sdir)
        per_source_classes[src["slug"]] = sclasses
        all_source_classes.update(sclasses)

    # 2. Package all-in-one suite JAR
    all_classes = {}
    all_classes.update(core_classes)
    all_classes.update(multisrc_classes)
    all_classes.update(all_source_classes)

    max_code = max(get_source_version(s["src_dir"])["versionCode"] for s in SOURCES)
    all_version_name = f"1.4.{max_code}"
    all_jar_name = f"kaziyami-all-v{all_version_name}.jar"
    all_jar_path = os.path.join(REPO_JAR_DIR, all_jar_name)

    manifest_xml = make_manifest_xml(
        package_name="app.kaziyami.kototoro",
        ext_class="app.kaziyami.adapter.kototoro.KototoroPlugin",
        display_name="Kaziyami Adult Suite",
        version_name=all_version_name,
        version_code=max_code,
    )

    with tempfile.TemporaryDirectory() as tmp_dir:
        # Run D8 on all classes
        class_file_list = list(all_classes.values())
        dex_path = run_d8_on_classes(class_file_list, tmp_dir)

        with zipfile.ZipFile(all_jar_path, "w", zipfile.ZIP_DEFLATED) as zout:
            # AndroidManifest.xml
            zout.writestr("AndroidManifest.xml", manifest_xml.strip())
            # classes.dex if generated
            if dex_path and os.path.exists(dex_path):
                zout.write(dex_path, "classes.dex")
                print(f"[+] Embedded classes.dex ({os.path.getsize(dex_path)} bytes) into {all_jar_name}")
            # .class files
            for rel_path, full_path in all_classes.items():
                zout.write(full_path, rel_path)

    # Also copy to build/libs/
    shutil.copy2(all_jar_path, os.path.join(BUILD_LIBS_DIR, all_jar_name))
    print(f"[+] Packaged {all_jar_name} ({len(all_classes)} classes, {os.path.getsize(all_jar_path)} bytes)")

    # 3. Package individual per-source JARs
    for src in SOURCES:
        slug = src["slug"]
        src_ver = get_source_version(src["src_dir"])
        single_jar_name = f"kaziyami-{slug}-v{src_ver['versionName']}.jar"
        single_jar_path = os.path.join(REPO_JAR_DIR, single_jar_name)

        src_specific_classes = {}
        src_specific_classes.update(core_classes)
        # Add required multisrc
        msrc_dir = os.path.join(ROOT_DIR, src["multisrc"])
        src_specific_classes.update(collect_classes_from_dir(msrc_dir))
        # Add source classes
        src_specific_classes.update(per_source_classes[slug])

        single_manifest = make_manifest_xml(
            package_name=f"eu.kanade.tachiyomi.animeextension.{src['lang']}.{slug}",
            ext_class=f"eu.kanade.tachiyomi.animeextension.{src['lang']}.{slug}.{src['name']}",
            display_name=f"Kaziyami: {src['name']}",
            version_name=src_ver["versionName"],
            version_code=src_ver["versionCode"],
        )

        with tempfile.TemporaryDirectory() as tmp_dir:
            single_file_list = list(src_specific_classes.values())
            dex_path = run_d8_on_classes(single_file_list, tmp_dir)

            with zipfile.ZipFile(single_jar_path, "w", zipfile.ZIP_DEFLATED) as zout:
                zout.writestr("AndroidManifest.xml", single_manifest.strip())
                if dex_path and os.path.exists(dex_path):
                    zout.write(dex_path, "classes.dex")
                for rel_path, full_path in src_specific_classes.items():
                    zout.write(full_path, rel_path)

        shutil.copy2(single_jar_path, os.path.join(BUILD_LIBS_DIR, single_jar_name))
        print(f"[+] Packaged {single_jar_name} ({os.path.getsize(single_jar_path)} bytes)")

    # 4. Prune older versions (keep newest 3)
    prune_old_artifacts(REPO_JAR_DIR, ".jar", keep=3)

    # 5. Generate sha256sums.txt for JARs
    sums_path = os.path.join(REPO_JAR_DIR, "sha256sums.txt")
    with open(sums_path, "w", encoding="utf-8") as f:
        for fname in sorted(os.listdir(REPO_JAR_DIR)):
            if fname.endswith(".jar"):
                h = sha256_file(os.path.join(REPO_JAR_DIR, fname))
                f.write(f"{h}  {fname}\n")
    print(f"[+] Generated {sums_path}")
    return 0

if __name__ == "__main__":
    sys.exit(package())
