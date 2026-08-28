from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_registry_has_full_causal_metadata_and_no_unclassified_entry_helper():
    source = read("app/src/main/java/com/omegas/prohub/learning/ScientificConstantRegistry.kt")
    for token in (
        "val symbol: String",
        "val value: String",
        "val unit: String",
        "val source: String",
        "val consumer: String",
        "val falsifier: String",
        "val owner: String",
        "val revision: String",
        "val classification: ScientificConstantClass",
    ):
        assert token in source
    assert "require(source.isNotBlank())" in source
    assert "require(consumer.isNotBlank())" in source
    assert "require(falsifier.isNotBlank())" in source
    assert "require(owner.isNotBlank())" in source


def test_legacy_selector_numbers_are_named_as_baselines_not_physical_truth():
    source = read("app/src/main/java/com/omegas/prohub/learning/ScientificConstantRegistry.kt")
    assert "classification = ScientificConstantClass.LEGACY_BASELINE" in source
    for symbol in (
        "selector.MAX_NEIGHBORS",
        "selector.DIRECT_DISTANCE_WINDOW",
        "selector.EXTRAPOLATION_QUALITY_FACTOR",
        "visitConfidence.STRONG_TARGET_VISITS",
        "visitConfidence.NOISY_TARGET_VISITS",
    ):
        assert symbol in source
    assert "not promoted to physical/native truth" in source


def test_registered_legacy_baselines_do_not_create_writer_or_human_confirmation():
    registry = read("app/src/main/java/com/omegas/prohub/learning/ScientificConstantRegistry.kt")
    assert "KWriteManager" not in registry
    assert "KFactorManager" not in registry
    assert "Mp48SerialScheduler" not in registry
    assert "humanConfirmed" not in registry
    assert "readbackValid" not in registry


def test_actionability_remains_downstream_of_manual_writer_contract_not_registry_classification():
    map_writer = read("app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt")
    factor_writer = read("app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt")
    assert 'put("humanConfirmed", true)' in map_writer
    assert 'put("readbackValid", true)' in map_writer
    assert 'put("humanConfirmed", true)' in factor_writer
    assert 'put("readbackValid", true)' in factor_writer
    # Registry metadata cannot bypass those physical contracts.
    assert "ScientificConstantRegistry" not in map_writer
    assert "ScientificConstantRegistry" not in factor_writer
