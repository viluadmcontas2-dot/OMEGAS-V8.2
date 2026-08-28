from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"
CANONICAL = ROOT / "app/src/main/java/com/omegas/prohub/telemetry/CanonicalEvidence.kt"
ADAPTIVE = ROOT / "app/src/main/java/com/omegas/prohub/adaptive/AdaptiveShadowObserver.kt"
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"


def test_052b_one_canonical_envelope_routes_existing_consumers_without_parallel_polling():
    runtime = RUNTIME.read_text(encoding="utf-8")
    canonical = CANONICAL.read_text(encoding="utf-8")
    adaptive = ADAPTIVE.read_text(encoding="utf-8")
    service = SERVICE.read_text(encoding="utf-8")

    assert runtime.count("CanonicalEvidence.from(") == 1
    assert "latestCanonicalEvidence.publish(evidence)" in runtime
    assert "projectTelemetryCompatibility(\n                    evidence = evidence" in runtime
    assert "learning.ingest(evidence.rawTelemetry, evidence.sampleDecision)" in runtime
    assert "adaptiveShadow.observe(evidence)" in runtime
    assert 'put("canonical_provenance", evidence.provenance.toJson())' in runtime

    # O Adaptive é só consumidor shadow do backbone atual.
    assert 'put("polling", false)' in adaptive
    assert 'put("writer", false)' in adaptive
    assert 'put("automatic_calibration", false)' in adaptive
    assert "MAY_TOUCH_MP48_SERIAL" not in adaptive

    # O recorder continua na trilha única de telemetria criada no 052A e recebe
    # a provenance serializada pela mesma UI projection, sem segundo polling/evento.
    assert 'sessionRecorder.record("telemetry", "mp48", live)' in service
    assert 'sessionRecorder.record("engine_event"' not in service

    assert 'const val SCHEMA = "omegas-canonical-evidence-v1"' in canonical
    assert 'const val ACQUISITION_SOURCE = "MP48_RESPONSE_DRIVEN"' in canonical
