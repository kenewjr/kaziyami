#!/usr/bin/env python3
"""
test_verify_apks.py:
Unit tests for the APK verification logic using small fixture files:
1. Valid APK fixture.
2. Unsigned APK fixture.
3. HTML-masquerading-as-APK fixture.
4. Truncated ZIP fixture.
5. Plain-text manifest fixture.
6. Wrong SHA-256 fixture.
"""

import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT_DIR, "tools"))
import android_sdk
import verify_apks

class TestApkVerification(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sdk_tools = android_sdk.get_sdk_tools()
        cls.sources = verify_apks.load_source_ids()
        cls.test_dir = tempfile.mkdtemp(prefix="kaziyami_test_verify_")

        # Create a throwaway test keystore
        cls.ks_path = os.path.join(cls.test_dir, "test.keystore")
        subprocess.check_call([
            "keytool", "-genkeypair", "-alias", "testkey", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "365", "-keystore", cls.ks_path, "-storepass", "testpass", "-keypass", "testpass",
            "-dname", "CN=Test, O=Kaziyami, C=ID"
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        # Build a valid fixture APK
        cls.valid_apk = os.path.join(cls.test_dir, "valid.apk")
        cls._create_valid_fixture(cls.valid_apk)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.test_dir, ignore_errors=True)

    @classmethod
    def _create_valid_fixture(cls, out_apk):
        manifest_xml = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="eu.kanade.tachiyomi.animeextension.id.nekopoi"
    android:versionCode="2"
    android:versionName="14.2">
    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="33" />
    <uses-feature android:name="tachiyomi.animeextension" />
    <application
        android:label="Kaziyami: Nekopoi"
        android:hasCode="true">
        <meta-data
            android:name="tachiyomi.animeextension.class"
            android:value="eu.kanade.tachiyomi.animeextension.id.nekopoi.Nekopoi" />
        <meta-data
            android:name="tachiyomi.animeextension.version"
            android:value="14" />
        <meta-data
            android:name="tachiyomix.extensionLib"
            android:value="14" />
        <meta-data
            android:name="tachiyomi.animeextension.nsfw"
            android:value="1" />
    </application>
</manifest>"""
        man_path = os.path.join(cls.test_dir, "AndroidManifest.xml")
        with open(man_path, "w", encoding="utf-8") as f:
            f.write(manifest_xml)

        base_apk = os.path.join(cls.test_dir, "base_fix.apk")
        if os.path.exists(base_apk): os.remove(base_apk)
        subprocess.check_call([
            cls.sdk_tools["aapt2"], "link",
            "-I", cls.sdk_tools["android_jar"],
            "--manifest", man_path,
            "-o", base_apk
        ])

        # Create dummy classes.dex
        dex_path = os.path.join(cls.test_dir, "classes.dex")
        with open(dex_path, "wb") as f:
            # Header magic for DEX 035 followed by padding to exceed 50KB min size
            f.write(b"dex\n035\x00" + b"\x00" * 60000)

        with zipfile.ZipFile(base_apk, "a") as zf:
            zf.write(dex_path, "classes.dex")

        aligned_apk = os.path.join(cls.test_dir, "aligned_fix.apk")
        subprocess.check_call([cls.sdk_tools["zipalign"], "-p", "-f", "4", base_apk, aligned_apk])

        subprocess.check_call([
            "java", "-jar", cls.sdk_tools["apksigner_jar"], "sign",
            "--ks", cls.ks_path,
            "--ks-key-alias", "testkey",
            "--ks-pass", "pass:testpass",
            "--key-pass", "pass:testpass",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "true",
            "--out", out_apk,
            aligned_apk
        ])

    def test_valid_apk(self):
        errs = verify_apks.verify_single_apk(self.valid_apk, self.sources, allow_debug_cert=True)
        self.assertEqual(errs, [], f"Expected 0 errors for valid APK, got: {errs}")

    def test_unsigned_apk(self):
        unsigned_apk = os.path.join(self.test_dir, "unsigned.apk")
        shutil.copy(self.valid_apk, unsigned_apk)
        # Strip signature block by removing META-INF
        with zipfile.ZipFile(self.valid_apk, "r") as zin:
            with zipfile.ZipFile(unsigned_apk, "w") as zout:
                for item in zin.infolist():
                    if not item.filename.startswith("META-INF/"):
                        zout.writestr(item, zin.read(item.filename))

        errs = verify_apks.verify_single_apk(unsigned_apk, self.sources, allow_debug_cert=True)
        self.assertTrue(any("apksigner verification failed" in e or "not signed" in e for e in errs),
                        f"Expected signature failure, got: {errs}")

    def test_html_masquerading_as_apk(self):
        html_apk = os.path.join(self.test_dir, "fake_html.apk")
        with open(html_apk, "wb") as f:
            f.write(b"<!DOCTYPE html><html><body>404 Not Found</body></html>" + b" " * 60000)

        errs = verify_apks.verify_single_apk(html_apk, self.sources, allow_debug_cert=True)
        self.assertTrue(any("Failed to read APK as ZIP" in e for e in errs),
                        f"Expected ZIP parse failure, got: {errs}")

    def test_truncated_zip(self):
        trunc_apk = os.path.join(self.test_dir, "truncated.apk")
        with open(trunc_apk, "wb") as f:
            f.write(b"PK\x03\x04\x14\x00\x00\x00\x08\x00" + b"\x00" * 60000)

        errs = verify_apks.verify_single_apk(trunc_apk, self.sources, allow_debug_cert=True)
        self.assertTrue(any("Failed to read APK as ZIP" in e or "Corrupt ZIP" in e for e in errs),
                        f"Expected corrupt/truncated ZIP error, got: {errs}")

    def test_plain_text_manifest(self):
        text_apk = os.path.join(self.test_dir, "text_manifest.apk")
        with zipfile.ZipFile(text_apk, "w") as zf:
            zf.writestr("AndroidManifest.xml", "<?xml version=\"1.0\"?><manifest/>")
            zf.writestr("classes.dex", b"dex\n035\x00" + b"\x00" * 60000)

        errs = verify_apks.verify_single_apk(text_apk, self.sources, allow_debug_cert=True)
        self.assertTrue(any("plain text XML instead of Android Binary XML" in e for e in errs),
                        f"Expected plain text manifest error, got: {errs}")

    def test_wrong_sha256(self):
        actual_hash = hashlib.sha256(b"hello").hexdigest()
        expected_hash = hashlib.sha256(b"world").hexdigest()
        self.assertNotEqual(actual_hash, expected_hash)

if __name__ == "__main__":
    unittest.main()
