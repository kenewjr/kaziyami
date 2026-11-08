#!/usr/bin/env python3
"""
test_source_instantiation.py:
Verifies that built extension APK artifacts can be instantiated reflectively
through host entry points (AnimeSourceFactory / extClass reflection),
ensuring no NoSuchMethodError, ClassNotFoundException, or constructor crashes occur,
and validating that runtime name, lang, and 64-bit source ID match docs/SOURCE_IDS.md.
"""

import glob
import os
import struct
import subprocess
import sys
import unittest
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
import android_sdk

EXPECTED_SOURCES = {
    "Nekopoi": {"name": "Nekopoi", "lang": "id", "id": 7073589375897212735},
    "NontonHentai": {"name": "NontonHentai", "lang": "id", "id": 7400791378172011716},
    "Hentaimama": {"name": "Hentaimama", "lang": "en", "id": 8058423919997530503},
    "Hanime": {"name": "Hanime", "lang": "en", "id": 2150907185602526076},
    "RuleVid": {"name": "RuleVid", "lang": "en", "id": 3619818370138619659},
    "Rule34Video": {"name": "Rule34Video", "lang": "en", "id": 9212790518101043860},
}

class TestSourceInstantiation(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        # Compile SourceInstantiationRunner.java once
        cls.runner_dir = os.path.join(ROOT_DIR, "build", "test_runner")
        os.makedirs(cls.runner_dir, exist_ok=True)
        runner_src = os.path.join(ROOT_DIR, "tools", "SourceInstantiationRunner.java")
        ext_lib = os.path.join(ROOT_DIR, "libs", "extensions-lib-14.jar")
        javac_cmd = ["javac", "-cp", ext_lib, "-d", cls.runner_dir, runner_src]
        res = subprocess.run(javac_cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Failed to compile SourceInstantiationRunner: {res.stderr}")

    def test_all_apks_exist_and_match_permanent_ids(self):
        apks = sorted(glob.glob(os.path.join(ROOT_DIR, "repo", "apk", "*.apk")))
        self.assertEqual(len(apks), 6, f"Expected 6 built APKs, found {len(apks)}")

        for apk_path in apks:
            apk_name = os.path.basename(apk_path)
            meta = android_sdk.parse_apk_manifest(apk_path)
            ext_class = meta.get("extClass")
            self.assertTrue(ext_class, f"Manifest missing extClass in {apk_name}")

            # 1. Verify classes.dex contains the extension class
            with zipfile.ZipFile(apk_path, "r") as zf:
                self.assertIn("classes.dex", zf.namelist(), f"classes.dex missing in {apk_name}")
                dex_data = zf.read("classes.dex")

            class_type = f"L{ext_class.replace('.', '/')};"
            self.assertTrue(
                self._class_in_dex(dex_data, class_type),
                f"Extension class {class_type} not found in classes.dex of {apk_name}"
            )

            # 2. Match slug to expected source
            source_slug = None
            for sname in EXPECTED_SOURCES:
                if sname.lower() in apk_name.lower():
                    source_slug = sname
                    break
            self.assertIsNotNone(source_slug, f"Unrecognized source slug for APK {apk_name}")
            expected = EXPECTED_SOURCES[source_slug]

            # 3. Reflective instantiation test against packaged classes jar
            packaged_jar = os.path.join(ROOT_DIR, "build", "packaged-jars", f"{source_slug.lower()}.jar")
            self.assertTrue(os.path.exists(packaged_jar), f"Missing packaged jar: {packaged_jar}")

            sources_found = self._run_instantiation(packaged_jar, ext_class)
            self.assertGreaterEqual(len(sources_found), 1, f"No sources instantiated for {source_slug}")

            matched = False
            for s in sources_found:
                if s["name"] == expected["name"]:
                    self.assertEqual(s["lang"], expected["lang"], f"Language mismatch for {source_slug}")
                    self.assertEqual(s["id"], expected["id"], f"Source ID mismatch for {source_slug}")
                    matched = True
                    break
            self.assertTrue(matched, f"Source {expected['name']} not returned by instantiation of {ext_class}")

    def _class_in_dex(self, dex_data, target_class_type):
        (
            str_sz, str_off, type_sz, type_off,
            proto_sz, proto_off, field_sz, field_off,
            meth_sz, meth_off, cls_sz, cls_off
        ) = struct.unpack_from("<12I", dex_data, 56)

        def get_string(idx):
            off = struct.unpack_from("<I", dex_data, str_off + idx * 4)[0]
            pos = off
            while dex_data[pos] & 0x80:
                pos += 1
            pos += 1
            end = dex_data.find(b"\x00", pos)
            return dex_data[pos:end].decode("utf-8", errors="replace")

        for i in range(cls_sz):
            cidx = struct.unpack_from("<I", dex_data, cls_off + i * 32)[0]
            str_idx = struct.unpack_from("<I", dex_data, type_off + cidx * 4)[0]
            cname = get_string(str_idx)
            if cname == target_class_type:
                return True
        return False

    def _run_instantiation(self, packaged_jar, ext_class):
        sdk_tools = android_sdk.get_sdk_tools()
        android_jar = sdk_tools["android_jar"]
        ext_lib = os.path.join(ROOT_DIR, "libs", "extensions-lib-14.jar")
        core_test_classes = os.path.join(ROOT_DIR, "core", "build", "classes", "kotlin", "test")

        host_libs = []
        host_dir = os.path.join(ROOT_DIR, "build", "host-libs")
        if os.path.exists(host_dir):
            host_libs = [os.path.join(host_dir, f) for f in os.listdir(host_dir) if f.endswith(".jar")]

        cp_parts = [
            self.runner_dir,
            packaged_jar,
            core_test_classes,
            ext_lib,
            android_jar
        ] + host_libs

        cp = os.pathsep.join(cp_parts)
        cmd = ["java", "-cp", cp, "app.kaziyami.test.SourceInstantiationRunner", ext_class]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(
                f"Instantiation failed for {ext_class}:\nSTDOUT:\n{res.stdout}\nSTDERR:\n{res.stderr}"
            )

        sources = []
        for line in res.stdout.strip().splitlines():
            if line.startswith("SOURCE|"):
                parts = line.split("|")
                sources.append({
                    "name": parts[1],
                    "lang": parts[2],
                    "id": int(parts[3])
                })
        return sources

if __name__ == "__main__":
    unittest.main()

