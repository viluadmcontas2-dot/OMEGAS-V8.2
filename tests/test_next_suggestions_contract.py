#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADAPTER = ROOT / "app/src/main/java/com/omegas/prohub/calibration/AdvisorSuggestionAdapterV7.kt"
RUNTIME = ROOT / "app/src/main/java/com/omegas/v7/runtime/V7SessionRuntime.kt"
COORDINATOR = ROOT / "app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt"
PROJECTION = ROOT / "app/src/main/java/com/omegas/prohub/calibration/SuggestionUiProjection.kt"
NEXT = ROOT / "app/src/main/assets/ui-next"
BOOT = NEXT / "bootstrap.js"
QUEUE = NEXT / "components/suggestion-queue.js"


class NextSuggestionsContractTest(unittest.TestCase):
    def test_advisor_id_is_stable_by_target_and_revision_not_magnitude(self):
        source = ADAPTER.read_text(encoding="utf-8")
        self.assertIn("private fun stableId", source)
        self.assertIn("calibration.revision.curveK", source)
        self.assertIn("calibration.revision.mapK", source)
        self.assertIn("physicalTarget", source)
        self.assertIn('MessageDigest.getInstance("SHA-256")', source)
        self.assertNotIn("UUID.randomUUID", source)

    def test_refresh_preserves_existing_entity_instead_of_silent_delete(self):
        source = RUNTIME.read_text(encoding="utf-8")
        self.assertIn("createdAtMs = existing.createdAtMs", source)
        self.assertIn("Sugestão continua registrada", source)
        self.assertIn("SuggestionLifecycleV7.OBSERVING", source)
        self.assertIn("SuggestionLifecycleV7.SUPERSEDED", source)

    def test_applied_requires_real_calibration_readback(self):
        coordinator = COORDINATOR.read_text(encoding="utf-8")
        runtime = RUNTIME.read_text(encoding="utf-8")
        self.assertIn("CONFIRMED_MANUAL_WRITE_READBACK", coordinator)
        self.assertIn("suggestionMatchesCalibration", coordinator)
        self.assertIn("Writer confirmou sem readback da ECU", runtime)
        self.assertIn("SuggestionLifecycleV7.APPLIED", runtime)

    def test_human_lifecycle_projection_is_explicit(self):
        source = PROJECTION.read_text(encoding="utf-8")
        for state in ["PENDENTE", "OBSERVANDO", "APLICADA", "SUPERADA"]:
            self.assertIn(state, source)
        self.assertIn('put("automaticWrite", false)', source)
        self.assertIn('put("humanSelectionRequired", true)', source)

    def test_queue_opens_existing_editors_and_never_writes(self):
        boot = BOOT.read_text(encoding="utf-8")
        queue = QUEUE.read_text(encoding="utf-8")
        self.assertIn("selectReadySuggestions", boot)
        self.assertIn("openSuggestion", boot)
        self.assertIn("router.navigate('mapa-k')", boot)
        self.assertIn("router.navigate('curva-k')", boot)
        self.assertIn("Selecionar prontas", queue)
        for token in ["startBatchWrite(", "startKFactorWrite(", "protocolTransaction(", "OmegasNative"]:
            self.assertNotIn(token, boot)
            self.assertNotIn(token, queue)

    def test_suggestions_use_global_scheduler_not_route_timer(self):
        boot = BOOT.read_text(encoding="utf-8")
        self.assertIn("scheduler.addHook('suggestions', loadSuggestions, 2000)", boot)
        for path in NEXT.rglob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("setInterval(", source, str(path))
            self.assertNotIn("setTimeout(", source, str(path))


if __name__ == "__main__":
    unittest.main()
