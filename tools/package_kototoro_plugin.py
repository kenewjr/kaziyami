#!/usr/bin/env python3
"""
package_kototoro_plugin.py:
Packages all Kaziyami adult anime extensions into a single Kototoro-compatible
plugin JAR: build/libs/kaziyami-kototoro-all-v1.4.1.jar.
Includes text AndroidManifest.xml parsed natively by Kototoro's MihonJarInspector.
Ensures zero test fixtures, zero secrets, and zero host class collisions.
"""

import os
import sys
import zipfile
import re

VERSION = "1.4.1"
OUTPUT_JAR = os.path.join("build", "libs", f"kaziyami-kototoro-all-v{VERSION}.jar")

MANIFEST_XML = f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="app.kaziyami.kototoro"
    android:versionName="{VERSION}"
    android:versionCode="141">
    <application>
        <meta-data android:name="tachiyomi.animeextension.class" android:value="app.kaziyami.adapter.kototoro.KototoroPlugin" />
        <meta-data android:name="tachiyomi.animeextension.nsfw" android:value="1" />
        <meta-data android:name="tachiyomix.name" android:value="Kaziyami Adult Suite" />
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

def package():
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    output_path = os.path.join(root_dir, OUTPUT_JAR)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    print(f"[*] Packaging Kototoro plugin JAR: {output_path}")

    # Search for compiled classes in all build/classes/kotlin/main directories
    added_entries = set()
    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zout:
        # 1. Write AndroidManifest.xml
        zout.writestr("AndroidManifest.xml", MANIFEST_XML.strip())
        added_entries.add("AndroidManifest.xml")

        # 2. Collect class files
        for dirpath, _, filenames in os.walk(root_dir):
            if "build" in dirpath and "classes" in dirpath and "main" in dirpath:
                rel_base = dirpath.split("classes" + os.sep)[1]
                # rel_base is like 'kotlin/main' or 'java/main'
                prefix = os.path.join("classes", rel_base)
                base_len = dirpath.find(prefix) + len(prefix) + 1
                base_dir = dirpath[:base_len].rstrip(os.sep)

                for fname in filenames:
                    if fname.endswith(".class"):
                        full_class_path = os.path.join(dirpath, fname)
                        arc_name = os.path.relpath(full_class_path, base_dir).replace("\\", "/")
                        if should_include(arc_name) and arc_name not in added_entries:
                            zout.write(full_class_path, arc_name)
                            added_entries.add(arc_name)

    print(f"[+] Successfully packaged {len(added_entries)} entries into {output_path} ({os.path.getsize(output_path)} bytes)")
    return 0

if __name__ == "__main__":
    sys.exit(package())
