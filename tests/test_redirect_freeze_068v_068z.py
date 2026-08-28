from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/contracts/068V-068Z-redirect-freeze.md"


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_redirect_contract_contains_every_owner_and_audit_gate():
    text = DOC.read_text(encoding="utf-8")
    for suffix in "VWXYZ":
        assert f"## 068{suffix}" in text
    for marker in (
        "metric-primary-numeral",
        "Aprender → editar A → Predictor → editar B → Agora → Review",
        "independent falsifier",
        "producer→consumer call path",
        "F4:", "F8:", "F9:", "F11:", "F12:",
        "no parallel architecture",
    ):
        assert marker in text


def test_router_is_single_presentation_authority_and_has_no_science_calls():
    router = read("app/src/main/assets/ui/core/router.js")
    app = read("app/src/main/assets/ui/app.js")
    assert "const router = new ui.Router(store)" in app
    assert "new ui.Router(store)" not in router
    for forbidden in ("ingest(", "startSession(", "onCalibrationAdjustment(", "Mp48", "KWrite"):
        assert forbidden not in router


def test_current_dashboard_gap_is_not_misrepresented_as_068v_implementation():
    dashboard = read("app/src/main/assets/ui/screens/dashboard.js")
    doc = DOC.read_text(encoding="utf-8")
    assert 'class="hero-rpm"' in dashboard
    assert 'id="dashPetrol"' in dashboard
    assert "068V is not implemented yet" in doc


def test_contract_does_not_authorize_parallel_architecture():
    doc = DOC.read_text(encoding="utf-8")
    assert "No new Store, Router, Scheduler, serial path, Learning engine, Predictor authority or writer" in doc
