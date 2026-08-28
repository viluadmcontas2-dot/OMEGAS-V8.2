from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LEARNING_MODEL = ROOT / "app/src/main/assets/ui/core/learning-model.js"


def test_explicit_refresh_invalidates_structural_cache_before_normal_app_listener():
    source = LEARNING_MODEL.read_text(encoding="utf-8")
    assert "root.addEventListener('omegas-refresh', controller.invalidate, true);" in source
