from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def test_autocal_normal_surface_matches_closed_progbase_host_model():
    cockpit = read("app/src/main/assets/ui/screens/autocal-cockpit.js")
    css = read("app/src/main/assets/ui/styles-autocal-cockpit.css")
    assert 'data-autocal-action="FINISH_AUTOCAL"' not in cockpit
    assert 'data-autocal-action="FINISH_AUTOMATCH"' not in cockpit
    assert ".autocal-finish-action" not in css
    assert "O AutoMatch nativo é automático e decidido pela ECU." in cockpit
    assert "Nada aqui roda automaticamente." not in cockpit
    assert "autoMatchQuotaReached" in cockpit
    assert "AutoMatch automático " in cockpit
    assert "A aquisição continua habilitada e pode preencher novas zonas" in cockpit
    assert "autoMatchQuotaReached" in cockpit
    assert "AUTO_CAL_ENABLE" not in cockpit.split("autoMatchQuotaReached", 1)[0][-180:]

def test_finish_remains_backend_compatibility_not_terminal_semantics():
    manager = read("app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt")
    assert 'FINISH_AUTOCAL(' in manager
    assert 'Encerrar cota AutoMatch (técnico)' in manager
    assert 'originalmente desabilitada no DFM' in manager
    assert 'PanelDbg oculto do ProgBase' in manager
    assert 'AutoCal finalizado' not in manager
    assert 'aquisição não foi pausada' in manager

def test_modern_curve_screen_preserves_progbase_manual_k_capability():
    curve = read("app/src/main/assets/ui/screens/curve.js")
    coherence = read("tests/ui/curve-map-editor-coherence.test.cjs")
    assert "data-curve-nudge" in curve
    assert "nudgeActive" in curve
    assert "writePrepared()" in curve
    assert "writeCurve" in curve
    assert "uma única confirmação humana" in coherence

def test_host_actions_remain_distinct_and_manual():
    manager = read("app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt")
    for action in ("RESET_PETROL", "RESET_GAS", "RESET_ALL", "RESET_K_FACTOR", "DELETE_POINT"):
        assert action in manager
    assert "MANUAL_AUTOMATCH(" not in manager
    assert '"automatic", false' in manager
    assert '"manualOnly", true' in manager