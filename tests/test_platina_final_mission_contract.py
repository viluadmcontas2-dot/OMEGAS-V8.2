from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_active_authority_is_omegas_platina():
    # Texto de AGENTS/PROJECT/STATUS é coberto por tests/test_governance_contract.py.
    assert "OmegasPlatina" in read(".github/workflows/ci.yml")


def test_active_push_workflows_target_platina_only():
    for path in (
        ".github/workflows/verde-fast-contracts.yml",
        ".github/workflows/ci.yml",
        ".github/workflows/autocal-blueprint-layout.yml",
    ):
        text = read(path)
        assert "OmegasPlatina" in text, path
        push_block = text.split("workflow_dispatch", 1)[0]
        assert "OmegasVerde" not in push_block, path


def test_apk_workflow_is_manual_gated_and_not_push_triggered():
    text = read(".github/workflows/verde-apk-now.yml")
    assert "workflow_dispatch" in text
    assert "build_apk" in text
    assert "if: ${{ inputs.build_apk == true }}" in text
    assert "push:" not in text
    assert "assembleDebug" in text


def test_v7_predictor_suggestion_cannot_start_writer():
    coordinator = read("app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt")
    writer = read("app/src/main/java/com/omegas/prohub/calibration/ExistingCalibrationWriterV7.kt")
    assert "active.applySuggestionToEcu(" not in coordinator
    assert "MANUAL_REVIEW_REQUIRED" in coordinator
    assert "automaticWriteBlocked" in coordinator
    assert "writesStarted" in coordinator
    assert "private val ecuWriter" not in coordinator
    assert "LAB_ONLY" in writer
    assert "labOnlyWriterEnabled: Boolean = false" in writer


def test_status_keeps_gap_classification_fail_closed():
    status = read("docs/archive/STATUS-ate-2026-10-03.md")
    assert "PARTIAL" in status
    assert "UNKNOWN" in status
    assert "PARTIAL` ou `UNKNOWN` em comando de mutação bloqueia release" in status
    assert "GAS_PREV" in status
    assert "contador anterior UNKNOWN" in status

if __name__ == "__main__":
    test_active_authority_is_omegas_platina()
    test_active_push_workflows_target_platina_only()
    test_apk_workflow_is_manual_gated_and_not_push_triggered()
    test_v7_predictor_suggestion_cannot_start_writer()
    test_status_keeps_gap_classification_fail_closed()
    print("PLATINA_FINAL_MISSION_CONTRACT=PASS")
