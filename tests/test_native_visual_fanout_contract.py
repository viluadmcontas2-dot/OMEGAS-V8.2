from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"


def _body(source: str, start: str, end: str) -> str:
    return source.split(start, 1)[1].split(end, 1)[0]


def test_overlay_admission_happens_before_expensive_status_projection():
    source = SERVICE.read_text(encoding="utf-8")
    assert "private val overlayAdmission = VisualFanoutAdmission(250L)" in source
    body = _body(source, "private fun updateOverlay", "private fun startForegroundCompat")
    assert "overlayAdmission.tryAcquire" in body
    assert body.index("overlayAdmission.tryAcquire") < body.index("val hub = status()")
    assert body.index("overlayAdmission.tryAcquire") < body.index("obd?.statusJson()")


def test_enabling_overlay_forces_one_immediate_projection():
    source = SERVICE.read_text(encoding="utf-8")
    body = _body(source, "fun setTelemetryOverlayEnabled", "fun nativeAutoCalStatusJson")
    assert "updateOverlay(force = true)" in body
