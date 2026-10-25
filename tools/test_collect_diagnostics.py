#!/usr/bin/env python3
"""
tools/test_collect_diagnostics.py
Unit tests verifying the redaction logic and diagnostic bundle generation
in scripts/collect_diagnostics.py.
"""

import unittest
import sys
from pathlib import Path

# Add project root to path
REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT))

from scripts.collect_diagnostics import (
    sanitize_log_text,
    generate_bundle
)

class TestDiagnosticsRedaction(unittest.TestCase):

    def test_cookie_redaction(self):
        sample = (
            "GET /api/v1 HTTP/1.1\n"
            "Host: example.com\n"
            "Cookie: cf_clearance=s3cr3t_cl3aranc3; session_id=9876543210abcdef\n"
            "Set-Cookie: PHPSESSID=php_sess_12345; Secure; HttpOnly\n"
            "Random text with cf_clearance=abc123xyz inside query."
        )
        redacted = sanitize_log_text(sample)
        self.assertNotIn("s3cr3t_cl3aranc3", redacted)
        self.assertNotIn("9876543210abcdef", redacted)
        self.assertNotIn("php_sess_12345", redacted)
        self.assertNotIn("abc123xyz", redacted)
        self.assertIn("Cookie: [REDACTED_COOKIE]", redacted)
        self.assertIn("Set-Cookie: [REDACTED_COOKIE]", redacted)

    def test_bearer_and_auth_redaction(self):
        sample = (
            "D/OkHttp: --> GET https://api.site.com/auth\n"
            "D/OkHttp: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.super_secret_jwt\n"
            "D/OkHttp: Authorization: Basic dXNlcjpwYXNzd29yZA==\n"
            "Other line: Bearer secret_raw_token_value_long_123456789\n"
        )
        redacted = sanitize_log_text(sample)
        self.assertNotIn("super_secret_jwt", redacted)
        self.assertNotIn("dXNlcjpwYXNzd29yZA==", redacted)
        self.assertNotIn("secret_raw_token_value_long_123456789", redacted)
        self.assertIn("[REDACTED_BEARER]", redacted)
        self.assertIn("[REDACTED_BASIC]", redacted)

    def test_signed_url_token_redaction(self):
        sample = (
            "V/Player: Playing stream: https://cdn.stream.com/video.mp4?token=secretStreamToken123&expires=1799999999&sig=a8f9c0e2d1\n"
            "V/Player: Backup stream: https://video.host/stream/master.m3u8?h=hashValue9999&auth=authTicket\n"
        )
        redacted = sanitize_log_text(sample)
        self.assertNotIn("secretStreamToken123", redacted)
        self.assertNotIn("1799999999", redacted)
        self.assertNotIn("a8f9c0e2d1", redacted)
        self.assertNotIn("hashValue9999", redacted)
        self.assertNotIn("authTicket", redacted)
        self.assertIn("token=[REDACTED_QUERY_PARAM]", redacted)
        self.assertIn("expires=[REDACTED_QUERY_PARAM]", redacted)
        self.assertIn("sig=[REDACTED_QUERY_PARAM]", redacted)
        self.assertIn("h=[REDACTED_QUERY_PARAM]", redacted)
        self.assertIn("auth=[REDACTED_QUERY_PARAM]", redacted)

    def test_ipv4_and_ipv6_redaction(self):
        sample = (
            "D/DNS: Resolved mirror to 118.98.2.1\n"
            "D/OkHttp: Connecting to 192.168.1.100:443\n"
            "D/OkHttp: Connecting to 2001:0db8:85a3:0000:0000:8a2e:0370:7334\n"
            "Version tag Kaziyami 1.4.1 should NOT be redacted as IP!\n"
        )
        redacted = sanitize_log_text(sample)
        self.assertNotIn("118.98.2.1", redacted)
        self.assertNotIn("192.168.1.100", redacted)
        self.assertNotIn("2001:0db8:85a3:0000:0000:8a2e:0370:7334", redacted)
        self.assertIn("[REDACTED_IP]", redacted)
        self.assertIn("[REDACTED_IPV6]", redacted)
        # Verify 3-part version string preserved
        self.assertIn("1.4.1", redacted)

    def test_generate_bundle_integration(self):
        raw_log = "Error connecting to 10.0.2.15 with Cookie: foo=bar"
        bundle = generate_bundle(
            repo_dir=REPO_ROOT,
            app_version="Aniyomi 0.15.3",
            android_version="Android 14",
            raw_logs=raw_log
        )
        self.assertIn("KAZIYAMI DIAGNOSTIC BUNDLE", bundle)
        self.assertIn("Aniyomi 0.15.3", bundle)
        self.assertIn("Android 14", bundle)
        self.assertNotIn("10.0.2.15", bundle)
        self.assertNotIn("foo=bar", bundle)
        self.assertIn("[REDACTED_IP]", bundle)
        self.assertIn("[REDACTED_COOKIE]", bundle)

if __name__ == "__main__":
    unittest.main()
