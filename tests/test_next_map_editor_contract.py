#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
BOOT = NEXT / "bootstrap.js"
STORE = NEXT / "core/store.js"
EDITOR = NEXT / "components/map-k-editor.js"
CSS = NEXT / "components/map-k-editor.css"
APRENDER = NEXT / "routes/aprender.js"
MAP_ROUTE = NEXT / "routes/mapa-k.js"


class NextMapEditorContractTest(unittest.TestCase):
    def test_one_official_editor_component_is_reused(self):
        self.assertEqual(1, len(list(NEXT.rglob("map-k-editor.js"))))
        aprender = APRENDER.read_text(encoding="utf-8")
        mapa = MAP_ROUTE.read_text(encoding="utf-8")
        self.assertIn("renderMapKEditor", aprender)
        self.assertIn("renderMapKEditor", mapa)

    def test_editor_never_touches_native_bridge_usb_or_writer(self):
        source = EDITOR.read_text(encoding="utf-8")
        for token in ["OmegasNative", "OmegasV7", "UsbSerialManager", "protocolTransaction", "writeK", "ACK"]:
            self.assertNotIn(token, source)
        self.assertIn("actions.onReview", source)
        self.assertIn("actions.onRead", source)

    def test_read_is_required_before_selection(self):
        editor = EDITOR.read_text(encoding="utf-8")
        self.assertIn("const ready = state?.state === 'READY'", editor)
        self.assertIn("Leia a ECU antes de selecionar", editor)
        boot = BOOT.read_text(encoding="utf-8")
        self.assertIn("if (mapK.state !== UI_STATE.READY) return", boot)

    def test_current_cell_and_selection_have_different_styles(self):
        css = CSS.read_text(encoding="utf-8")
        for token in [".k-cell.current::after", ".k-cell.selected", ".k-cell.selected.current::after"]:
            self.assertIn(token, css)

    def test_store_owns_selection_and_context_editor(self):
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("contextualEditor", store)
        self.assertIn("MAP_K_STATE", store)
        boot = BOOT.read_text(encoding="utf-8")
        self.assertIn("store.get().mapK.selection", boot)
        self.assertIn("CONTEXT_EDITOR_CHANGED", boot)

    def test_review_cannot_write_in_simulated_surface(self):
        boot = BOOT.read_text(encoding="utf-8")
        self.assertIn("Nenhuma escrita foi enviada", boot)
        self.assertIn("Confirmar escrita — indisponível no simulador", boot)
        self.assertNotIn("startBatchWrite(", boot)
        self.assertNotIn("protocolTransaction(", boot)

    def test_human_selection_supports_144_without_technical_row(self):
        editor = EDITOR.read_text(encoding="utf-8")
        self.assertIn("for (let row = 0; row < 12; row += 1)", editor)
        self.assertIn("for (let column = 0; column < 12; column += 1)", editor)
        self.assertIn("A linha técnica não pertence a esta grade gravável", editor)
        self.assertIn("if (next.length > 144) return", BOOT.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
