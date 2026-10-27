from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_active_authority_is_branch_ativa():
    assert "Branch autorizada: `OMEGASCANONICO`" in read("PROJECT.md")
    assert "resolver HEAD remoto de `OMEGASCANONICO`" in read("AGENTS.md")  # ativa e canônica (2026-10-20)
    assert "Branch: `OMEGASCANONICO`" in read("STATUS.md")


def test_only_two_workflows_exist_and_ci_preserves_all_groups():
    names = sorted(p.name for p in (ROOT / ".github" / "workflows").glob("*.yml"))
    assert names == ["apk.yml", "ci.yml"], names  # dono, 2026-10-17: sem automação legada
    text = read(".github/workflows/ci.yml")
    assert "branches: ['**']" in text
    for obsolete in ("platina", "vermelho", "legado", "verde-apk"):
        assert obsolete not in text.lower()
    for job in ("javascript", "python", "kotlin", "lint", "grafo"):
        assert f"  {job}:" in text
        assert f"needs.{job}.result" in text
    assert 'glob("test_*.py")' in text
    assert 'glob("*.test.cjs")' in text
    assert "@requires-gradle-test-results" in text
    assert "workflow run" not in text
    assert "assemble" not in text


def test_apk_workflow_is_manual_gated_and_not_push_triggered():
    text = read(".github/workflows/apk.yml")
    assert "workflow_dispatch" in text
    assert "build_apk" in text
    assert "if: ${{ inputs.build_apk == true }}" in text
    assert "push:" not in text
    assert "assembleDebug" in text
    assert "-PomegasAbis=arm64-v8a" in text
    assert "OMEGAS-${VERSION}-${PRODUCT_SHA:0:12}.apk" in text
    for obsolete in ("platina", "vermelho", "legado", "verde-apk"):
        assert obsolete not in text.lower()


def test_v7_predictor_suggestion_cannot_start_writer():
    coordinator = read("app/src/main/java/com/omegas/prohub/calibration/CalibrationCoordinator.kt")
    assert "active.applySuggestionToEcu(" not in coordinator
    assert "MANUAL_REVIEW_REQUIRED" in coordinator
    assert "automaticWriteBlocked" in coordinator
    assert "writesStarted" in coordinator
    assert "private val ecuWriter" not in coordinator
    # Adaptador sugestão -> writer apagado (dono, 16/10): não existe mais caminho compilável.
    assert not (ROOT / "app/src/main/java/com/omegas/prohub/calibration/ExistingCalibrationWriter.kt").exists()
    assert "ExistingCalibrationWriter" not in coordinator


def test_status_keeps_gap_classification_fail_closed():
    status = read("STATUS.md")
    assert "PARTIAL" in status
    assert "UNKNOWN" in status
    assert "PARTIAL` ou `UNKNOWN` em comando de mutação bloqueia release" in status
    assert "GAS_PREV" in status
    assert "contador anterior UNKNOWN" in status

if __name__ == "__main__":
    test_active_authority_is_branch_ativa()
    test_only_two_workflows_exist_and_ci_preserves_all_groups()
    test_apk_workflow_is_manual_gated_and_not_push_triggered()
    test_v7_predictor_suggestion_cannot_start_writer()
    test_status_keeps_gap_classification_fail_closed()
    print("MISSAO_FINAL_CONTRACT=PASS")
