import json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEXT = {".kt", ".kts", ".js", ".cjs", ".css", ".html", ".xml", ".json", ".py", ".sh", ".yml"}
# Os dois arquivos V7 que citavam o Predictor em comentário saíram na Task 2.9: a tupla fica vazia.
V7_PENDING = ()


def read(rel): return (ROOT / rel).read_text(encoding="utf-8")


def gone(paths):
    alive = [p for p in paths if (ROOT / p).exists()]
    assert alive == [], alive


def hits(pattern, roots, flags=0, allow=()):
    rx, out = re.compile(pattern, flags), []
    for r in roots:
        for p in sorted((ROOT / r).rglob("*")):
            rel = p.relative_to(ROOT).as_posix()
            if p.is_file() and p.suffix in TEXT and rel not in allow \
               and rx.search(p.read_text(encoding="utf-8", errors="ignore")):
                out.append(rel)
    return out


# O pacote antigo de aprendizado saiu na F3; o caminho é montado para não citar o pacote literal.
OLD_PKG = "learning"
PRED_KT = [f"app/src/main/java/com/omegas/prohub/{OLD_PKG}/Predictor{n}.kt" for n in ("Interpolator", "Surface", "SpatialConfidence")]
PRED_KT_TESTS = [f"app/src/test/java/com/omegas/prohub/{OLD_PKG}/Predictor{n}Test.kt" for n in ("Interpolator", "Surface", "SpatialConfidence")]


def test_predictor_kotlin_is_gone():
    gone(PRED_KT + PRED_KT_TESTS + ["tests/test_predictor_map_residual_contract.py",
                                    "tests/test_v82_integral_regression_contract.py"])
    assert hits(r"predictor", ("app/src/main/java",), re.I, allow=V7_PENDING) == []


UI = "app/src/main/assets/ui/"
PRED_UI = [UI + p for p in ("screens/predictor.js", "core/predictor-model.js", "components/predictor-current-cell.js",
                            "styles-predictor.css", "styles-predictor-live.css")]
PRED_UI_TESTS = [f"tests/ui/predictor-{n}.test.cjs" for n in ("consumer", "live-cell", "pan-layout")]


def test_predictor_ui_is_gone():
    gone(PRED_UI + PRED_UI_TESTS)
    for rel in (UI + "app.js", UI + "core/router.js"):
        assert re.search(r"predictor", read(rel), re.I) is None, rel


def test_no_predictor_anywhere_in_app_src():
    assert hits(r"predictor", ("app/src",), re.I, allow=V7_PENDING) == []


COCKPIT = UI + "screens/autocal-cockpit.js"


def test_cockpit_exposes_no_manual_automatch_nor_reset_all():
    c = read(COCKPIT)
    assert "MANUAL_AUTOMATCH" not in c
    assert 'data-autocal-action="RESET_ALL"' not in c
    for keep in ('data-autocal-action="RESET_GAS"', 'data-autocal-action="RESET_PETROL"',
                 'data-autocal-action="RESET_K_FACTOR"', "O AutoMatch é automático e decidido pela ECU.",
                 "Pausar interrompe a aquisição."):
        assert keep in c, keep


AUTOCAL = "app/src/main/java/com/omegas/prohub/autocal/"


def test_autocal_bridge_refuses_retired_actions():
    b, m = read(AUTOCAL + "AutoCalJavascriptBridge.kt"), read(AUTOCAL + "NativeAutoCalMonitor.kt")
    assert "Action.MANUAL_AUTOMATCH" not in b and "Action.RESET_ALL" not in b
    for kept in ("RESET_PETROL", "RESET_GAS", "RESET_K_FACTOR", "DELETE_POINT"):
        assert b.count(f"AutoCalNativeActionManager.Action.{kept},") == 2, kept
    assert '.put("manualAutoMatchExposed", false)' in b and '"manualAutoMatchExposed", true' not in b
    assert m.count('.put("manualAutoMatchExposed", false)') == 2 and '"manualAutoMatchExposed", true' not in m


EPOCH = AUTOCAL + "NativeAutoCalAcquisitionEpoch.kt"


