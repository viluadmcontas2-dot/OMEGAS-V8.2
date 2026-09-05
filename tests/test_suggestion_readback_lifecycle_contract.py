#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt").read_text("utf-8")
COORDINATOR = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt").read_text("utf-8")
ACCESS = (ROOT / "app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt").read_text("utf-8")

# A interface nunca marca uma escrita como confirmada antes de ACK/readback.
assert 'val confirmed = status.optString("state") == "BATCH_CONFIRMED" && details.optBoolean("readbackValid", false)' in BRIDGE
assert 'if (confirmed)' in BRIDGE
assert 'service.v7ReconcileConfirmedManualWrite("CURVE_K")' in BRIDGE
assert 'val fullyConfirmed = failure == null && completedCells == plan.totalCells' in BRIDGE
assert 'if (fullyConfirmed)' in BRIDGE
assert 'service.v7ReconcileConfirmedManualWrite("MAP_K")' in BRIDGE
assert '.put("readbackValid", true)' in BRIDGE

# A reconciliação ainda faz releitura fresca da ECU. O Blue bloqueia a antiga
# aplicação automática/delegada de sugestões; readback serve como fronteira do
# estado de calibração, não como permissão para um motor legado.
assert 'fun reconcileConfirmedManualWrite(' in COORDINATOR
assert 'val sync = synchronizedFromEcu(activeFileName)' in COORDINATOR
assert 'CONFIRMED_MANUAL_WRITE_READBACK' in COORDINATOR
assert 'v7ApplySuggestion' in ACCESS
assert 'Aplicação de sugestão legada bloqueada no OMEGAS Blue' in ACCESS
assert 'decisionAuthority' in ACCESS
assert 'BLUE_CAUSAL_ENGINE' in ACCESS

print("SUGGESTION_READBACK_LIFECYCLE_CONTRACT=PASS")
