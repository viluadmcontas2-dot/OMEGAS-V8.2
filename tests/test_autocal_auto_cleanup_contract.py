from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manager = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
service = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")


def test_only_point_reacquisition_gets_an_automatic_writer_path():
    assert "fun executeAutomaticPointDelete" in manager
    assert "Action.DELETE_POINT" in manager
    assert '.put("automatic", prepared.automatic)' in manager
    assert '.put("humanConfirmed", !prepared.automatic)' in manager
    assert '.put("manualOnly", !prepared.automatic)' in manager


def test_background_service_owns_detection_and_executes_through_canonical_manager():
    assert "AutoCalNativeOutlierDetector" in service
    assert "autoCalOutlierDetector.observe" in service
    assert "executeAutomaticPointDelete" in service
    assert "REACQUIRE_MIN_RPM" in service
    assert "autocal_auto_reacquire" in service


def test_manual_bridge_remains_manual_for_all_operator_actions():
    assert "fun executeNativeAction" in bridge
    assert '.put("automatic", false)' in bridge
    assert '.put("manualOnly", true)' in bridge
