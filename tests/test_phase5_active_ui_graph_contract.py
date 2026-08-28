from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/assets/ui"


def test_active_asset_graph_keeps_future_predictor_out_of_phase5_runtime():
    html = (UI / "index.html").read_text(encoding="utf-8")
    assert 'src="screens/predictor.js"' not in html
    assert 'data-route="predictor"' not in html


def test_active_ui_has_one_scheduler_authority_and_no_screen_local_intervals():
    app = (UI / "app.js").read_text(encoding="utf-8")
    assert app.count("new ui.Scheduler(") == 1
    for name in ("dashboard.js", "learning.js", "map.js", "curve.js", "obd.js"):
        source = (UI / "screens" / name).read_text(encoding="utf-8")
        assert "setInterval(" not in source, f"{name} created a second active UI timer"


def test_learning_heavy_projection_is_requested_only_by_coordinated_context_path():
    app = (UI / "app.js").read_text(encoding="utf-8")
    assert app.count("api.learning()") == 1
    assert "patch.learning = api.learning() || {};" in app
    assert "patch.learningStatus = api.learningStatus() || {};" in app
