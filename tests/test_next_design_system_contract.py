#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
DESIGN = NEXT / "components/design-system.css"
INDEX = NEXT / "index.html"
CATALOG = NEXT / "design-system.html"
ROUTES = NEXT / "routes"


class NextDesignSystemContractTest(unittest.TestCase):
    def test_semantic_tokens_cover_color_type_spacing_and_targets(self):
        source = DESIGN.read_text(encoding="utf-8")
        for token in [
            "--status-success", "--status-attention", "--status-danger", "--status-info",
            "--type-value-hero", "--type-value", "--type-title", "--type-control", "--type-context",
            "--grid-gap", "--control-radius", "--icon-critical-min",
            "min-height: var(--target)", "min-height: var(--primary-target)",
        ]:
            self.assertIn(token, source)

    def test_action_variants_and_control_states_are_defined(self):
        source = DESIGN.read_text(encoding="utf-8")
        for token in [
            ".primary-action", ".secondary-action", ".quiet-action", ".danger-action",
            'button[aria-pressed="true"]', 'button[aria-busy="true"]',
            ".control-success", ".control-failure", "button:disabled",
        ]:
            self.assertIn(token, source)

    def test_operational_k_values_do_not_fall_back_to_microtext(self):
        source = DESIGN.read_text(encoding="utf-8")
        self.assertIn(".k-cell", source)
        self.assertIn("clamp(12px", source)
        self.assertIn(".curve-point small", source)
        self.assertIn("font-size: max(var(--type-detail), 11px)", source)

    def test_state_semantics_do_not_depend_only_on_color(self):
        source = DESIGN.read_text(encoding="utf-8")
        self.assertIn(".state-chip::before", source)
        self.assertIn("content: '✓'", source)
        self.assertIn("content: '!'", source)
        self.assertIn("content: '×'", source)

    def test_static_catalog_exists_without_native_or_writer(self):
        catalog = CATALOG.read_text(encoding="utf-8")
        self.assertIn("Catálogo NEXT", catalog)
        self.assertIn("Desabilitado — leia a ECU", catalog)
        self.assertIn("Confirmar intenção manual", catalog)
        for token in ["OmegasNative", "protocolTransaction", "startBatchWrite", "UsbSerialManager"]:
            self.assertNotIn(token, catalog)

    def test_production_routes_do_not_define_inline_css_blocks(self):
        for path in ROUTES.glob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("<style", source, str(path))
            self.assertNotIn("style=\"", source, str(path))

    def test_design_system_is_loaded_before_adaptive_overrides(self):
        html = INDEX.read_text(encoding="utf-8")
        design_pos = html.index("components/design-system.css")
        adaptive_pos = html.index("components/adaptive.css")
        self.assertLess(design_pos, adaptive_pos)


if __name__ == "__main__":
    unittest.main()
