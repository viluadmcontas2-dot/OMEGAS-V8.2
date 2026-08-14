#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
STORE = NEXT / "core/store.js"
ROUTER = NEXT / "core/router.js"
SCHEDULER = NEXT / "core/scheduler.js"


class NextUiAuthorityContractTest(unittest.TestCase):
    def test_exactly_one_core_store_router_and_scheduler(self):
        self.assertTrue(STORE.is_file())
        self.assertTrue(ROUTER.is_file())
        self.assertTrue(SCHEDULER.is_file())
        self.assertEqual(1, len(list(NEXT.rglob("store.js"))))
        self.assertEqual(1, len(list(NEXT.rglob("router.js"))))
        self.assertEqual(1, len(list(NEXT.rglob("scheduler.js"))))

    def test_router_has_exactly_six_human_destinations(self):
        source = ROUTER.read_text(encoding="utf-8")
        expected = [
            ("agora", "Agora"),
            ("aprender", "Aprender"),
            ("predictor", "Predictor"),
            ("mapa-k", "Mapa K"),
            ("curva-k", "Curva K"),
            ("obd", "OBD"),
        ]
        for route_id, label in expected:
            self.assertIn(f"id: '{route_id}', label: '{label}'", source)
        self.assertEqual(6, len(re.findall(r"Object\.freeze\(\{ id: '[^']+', label: '[^']+' \}\)", source)))

    def test_no_independent_interval_or_timeout_scheduler_in_next_tree(self):
        for path in NEXT.rglob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("setInterval(", source, str(path))
            self.assertNotIn("setTimeout(", source, str(path))
        scheduler = SCHEDULER.read_text(encoding="utf-8")
        self.assertIn("requestAnimationFrame", scheduler)
        self.assertIn("addHook", scheduler)

    def test_navigation_does_not_call_native_writers_or_reads(self):
        source = ROUTER.read_text(encoding="utf-8")
        forbidden = [
            "OmegasNative",
            "OmegasV7",
            "OmegasAutoCal",
            "write",
            "readKMap",
            "protocol",
            "usb",
        ]
        for token in forbidden:
            self.assertNotIn(token, source)

    def test_store_invalidates_edit_context_on_epoch_change(self):
        source = STORE.read_text(encoding="utf-8")
        self.assertIn("CALIBRATION_EPOCH_CHANGED", source)
        self.assertIn("selection: []", source)
        self.assertIn("prepared: []", source)
        self.assertIn("cellContext: null", source)


if __name__ == "__main__":
    unittest.main()
