#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
STORE = NEXT / "core/store.js"
LANG = NEXT / "core/action-language.js"
MAP = NEXT / "components/map-k-editor.js"
CURVE = NEXT / "routes/curva-k.js"


class NextFeedbackContractTest(unittest.TestCase):
    def test_recoverable_failure_preserves_calibration_draft(self):
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("preserveDraft", store)
        self.assertIn("next.selection = current.selection", store)
        self.assertIn("next.proposal = current.proposal", store)
        self.assertIn("next.prepared = current.prepared", store)
        self.assertIn("draftBlocked", store)
        self.assertIn("releia a ECU antes de confirmar", store)

    def test_loss_of_telemetry_marks_stale_before_confirmation(self):
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("TELEMETRY_INVALIDATED", store)
        self.assertIn("UI_STATE.STALE", store)
        self.assertIn("confirmationBlockedReason", store)
        self.assertIn("releia a ECU", store)

    def test_epoch_revalidates_predictor_and_suggestions_without_erasing_history(self):
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("predictor: Object.freeze({ ...state.predictor, state: UI_STATE.STALE", store)
        self.assertIn("suggestions: Object.freeze({ ...state.suggestions, state: UI_STATE.STALE", store)
        self.assertIn("sem apagar o histórico", store)

    def test_blocked_drafts_remain_visible_in_map_and_curve(self):
        map_source = MAP.read_text(encoding="utf-8")
        curve_source = CURVE.read_text(encoding="utf-8")
        self.assertIn("Rascunho preservado • confirmação bloqueada", map_source)
        self.assertIn("Dado stale — rascunho mantido", map_source)
        self.assertIn("Rascunho preservado • confirmação bloqueada", curve_source)
        self.assertIn("STALE — releitura obrigatória", curve_source)

    def test_operation_language_is_canonical_and_critical_label_names_ecu(self):
        source = LANG.read_text(encoding="utf-8")
        for token in ["'Ler'", "'Reler ECU'", "'Preparar'", "'Revisar'", "'Confirmar intenção'", "'Gravar na ECU'", "'Validar readback'"]:
            self.assertIn(token, source)
        self.assertIn("criticalWriteLabel", source)
        self.assertNotIn("Aplicar automaticamente", source)

    def test_no_accidental_confirmation_by_enter_resize_or_swipe(self):
        for path in NEXT.rglob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("keydown", source, str(path))
            self.assertNotIn("keyup", source, str(path))
            self.assertNotIn("onresize", source, str(path))
            self.assertNotIn("touchend", source, str(path))
            self.assertNotIn("swipe", source.lower(), str(path))

    def test_ui_does_not_offer_fake_undo_for_ecu(self):
        for path in NEXT.rglob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("Desfazer gravação", source, str(path))
            self.assertNotIn("rollback automático", source.lower(), str(path))

    def test_no_independent_spinner_timers(self):
        for path in NEXT.rglob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("setInterval(", source, str(path))
            self.assertNotIn("setTimeout(", source, str(path))
            self.assertNotIn("spinner", source.lower(), str(path))


if __name__ == "__main__":
    unittest.main()
