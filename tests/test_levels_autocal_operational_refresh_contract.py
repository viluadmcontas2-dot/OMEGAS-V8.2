from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"
PROTOCOL = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt"
SCALE = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48TelemetryScale.kt"
DASHBOARD = ROOT / "app/src/main/assets/ui/screens/dashboard.js"

monitor = MONITOR.read_text(encoding="utf-8")
protocol = PROTOCOL.read_text(encoding="utf-8")
scale = SCALE.read_text(encoding="utf-8")
dashboard = DASHBOARD.read_text(encoding="utf-8")

section = monitor.split("private fun refreshAcquisitionGroup", 1)[1].split("private fun refreshReferenceGroup", 1)[0]
required = [
    "PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR",
    "PETR_INJ_TBUF_GAS_PREV", "MNFLD_PRESS_BUF_GAS_PREV",
    "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS",
    "ACQUIRED_ZONES_PETROL", "ACQUIRED_ZONES_GAS",
]
for name in required:
    assert f"AutoCalProtocol.{name}" in section, name
assert "AutoCalProtocol.MUL_ACT" not in section

assert '.put("level_raw", levelRaw)' in protocol
assert "level_percentage" not in protocol
assert "levelPercentage" not in scale
assert "LEVELS RAW" in dashboard
assert 'id="dashLevelsRaw"' in dashboard
assert "level_raw" in dashboard

print("LEVELS_AND_AUTOCAL_OPERATIONAL_REFRESH_CONTRACT=PASS")
