from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORE = (ROOT / "app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt").read_text("utf-8")
MP48 = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt").read_text("utf-8")

for marker in (
    "lastTelemetryCapturedElapsedMs",
    "physicalFrameRevision(payload)",
    "capturedElapsedMs <= lastTelemetryCapturedElapsedMs",
    "telemetry = copyObject(payload)",
    '.put("captured_elapsed_ms", payload.getLong("captured_elapsed_ms"))',
):
    assert marker in STORE, marker

assert "merge(telemetry, payload)" not in STORE
assert "merge(telemetry, live)" not in STORE
for physical in ('"captured_elapsed_ms"', '"rpm"', '"petrol_ms"', '"load_bar"'):
    assert physical in MP48, physical

print("TELEMETRY_ATOMIC_PHYSICAL_REVISION=PASS")
