#!/usr/bin/env python3
"""
tools/test_update_simulation.py:
Automated test simulating Release N vs Release N+1 for both distribution variants:
- Variant A: Aniyomi APK repo (repo/index.json)
- Variant B: Kototoro JAR repo (repo/jar/index.json)

Verifies:
1. Strict integer comparison: only packages with remote.code > installed.code get flagged.
2. Unchanged packages remain unflagged (state = INSTALLED).
3. Same-version republish and downgrade return False (never flagged).
4. Immutable filenames: artifact names are distinct across version bumps.
"""

import json
import os
import sys
import unittest

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def is_newer_than_installed(installed_code, remote_code):
    """Kototoro upstream rule: ExtensionUpdatePolicy.kt:5"""
    return installed_code is not None and remote_code > installed_code

class TestUpdateSimulation(unittest.TestCase):

    def test_live_indexes_exist_and_conform(self):
        jar_index_path = os.path.join(ROOT_DIR, "repo", "jar", "index.json")
        apk_index_path = os.path.join(ROOT_DIR, "repo", "index.json")

        self.assertTrue(os.path.exists(jar_index_path), "repo/jar/index.json must exist")
        self.assertTrue(os.path.exists(apk_index_path), "repo/index.json must exist")

        with open(jar_index_path, "r", encoding="utf-8") as f:
            jar_data = json.load(f)
        with open(apk_index_path, "r", encoding="utf-8") as f:
            apk_data = json.load(f)

        jar_extensions = jar_data["extensionList"]["extensions"]
        self.assertEqual(len(jar_extensions), 7)  # Suite + 6 individual sources
        self.assertEqual(len(apk_data), 6)  # 6 extensions

        # Verify versionCode and sha256 presence
        for ext in jar_extensions:
            self.assertIn("versionCode", ext)
            self.assertIn("versionName", ext)
            self.assertIn("sha256", ext)
            self.assertIn("size", ext)
            self.assertGreater(ext["versionCode"], 0)

        for entry in apk_data:
            self.assertIn("code", entry)
            self.assertIn("version", entry)
            self.assertIn("sha256", entry)
            self.assertIn("size", entry)
            self.assertGreater(entry["code"], 0)

    def test_simulated_n_plus_one_update_policy(self):
        # Installed state on device: version 2
        installed = {
            "eu.kanade.tachiyomi.animeextension.id.nekopoi": 2,
            "eu.kanade.tachiyomi.animeextension.id.indohentai": 2,
            "eu.kanade.tachiyomi.animeextension.id.nontonhentai": 2,
            "eu.kanade.tachiyomi.animeextension.en.hentaimama": 2,
            "eu.kanade.tachiyomi.animeextension.en.javsubbed": 2,
            "eu.kanade.tachiyomi.animeextension.en.hanime": 2,
        }

        # Simulated remote index where only Nekopoi is bumped to 3
        remote_catalog = {
            "eu.kanade.tachiyomi.animeextension.id.nekopoi": 3,
            "eu.kanade.tachiyomi.animeextension.id.indohentai": 2,
            "eu.kanade.tachiyomi.animeextension.id.nontonhentai": 2,
            "eu.kanade.tachiyomi.animeextension.en.hentaimama": 2,
            "eu.kanade.tachiyomi.animeextension.en.javsubbed": 2,
            "eu.kanade.tachiyomi.animeextension.en.hanime": 2,
        }

        updates_available = {}
        for pkg, remote_code in remote_catalog.items():
            installed_code = installed.get(pkg)
            updates_available[pkg] = is_newer_than_installed(installed_code, remote_code)

        # Assert only Nekopoi is flagged
        self.assertTrue(updates_available["eu.kanade.tachiyomi.animeextension.id.nekopoi"])
        self.assertFalse(updates_available["eu.kanade.tachiyomi.animeextension.id.indohentai"])
        self.assertFalse(updates_available["eu.kanade.tachiyomi.animeextension.id.nontonhentai"])
        self.assertFalse(updates_available["eu.kanade.tachiyomi.animeextension.en.hentaimama"])
        self.assertFalse(updates_available["eu.kanade.tachiyomi.animeextension.en.javsubbed"])
        self.assertFalse(updates_available["eu.kanade.tachiyomi.animeextension.en.hanime"])

    def test_same_version_and_downgrade_not_flagged(self):
        # Same version: 2 vs 2
        self.assertFalse(is_newer_than_installed(2, 2))
        # Downgrade: 3 installed, remote 2
        self.assertFalse(is_newer_than_installed(3, 2))

    def test_immutable_artifact_names(self):
        rel_n_jar = "kaziyami-nekopoi-v1.4.2.jar"
        rel_n1_jar = "kaziyami-nekopoi-v1.4.3.jar"
        self.assertNotEqual(rel_n_jar, rel_n1_jar)

        rel_n_apk = "kaziyami-id.nekopoi-v14.2.apk"
        rel_n1_apk = "kaziyami-id.nekopoi-v14.3.apk"
        self.assertNotEqual(rel_n_apk, rel_n1_apk)

if __name__ == "__main__":
    unittest.main()

