from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORE = (ROOT / "app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt").read_text("utf-8")
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt").read_text("utf-8")
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text("utf-8")

assert "updateFullSnapshot" not in STORE
assert "private var fullSnapshot" not in STORE
assert "fun fullJson(" not in STORE
assert "service.telemetryStore.liveJson()" in BRIDGE
assert "runtime.fullSnapshotJson()" in SERVICE

print("TELEMETRY_SINGLE_SNAPSHOT_AUTHORITY=PASS")
