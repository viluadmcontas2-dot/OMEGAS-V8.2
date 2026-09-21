#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
matrix = json.loads((ROOT / "tests/fixtures/omegas-autocal-progbase-parity-v1.json").read_text(encoding="utf-8"))

assert matrix["schema"] == "omegas.autocal.progbase-parity.v1"
rows = {row["behavior"]: row for row in matrix["classifications"]}

assert rows["RunPoint/AGORA XY"]["classification"] == "MATCH"
assert rows["LEVELS RAW in AutoCal live strip"]["classification"] == "MATCH"
assert rows["PetrolCurve/GasCurve"]["dimensions"]["field_identity"] == "MATCH"
assert rows["PetrolCurve/GasCurve"]["dimensions"]["refresh_policy"] == "WRONG"
assert rows["CurrentBand"]["classification"] == "MATCH"
assert rows["NUM_BUF maturity / acquisition activity"]["dimensions"]["petrol_probe"] == "MISSING"
assert rows["PollingPetrol/PollingGas"]["classification"] == "INTENTIONAL_IMPROVEMENT"
assert rows["Enable/disable acquisition"]["classification"] == "MATCH"
assert rows["0x0165 subindex semantics"]["classification"] == "INCONCLUSIVE"

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
protocol = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text(encoding="utf-8")
engine = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt").read_text(encoding="utf-8")

assert "snapshot completo só é lido por evento" in monitor
assert "AutoCalProtocol.NUM_BUF_UPD_GAS" in monitor
assert "probeMaturityCounters" in monitor
assert "AutoCalProtocol.NUM_BUF_UPD_PETR" not in monitor.split("private fun probeMaturityCounters",1)[1].split("private fun readFullSnapshot",1)[0]
assert "addHook('fast'" in cockpit
assert "renderLiveCursor()" in cockpit
assert "const levelRaw = finite(live.level_raw ?? live.levelRaw);" in cockpit
assert "const levelRaw = finite(projection?.levelsRaw);" not in cockpit
assert "MNFLD_PRESS_THD" in cockpit
assert "currentBand(snapshot = {}, live = {})" in cockpit
assert "data-autocal-current-band" in cockpit
assert "intervalMs: 200" in app
assert "this.tick % 10 === 0" in scheduler
assert '.put("levelsRaw", levelsRaw(telemetryStatus, currentSession))' in projection
assert 'val MNFLD_PRESS_THD = Field("MNFLD_PRESS_THD", 0x014C' in protocol
assert "if (queued.telemetryAfter" in engine
assert "pollTelemetry()" in engine

print("OMEGAS_AUTOCAL_PARITY_MATRIX=PASS classifications=%d" % len(rows))
