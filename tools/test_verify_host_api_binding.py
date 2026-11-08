#!/usr/bin/env python3
"""
test_verify_host_api_binding.py:
Unit tests for host API binding verification logic.
Ensures that:
1. Valid method calls on host classes resolve.
2. Method calls resolved via inheritance chain (GenericThemeSource -> ParsedAnimeHttpSource -> AnimeHttpSource) resolve.
3. Calls to nonexistent methods (e.g. reproducing the device NoSuchMethodError GET(String)) are flagged as unresolved.
4. Nonexistent fields are flagged as unresolved.
5. All built APKs pass the full suite.
"""

import os
import sys
import unittest

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))

import verify_host_api_binding as checker

class TestVerifyHostApiBinding(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.host_classes = checker.load_host_classes()
        cls.apk_path = os.path.join(ROOT_DIR, "repo", "apk", "kaziyami-id.nekopoi-v14.3.apk")
        if os.path.exists(cls.apk_path):
            with checker.zipfile.ZipFile(cls.apk_path, "r") as zf:
                dex_data = zf.read("classes.dex")
            cls.local_classes, cls.method_refs, cls.field_refs = checker.parse_dex(dex_data)
        else:
            cls.local_classes, cls.method_refs, cls.field_refs = {}, [], []

    def test_host_classes_loaded(self):
        self.assertGreater(len(self.host_classes), 5000, "Must load host API classes from compileOnly jars")
        self.assertIn("Leu/kanade/tachiyomi/animesource/online/AnimeHttpSource;", self.host_classes)
        self.assertIn("Leu/kanade/tachiyomi/network/RequestsKt;", self.host_classes)
        self.assertIn("Lokhttp3/OkHttpClient;", self.host_classes)

    def test_valid_host_method_resolution(self):
        # Direct static method call
        resolved = checker.resolve_method(
            "Leu/kanade/tachiyomi/network/RequestsKt;",
            "GET$default",
            "(Ljava/lang/String;Lokhttp3/Headers;Lokhttp3/CacheControl;ILjava/lang/Object;)Lokhttp3/Request;",
            self.local_classes,
            self.host_classes
        )
        self.assertTrue(resolved, "RequestsKt.GET$default must resolve")

    def test_valid_inheritance_hierarchy_resolution(self):
        # getId() is declared on AnimeHttpSource, but called on GenericThemeSource
        resolved = checker.resolve_method(
            "Lapp/kaziyami/multisrc/generic/GenericThemeSource;",
            "getId",
            "()J",
            self.local_classes,
            self.host_classes
        )
        self.assertTrue(resolved, "GenericThemeSource.getId() must resolve through AnimeHttpSource")

    def test_reproduce_and_flag_get_string_nosuchmethoderror(self):
        """
        REPRODUCING THE DEVICE BUG:
        The crash observed on device:
        'No virtual method GET(Ljava/lang/String;)Lokhttp3/Request; in class GenericThemeSource'
        The checker MUST flag this call as unresolvable.
        """
        resolved = checker.resolve_method(
            "Lapp/kaziyami/multisrc/generic/GenericThemeSource;",
            "GET",
            "(Ljava/lang/String;)Lokhttp3/Request;",
            self.local_classes,
            self.host_classes
        )
        self.assertFalse(resolved, "Checker MUST flag GenericThemeSource.GET(String) as unresolvable!")

    def test_reproduce_and_flag_nonexistent_field(self):
        resolved = checker.resolve_field(
            "Lapp/kaziyami/multisrc/generic/GenericThemeSource;",
            "nonexistentField",
            "Ljava/lang/String;",
            self.local_classes,
            self.host_classes
        )
        self.assertFalse(resolved, "Checker MUST flag nonexistent field as unresolvable")

    def test_live_apks_pass_verification(self):
        if not os.path.exists(self.apk_path):
            self.skipTest("Built APK not found; run assembleRelease first")
        errors = checker.verify_apk(self.apk_path, self.host_classes)
        self.assertEqual([], errors, f"Built APK must have 0 binding errors, got: {errors}")

if __name__ == "__main__":
    unittest.main()

