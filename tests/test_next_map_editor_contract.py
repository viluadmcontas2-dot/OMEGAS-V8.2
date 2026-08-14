#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
APP = NEXT / "app.js"
STORE = NEXT / "core/store.js"
EDITOR = NEXT / "components/map-k-editor.js"
CSS = NEXT / "components/map-k-editor.css"


class NextMapEditorContractTest(unittest.TestCase):
    def test_one_official_editor_component_is_reused(self):
        self.assertEqual(1, len(list(NEXT.rglob("map-k-editor.js"))))
        app = APP.read_text(encoding="utf-8")
        self.assertIn("renderLearningEditor", app)
        self.assertIn("renderMapaK", app)
        self.assertGreaterEqual(app.count("renderMapKEditor("), 2)

    def test_editor_never_touches_native_bridge_usb_or_writer(self):
        source = EDITOR.read_text(encoding="utf-8")
        for token in ["OmegasNative", "OmegasV7", "UsbSerialManager", "protocolTransaction", "writeK", "ACK"]:
            self.assertNotIn(token, source)
        self.assertIn("actions.onReview", source)
        self.assertIn("actions.onRead", source)

    def test_read_is_required_before_selection(self):
        source = EDITOR.read_text(encoding="utf-8")
        self.assertIn("const ready = state?.state === 'READY'", source)
        self.assertIn("Leia a ECU antes de selecionar", source)
        app = APP.read_text(encoding="utf-8")
        self.assertIn("if (mapK.state !== UI_STATE.READY) return", app)

    def test_current_cell_and_selection_have_different_styles(self):
        css = CSS.read_text(encoding="utf-8")
        self.assertIn(".k-cell.current::after", css)
        self.assertIn(".k-cell.selected", css)
        self.assertIn(".k-cell.selected.current::after", css)

    def test_store_owns_selection_and_context_editor(self):
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("contextualEditor", store)
        self.assertIn("MAP_K_STATE", store)
        app = APP.read_text(encoding="utf-8")
        self.assertIn("store.get().mapK.selection", app)
        self.assertIn("CONTEXT_EDITOR_CHANGED", app)

    def test_review_cannot_write_in_simulated_surface(self):
        app = APP.read_text(encoding="utf-8")
        self.assertIn("Nenhuma escrita foi enviada", app)
        self.assertIn("Confirmar escrita — indisponível no simulador", app)
        self.assertNotIn("startBatchWrite(", app)
        self.assertNotIn("protocolTransaction(", app)

    def test_human_selection_supports_144_without_technical_row(self):
        editor = EDITOR.read_text(encoding="utf-8")
        self.assertIn("for (let row = 0; row < 12; row += 1)", editor)
        self.assertIn("for (let column = 0; column < 12; column += 1)", editor)
        self.assertIn("A linha técnica não pertence a esta grade gravável", editor)
        app = APP.read_text(encoding="utf-8")
        self.assertIn("if (next.length > 144) return", app)


if __name__ == "__main__":
    unittest.main()
