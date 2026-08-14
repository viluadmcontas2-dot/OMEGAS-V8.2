#!/usr/bin/env python3
from __future__ import annotations

import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
SCOPE = NEXT / "netlify-scope.json"
PACKAGER = ROOT / "tools/package_next_netlify.py"
HEADERS = NEXT / "_headers"
MAIN_ACTIVITY = ROOT / "app/src/main/java/com/omegas/prohub/MainActivity.kt"


class NextNetlifyScopeContractTest(unittest.TestCase):
    def test_scope_is_ui_only_and_forbids_native_sensitive_artifacts(self):
        scope = json.loads(SCOPE.read_text(encoding="utf-8"))
        self.assertEqual("app/src/main/assets/ui-next", scope["sourceRoot"])
        self.assertEqual("UI_AND_FICTIONAL_FIXTURES_ONLY", scope["servedContent"])
        self.assertFalse(scope["realVehicleData"])
        self.assertFalse(scope["androidNativeCode"])
        self.assertFalse(scope["usbProtocol"])
        self.assertFalse(scope["ecuWriter"])
        for suffix in [".kt", ".java", ".apk", ".aab", ".jks", ".keystore", ".log"]:
            self.assertIn(suffix, scope["forbiddenExtensions"])
        self.assertEqual("PACKAGE_DIRECTORY_ONLY_NOT_REPO_ROOT", scope["deploymentMode"])

    def test_packager_copies_only_ui_next_and_blocks_forbidden_files(self):
        source = PACKAGER.read_text(encoding="utf-8")
        self.assertIn('SOURCE = ROOT / "app/src/main/assets/ui-next"', source)
        self.assertIn("forbidden-extension", source)
        self.assertIn("forbidden-name", source)
        self.assertIn("not-allowlisted", source)
        self.assertIn("shutil.copytree(SOURCE, output)", source)
        self.assertNotIn("netlify deploy", source.lower())
        self.assertNotIn("requests.", source)
        self.assertNotIn("subprocess", source)

    def test_netlify_cache_never_serves_stale_index_as_immutable(self):
        headers = HEADERS.read_text(encoding="utf-8")
        self.assertIn("/index.html", headers)
        self.assertIn("Cache-Control: no-store, no-cache, must-revalidate", headers)
        self.assertIn("/*.js", headers)
        self.assertIn("/*.css", headers)
        self.assertIn("Permissions-Policy: geolocation=(), camera=(), microphone=(), usb=()", headers)

    def test_android_webview_already_clears_cache_before_asset_load(self):
        source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        self.assertIn("webView.clearCache(false)", source)
        self.assertIn("WebSettings.LOAD_DEFAULT", source)

    def test_ui_next_tree_contains_no_native_source_or_real_logs(self):
        forbidden = {".kt", ".java", ".apk", ".aab", ".jks", ".keystore", ".log", ".pem", ".key"}
        offenders = [str(path.relative_to(NEXT)) for path in NEXT.rglob("*") if path.is_file() and path.suffix.lower() in forbidden]
        self.assertEqual([], offenders)


if __name__ == "__main__":
    unittest.main()
