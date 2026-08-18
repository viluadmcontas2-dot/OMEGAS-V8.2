from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "docs/contracts/102B-autocal-native-observer-contract.md"


def test_102b_freezes_18_band_acquisition_separate_from_30_point_vectors():
    text = CONTRACT.read_text(encoding="utf-8")
    for field in (
        "NUM_BUF_UPD_PETR", "NUM_BUF_UPD_GAS", "PETR_INJ_TBUF", "MNFLD_PRESS_BUF",
        "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS",
        "PETR_INJ_TBUF_GAS_PREV", "MNFLD_PRESS_BUF_GAS_PREV",
    ):
        assert field in text
    for field in ("MUL_ACT", "PETR_INJ_TBP", "PETR_MNFLD_PRESS_RV", "GAS_MNFLD_PRESS_RV"):
        assert field in text
    assert "Shape: **18**" in text
    assert "Shape: **30**" in text
    assert "moduleVersion=100" in text


def test_102b_freezes_shadow_only_and_no_fabricated_rpm_or_direct_k():
    text = CONTRACT.read_text(encoding="utf-8")
    normalized = text.lower()
    assert "ECU_MATURED != OMEGAS_EQUIVALENT" in text
    assert "RPM nunca é derivado do índice da banda" in text
    assert "`INCONCLUSIVE`, `rpm=null`" in text
    assert "nenhum anchor autocal escreve ecu" in normalized
    assert "GasolineOracle → KStarEstimator → Target/Step" in text


def test_102b_keeps_single_runtime_backbone():
    text = CONTRACT.read_text(encoding="utf-8")
    assert "`Mp48SerialScheduler` existente" in text
    assert "abrir/fechar tela não cria probe" in text
    assert "snapshot pesado apenas por evento material" in text
    assert "UI é projection revision-driven" in text
