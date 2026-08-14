#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
INDEX = NEXT / "index.html"
CSS = NEXT / "styles.css"
APP = NEXT / "app.js"
SIM = NEXT / "adapters/simulated.js"


class NextShellAgoraContractTest(unittest.TestCase):
    def test_next_is_parallel_and_does_not_modify_legacy_ui_path(self):
        self.assertTrue(NEXT.is_dir())
        self.assertTrue((ROOT / "app/src/main/assets/ui").is_dir())
        self.assertTrue(INDEX.is_file())

    def test_automotive_touch_targets_are_explicit(self):
        css = CSS.read_text(encoding="utf-8")
        self.assertIn("--target: 48px", css)
        self.assertIn("--primary-target: 56px", css)
        self.assertIn("min-height: var(--target)", css)
        self.assertIn("min-height: var(--primary-target)", css)

    def test_shell_has_one_workspace_one_navigation_and_secondary_more(self):
        html = INDEX.read_text(encoding="utf-8")
        self.assertEqual(1, html.count('id="workspace"'))
        self.assertEqual(1, html.count('id="main-nav"'))
        self.assertEqual(1, html.count('id="settings-button"'))
        self.assertNotIn("UsbSerialManager", html)
        self.assertNotIn("protocol", html.lower())

    def test_agora_prioritizes_rpm_petrol_map_and_gas_diagnostic(self):
        source = APP.read_text(encoding="utf-8")
        for text in ["RPM", "Petrol Inj.", "MAP", "Gas Inj.", "Aprendendo agora"]:
            self.assertIn(text, source)
        self.assertIn("diagnóstico • não é referência", source)
        self.assertIn("não seleciona escrita", source)

    def test_aprender_exposes_three_distinct_semantic_origins(self):
        source = APP.read_text(encoding="utf-8")
        self.assertIn("AGORA", source)
        self.assertIn("REFERÊNCIA", source)
        self.assertIn("NO GNV", source)
        self.assertIn("Por que estes números são comparáveis?", source)
        simulated = SIM.read_text(encoding="utf-8")
        self.assertIn("value: 4.50", simulated)
        self.assertIn("value: 5.18", simulated)
        self.assertIn("value: 5.72", simulated)

    def test_no_independent_timers_or_mutation_observer(self):
        for path in NEXT.rglob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("setInterval(", source, str(path))
            self.assertNotIn("setTimeout(", source, str(path))
            self.assertNotIn("MutationObserver", source, str(path))
        app = APP.read_text(encoding="utf-8")
        self.assertIn("scheduler.addHook('fast-telemetry'", app)
        self.assertIn("scheduler.addHook('learning-status'", app)

    def test_main_navigation_is_not_a_seventh_more_destination(self):
        router = (NEXT / "core/router.js").read_text(encoding="utf-8")
        self.assertNotRegex(router, re.compile(r"id: 'mais'"))
        html = INDEX.read_text(encoding="utf-8")
        self.assertIn('id="settings-button"', html)


if __name__ == "__main__":
    unittest.main()
