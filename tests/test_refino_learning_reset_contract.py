#!/usr/bin/env python3
"""Reinício manual do GNV: local, com interlocks; nenhuma transação com a ECU."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
svc=(root/"app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text()
bridge=(root/"app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text()
reset=svc[svc.index("fun resetGasEvidence(): String"):svc.index("fun equivalenceResultJson(): String")]
for protected in ("usb.connected", "kWriter.isBusy()", "kFactor.isBusy()", "SerialWriteGuard.shared.isHeld()"):
 assert protected in reset, protected
for step in ("equivalence.resetGas(reason)", "equivalenceRuntime.onGasReset(reason, equivalencePhases)", "refinementJournal.interrupt(reason)"):
 assert step in reset, step
for forbidden in ("writeCurve(", "writeMap(", "transaction(", "resetCurve(", "prepareNativeAction", "equivalence.resetPetrol", "references.freeze", "references.restore"):
 assert forbidden not in reset, forbidden
assert 'put("ok", !failed)' in reset, "não promete sucesso se invalidação falhar"
method=bridge[bridge.index("fun resetGasEvidence(): String"):bridge.index("/** Desfazer do congelamento",bridge.index("fun resetGasEvidence(): String"))]
assert "noLocalControlFailure()" in method
assert "service.resetGasEvidence().also { invalidateAnalysis() }" in method
print("REFINO_LOCAL_GNV_RESET=PASS")
