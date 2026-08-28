from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_learning_ui_heavy_projection_is_cached_by_persisted_revision():
    source = read("app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt")
    assert '@Volatile private var cachedRevision = ""' in source
    assert '@Volatile private var cachedPayload = ""' in source
    assert 'if (revision == cachedRevision && cachedPayload.isNotBlank())' in source
    assert 'return JSONObject(cachedPayload)' in source
    assert 'rawSnapshot.optString("stateDigest")' in source
    assert 'LearningSnapshotReconciler.reconcile(rawSnapshot)' in source
    assert 'AssistedCalibrationAdvisor.analyze(adviceInput)' in source
    assert '"PERSISTED_REVISION_CACHE"' in source


def test_revision_cache_does_not_turn_navigation_into_science_authority():
    bridge = read("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt")
    router = read("app/src/main/assets/ui/core/router.js")
    learning = read("app/src/main/assets/ui/screens/learning.js")
    assert 'LearningUiSnapshotAssembler.assemble(raw)' in bridge
    assert 'startSession(' not in router
    assert 'ingest(' not in router
    assert 'onCalibrationAdjustment(' not in router
    assert 'startKWrite(' not in learning
    assert 'startKBatchWrite(' not in learning
