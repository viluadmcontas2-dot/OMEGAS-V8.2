#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
matrix = json.loads((ROOT / "tests/fixtures/omegas-autocal-progbase-parity-v1.json").read_text(encoding="utf-8"))

assert matrix["schema"] == "omegas.autocal.progbase-parity.v1"
rows = {row["behavior"]: row for row in matrix["classifications"]}

assert rows["RunPoint/AGORA XY"]["classification"] == "MATCH"
assert rows["LEVELS RAW routing"]["classification"] == "INTENTIONAL_IMPROVEMENT"
assert rows["PetrolCurve/GasCurve"]["dimensions"]["field_identity"] == "MATCH"
assert rows["PetrolCurve/GasCurve"]["dimensions"]["refresh_policy"] == "MATCH_AFTER_FIX"
assert rows["CurrentBand"]["classification"] == "MATCH"
assert rows["NUM_BUF maturity / acquisition activity"]["dimensions"]["petrol_probe"] == "MATCH_AFTER_FIX"
assert rows["PollingPetrol/PollingGas"]["classification"] == "INTENTIONAL_IMPROVEMENT"
assert rows["Enable/disable acquisition"]["classification"] == "MATCH"
assert rows["0x0165 subindex semantics"]["classification"] == "MATCH"
assert rows["0x0165 subindex semantics"]["dimensions"]["original"] == "PROVEN_FROM_PROGBASE_DFM"

decision = matrix["architecture_decision"]
assert decision["preferred"] == "GROUPED_INCREMENTAL_REFRESH_ON_EXISTING_MP48_SERIAL_AUTHORITY"
for forbidden in [
    "copy Delphi timers literally",
    "add JS/native screen polling loops",
    "second serial owner",
    "periodic full READ_ONLY_FIELDS snapshot every ~2s",
    "route live AGORA through slow AutoCal snapshot",
]:
    assert forbidden in decision["rejected"]

# Cross-check only source facts used to classify the current implementation.
monitor = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")
cockpit = (ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/assets/ui/app.js").read_text(encoding="utf-8")
scheduler = (ROOT / "app/src/main/assets/ui/core/scheduler.js").read_text(encoding="utf-8")
projection = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")
protocol = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text(encoding="utf-8")
engine = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt").read_text(encoding="utf-8")

assert "AutoCalProtocol.NUM_BUF_UPD_GAS" in monitor
assert "probeMaturityCounters" in monitor
assert "refreshAcquisitionGroup" in monitor
assert "AutoCalProtocol.NUM_BUF_UPD_PETR" in monitor
assert "AutoCalProtocol.NUM_BUF_UPD_GAS" in monitor
assert "AutoCalProtocol.ACQUIRED_ZONES_PETROL" in monitor
assert "AutoCalProtocol.ACQUIRED_ZONES_GAS" in monitor
assert "addHook('fast'" in cockpit
assert "renderLiveCursor()" in cockpit
assert "LEVELS RAW" not in cockpit
assert "autocalLiveLevel" not in cockpit
assert "level_raw" not in cockpit
assert "levelsRaw" not in projection
assert "telemetryStatus" not in projection
assert "telemetryStore.liveJson()" not in bridge
assert "MNFLD_PRESS_THD" in cockpit
assert "currentBand(snapshot = {}, live = {})" in cockpit
assert "data-autocal-current-band" in cockpit
assert "intervalMs: 200" in app
assert "setCadenceMs(route === 'autocal' ? 50 : 200)" in app
assert "setCadenceMs(intervalMs)" in scheduler
assert "statusElapsedMs" in scheduler
assert "contextElapsedMs" in scheduler
assert 'val MNFLD_PRESS_THD = Field("MNFLD_PRESS_THD", 0x014C' in protocol
assert "if (queued.telemetryAfter" in engine
assert "pollTelemetry()" in engine

print("OMEGAS_AUTOCAL_PARITY_MATRIX=PASS classifications=%d" % len(rows))