def test_manual_automatch_only_in_log_readers():
    # Guardião: o ramo morto saiu também da época; nenhum código de app/src/main cita a ação aposentada.
    assert hits(r"MANUAL_AUTOMATCH", ("app/src/main",)) == []
    assert '"RESET_GAS" ->' in read(EPOCH)
    assert '"MANUAL_AUTOMATCH":"02 24 04 08 32"' in read("tools/omegas/extract_lognovo_autocal_epochs.py")
    assert "RESET_ALL(0x04)" in read("app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt")
    assert "RESET_ALL(" in read(AUTOCAL + "AutoCalNativeActionManager.kt")
    fx = json.loads(read("tests/fixtures/platinum-autocal-action-parity-v1.json"))
    assert "MANUAL_AUTOMATCH" not in {a["name"] for a in fx["actions"]}
    assert [r["name"] for r in fx["retired"]] == ["MANUAL_AUTOMATCH"]
    assert {a["name"] for a in fx["actions"] if a["uiExposed"] is False} == {"RESET_ALL", "FINISH_AUTOCAL", "FINISH_AUTOMATCH"}


def test_curve_reads_no_suggestion_nor_advisor():
    gone([UI + "components/curve-prediction-state.js", UI + "styles-curve-prediction.css"])
    curve = read(UI + "screens/curve.js")
    for token in ("suggestionItems", "calibrationState", "assistedCalibration", "assisted_calibration",
                  "kFactorSuggestions", "persistentCurveChanges"):
        assert token not in curve, token
    assert "curve-prediction-state" not in read(UI + "core/router.js")


def test_suggestions_tab_is_gone_and_sessions_took_its_place():
    app, drawers = read(UI + "app.js"), read(UI + "components/drawers.js")
    for token in ("suggestionItems", "calibrationState", "selectedSuggestionIds", "data-review-selected",
                  "renderPersistentSuggestions", "updateSuggestionBadge", "suggestionCount"):
        assert token not in app, token
    for token in ("renderSuggestions", "suggestionsButton", "toolsButton", "suggestionDrawer", "assistedCalibration"):
        assert token not in drawers, token
    assert "calibrationState" not in read(UI + "core/native-api.js")
    router = read(UI + "core/router.js")
    assert "'suggestions'" not in router and "'sessions'" in router
    assert "suggestionCount" not in read(UI + "index.html")
    gone(["tests/test_block3_suggestion_ui_contract.py"])


V7_MAIN = ["app/src/main/java/com/omegas/v7"] + [
    "app/src/main/java/com/omegas/prohub/" + p for p in (
        "web/V7JavascriptBridge.kt", "service/V7CalibrationAccess.kt", "calibration/V7CalibrationCoordinator.kt",
        "calibration/AdvisorSuggestionAdapterV7.kt", "calibration/ExistingCalibrationWriterV7.kt",
        "calibration/CalibrationWriterReadBackV7.kt")]
V7_TESTS = ["app/src/test/java/com/omegas/v7"] + [
    "app/src/test/java/com/omegas/prohub/calibration/" + p for p in (
        "AdvisorSuggestionAdapterV7Test.kt", "AdvisorSuggestionAdapterV7CausalStepTest.kt",
        "CalibrationWriterReadBackV7Test.kt")] + [f"tests/{n}.py" for n in (
        "test_causal_step_wiring_contract", "test_suggestion_readback_lifecycle_contract",
        "test_learning_consolidation_contract", "test_verde_scientific_runtime_contract")]


def test_v7_brain_is_gone():
    gone(V7_MAIN + V7_TESTS)
    assert V7_PENDING == ()
    assert hits(r"\b(?:import|package)\s+com\.omegas\.v7\b", ("app/src",)) == []
    assert hits(r"OmegasV7|v7Bridge|V7JavascriptBridge|v7CalibrationStateJson|V7CalibrationCoordinator"
                r"|AdvisorSuggestionAdapterV7|ExistingCalibrationWriterV7|CalibrationWriterReadBackV7",
                ("app/src", "tests"),
                allow=("tests/test_poda_1_contract.py", "tests/test_red_snapshot_bus_contract.py")) == []
    hub = read("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt")
    assert '.put("calibrationState"' not in hub and '"v7_sessions"' not in hub
    assert json.loads(read("config/omegas-release.json"))["applicationId"] == "com.omegas.v7.test"
    assert "com.omegas.v7.test" in read("tools/ci/run_android_render_evidence.sh")


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_"): fn()
    print("PODA_1_CONTRACT=PASS")
