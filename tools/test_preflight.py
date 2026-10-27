#!/usr/bin/env python3
"""
tools/test_preflight.py
Unit tests for scripts/preflight.py redaction and classification logic.
"""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "scripts"))
import preflight

class TestPreflight(unittest.TestCase):

    def test_scrub_sensitive_cookies(self):
        msg = "Outgoing headers: Cookie: cf_clearance=secret123; session_id=token999"
        scrubbed = preflight.scrub_sensitive(msg)
        self.assertNotIn("secret123", scrubbed)
        self.assertNotIn("token999", scrubbed)
        self.assertIn("[REDACTED]", scrubbed)

    def test_scrub_sensitive_signed_url_queries(self):
        msg = "Fetching stream https://cdn.nekopoi.care/master.m3u8?token=xyz123&expires=1700000000"
        scrubbed = preflight.scrub_sensitive(msg)
        self.assertNotIn("xyz123", scrubbed)
        self.assertIn("?[REDACTED_QUERY]", scrubbed)

    def test_scrub_sensitive_auth_params(self):
        msg = "URL payload auth=super_secret_password and bearer=bearer_token_abc"
        scrubbed = preflight.scrub_sensitive(msg)
        self.assertNotIn("super_secret_password", scrubbed)
        self.assertIn("token=[REDACTED]", scrubbed)

    def test_isp_block_keyword_detection(self):
        body = "<html><body><h1>Internet Positif</h1><p>Situs ini diblokir sesuai regulasi</p></body></html>"
        is_blocked = any(kw in body.lower() for kw in preflight.KNOWN_ISP_BLOCK_KEYWORDS)
        self.assertTrue(is_blocked)

    def test_toolchain_checks_return_valid_dict(self):
        res = preflight.check_toolchain()
        self.assertIn("java", res)
        self.assertIn("adb", res)
        self.assertIn("gradlew", res)

    def test_check_repository_artifacts_structure(self):
        res = preflight.check_repository_artifacts()
        self.assertIn("variant_a_aniyomi", res)
        self.assertIn("variant_b_kototoro", res)
        self.assertIn(res["variant_a_aniyomi"]["status"], ["PASS", "WARN", "FAIL"])
        self.assertIn(res["variant_b_kototoro"]["status"], ["PASS", "WARN", "FAIL"])
        self.assertIsInstance(res["variant_a_aniyomi"]["details"], list)
        self.assertIsInstance(res["variant_b_kototoro"]["details"], list)

    def test_check_repository_artifacts_passes_for_current_repo(self):
        res = preflight.check_repository_artifacts()
        self.assertEqual(res["variant_a_aniyomi"]["status"], "PASS")
        self.assertEqual(res["variant_b_kototoro"]["status"], "PASS")
        self.assertEqual(len(res["variant_a_aniyomi"]["errors"]), 0)
        self.assertEqual(len(res["variant_b_kototoro"]["errors"]), 0)

if __name__ == "__main__":
    unittest.main()
