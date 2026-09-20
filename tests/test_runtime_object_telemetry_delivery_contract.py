from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt").read_text("utf-8")
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text("utf-8")
STORE = (ROOT / "app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt").read_text("utf-8")

consume_start = RUNTIME.index("    private fun consumeTelemetry(")
consume_end = RUNTIME.index("\n    private fun publishLearningState", consume_start)
consume = RUNTIME[consume_start:consume_end]

service_start = SERVICE.index("    private fun consumeEngineEvent(")
service_end = SERVICE.index("\n    private fun consumeGpsUpdate", service_start)
service_consume = SERVICE[service_start:service_end]

assert "private val onTelemetryEvent: (JSONObject) -> Unit" in RUNTIME
assert "onTelemetryEvent(root)" in consume
assert "val rawEvent = root.toString()" not in consume
assert "private fun consumeEngineEvent(root: JSONObject)" in SERVICE
assert "JSONObject(raw)" not in service_consume
assert "fun updateFromEngineEvent(root: JSONObject): JSONObject?" in STORE
assert "updateFromEngineEvent(JSONObject(raw))" in STORE

print("RUNTIME_OBJECT_TELEMETRY_DELIVERY=PASS")
