#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt").read_text("utf-8")
COORDINATOR = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt").read_text("utf-8")
ACCESS = (ROOT / "app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt").read_text("utf-8")

# A interface nunca marca sugestão aplicada no clique/preparo. O bridge só chama
# reconciliação depois de o writer já ter confirmado readback real.
assert 'val confirmed = status.optString("state") == "BATCH_CONFIRMED" && details.optBoolean("readbackValid", false)' in BRIDGE
assert 'if (confirmed)' in BRIDGE
assert 'service.v7ReconcileConfirmedManualWrite("CURVE_K")' in BRIDGE
assert 'val fullyConfirmed = failure == null && completedCells == plan.totalCells' in BRIDGE
assert 'if (fullyConfirmed)' in BRIDGE
assert 'service.v7ReconcileConfirmedManualWrite("MAP_K")' in BRIDGE
assert '.put("readbackValid", true)' in BRIDGE

# A reconciliação faz uma releitura fresca da ECU e só então compara os alvos
# exatos persistidos com a calibração efetivamente lida.
assert 'fun reconcileConfirmedManualWrite(' in COORDINATOR
assert 'val sync = synchronizedFromEcu(activeFileName)' in COORDINATOR
assert 'suggestionMatchesCalibration(it, actual)' in COORDINATOR
assert 'SuggestionLifecycleV7.APPLIED' in COORDINATOR
assert 'abs(actual - change.after) <= 1e-9' in COORDINATOR
assert '== change.after' in COORDINATOR
assert 'CONFIRMED_MANUAL_WRITE_READBACK' in COORDINATOR

# Falha nessa reconciliação auxiliar não converte uma escrita já confirmada em
# falha e, sobretudo, não inventa APPLIED.
assert 'suggestionReconciliation' in BRIDGE
assert 'Falha ao reconciliar sugestões após readback' in ACCESS

print("SUGGESTION_READBACK_LIFECYCLE_CONTRACT=PASS")
