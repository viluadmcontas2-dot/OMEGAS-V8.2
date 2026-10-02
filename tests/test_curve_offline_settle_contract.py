from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CURVE = ROOT / "app" / "src" / "main" / "assets" / "ui" / "screens" / "curve.js"
ANDROID_RENDER = ROOT / "app" / "src" / "androidTest" / "java" / "com" / "omegas" / "prohub" / "DashboardLevelsRenderTest.kt"


def test_curve_failed_read_settles_instead_of_sticking_in_reading():
    source = CURVE.read_text(encoding="utf-8")
    assert "settleReadFailure(message)" in source
    assert "this.reading = false;" in source
    assert "this.root?.classList.remove('is-reading');" in source
    assert "text('curveSourceStatus', 'Curva não confirmada');" in source
    assert "if (this.reading && !operation.busy)" in source
    assert "operation.state !== 'COMPLETED' && !operation.demo" in source
    assert "this.settleReadFailure(operation.error || 'A leitura da Curva K não foi confirmada pela ECU.');" in source


def test_android_webview_gate_requires_offline_curve_to_settle():
    source = ANDROID_RENDER.read_text(encoding="utf-8")
    assert 'globalRouteDom(scenario, "curve").optString("curveSource") == "Curva não confirmada"' in source
    assert 'assertEquals("Offline Curve read must settle honestly", "Curva não confirmada", dom.optString("curveSource"))' in source
    assert 'assertTrue("Offline Curve must leave the reading state", !dom.getBoolean("curveReading"))' in source


if __name__ == "__main__":
    test_curve_failed_read_settles_instead_of_sticking_in_reading()
    test_android_webview_gate_requires_offline_curve_to_settle()
