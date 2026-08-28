from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_adaptive_boundary_is_read_only_before_physical_gate():
    source = read("app/src/main/java/com/omegas/prohub/model/ArchitectureContracts.kt")
    for forbidden_capability in (
        "MAY_TOUCH_MP48_SERIAL = false",
        "MAY_OWN_TELEMETRY_POLLING = false",
        "MAY_OWN_WRITER = false",
        "MAY_WRITE_ECU = false",
        "MAY_TRIGGER_AUTOMATCH = false",
        "MAY_START_CALIBRATION_AUTOMATICALLY = false",
    ):
        assert forbidden_capability in source


def test_adaptive_model_binding_consumes_physical_identity_without_transport_dependencies():
    source = read("app/src/main/java/com/omegas/prohub/adaptive/AdaptiveModelBinding.kt")
    assert "CalibrationIdentity" in source
    assert "identity.materiallyUsable()" in source
    assert "functionFingerprint" in source
    assert "geometryFingerprint" in source
    assert "mapHash" in source
    assert "curveAxisFingerprint" in source
    assert "curveFactorsFingerprint" in source
    assert "Mp48SerialScheduler" not in source
    assert "UsbSerialManager" not in source
    assert "KWriteManager" not in source
    assert "KFactorManager" not in source


def test_adaptive_does_not_create_a_second_runtime_backbone():
    adaptive_dir = ROOT / "app/src/main/java/com/omegas/prohub/adaptive"
    text = "\n".join(path.read_text(encoding="utf-8") for path in adaptive_dir.glob("*.kt"))
    forbidden = (
        "ResponseDrivenEcuEngine(",
        "UsbSerialManager(",
        "Mp48SerialScheduler(",
        "TelemetryStateStore(",
        "SessionRecorder(",
        "KWriteManager(",
        "KFactorManager(",
    )
    for marker in forbidden:
        assert marker not in text
