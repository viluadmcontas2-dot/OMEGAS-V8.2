#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
ADAPTIVE = NEXT / "components/adaptive.css"
BOOT = NEXT / "bootstrap.js"
INDEX = NEXT / "index.html"


class NextAdaptiveContractTest(unittest.TestCase):
    def test_safe_areas_and_viewport_fit_are_explicit(self):
        css = ADAPTIVE.read_text(encoding="utf-8")
        html = INDEX.read_text(encoding="utf-8")
        for token in ["safe-area-inset-top", "safe-area-inset-right", "safe-area-inset-bottom", "safe-area-inset-left"]:
            self.assertIn(token, css)
        self.assertIn("viewport-fit=cover", html)

    def test_1280x644_and_1024x600_use_behavior_breakpoints(self):
        css = ADAPTIVE.read_text(encoding="utf-8")
        self.assertIn("@media (max-height: 660px) and (min-width: 900px)", css)
        self.assertIn("@media (max-width: 1100px) and (max-height: 660px)", css)
        self.assertNotIn("device-name", css)

    def test_touch_targets_never_shrink_below_48(self):
        css = ADAPTIVE.read_text(encoding="utf-8")
        self.assertIn("min-height: 50px", css)
        self.assertIn("min-height: 48px", css)
        self.assertNotIn("min-height: 4", css.replace("min-height: 48px", ""))

    def test_mobile_navigation_wraps_instead_of_horizontal_shell_scroll(self):
        css = ADAPTIVE.read_text(encoding="utf-8")
        self.assertIn("grid-template-columns: repeat(3, minmax(0, 1fr))", css)
        self.assertIn("overflow-x: hidden", css)

    def test_resize_has_no_js_authority_or_side_effect(self):
        for path in NEXT.rglob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("addEventListener('resize'", source, str(path))
            self.assertNotIn('addEventListener("resize"', source, str(path))
            self.assertNotIn("window.onresize", source, str(path))
            self.assertNotIn("location.reload", source, str(path))
        boot = BOOT.read_text(encoding="utf-8")
        self.assertNotIn("screen.width", boot)
        self.assertNotIn("innerWidth", boot)
        self.assertNotIn("innerHeight", boot)

    def test_horizontal_pan_is_limited_to_dense_surfaces(self):
        css = ADAPTIVE.read_text(encoding="utf-8")
        for token in [".k-grid", ".predictor-grid", ".curve-points"]:
            self.assertIn(token, css)
        self.assertIn(".workspace { overflow-x: hidden; }", css)


if __name__ == "__main__":
    unittest.main()
