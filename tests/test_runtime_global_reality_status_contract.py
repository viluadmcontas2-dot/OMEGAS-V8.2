import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATUS = ROOT / "tools" / "ci" / "global_reality_status.py"

spec = importlib.util.spec_from_file_location("global_reality_status", STATUS)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def test_global_reality_status_classifier_distinguishes_product_from_harness():
    assert module.classify_failure(0, "") == "PASS"
    assert module.classify_failure(
        1,
        "Could not resolve all files for configuration ':app:debugUnitTestCompileClasspath'.\n"
        "Could not find org.json:json:20240303.",
    ) == "BROKEN"
    assert module.classify_failure(1, "AssertionError: expected 4 but was 5") == "RED"


def test_global_reality_workflow_exposes_red_and_broken_separately():
    lane = (ROOT / "tools" / "ci" / "global_reality_lane.py").read_text(encoding="utf-8")
    workflow = (ROOT / ".github" / "workflows" / "verde-global-reality-fanout.yml").read_text(encoding="utf-8")
    assert "run_with_broken_retry" in lane
    assert 'status=="BROKEN"' in lane
    assert 'else 2 if status=="BROKEN" else 1' in lane
    assert "Fail RED product contract" in workflow
    assert "Fail BROKEN infrastructure" in workflow
    assert 'broken.get("conclusion")=="failure"' in workflow
    assert 'red.get("conclusion")=="failure"' in workflow


if __name__ == "__main__":
    test_global_reality_status_classifier_distinguishes_product_from_harness()
    test_global_reality_workflow_exposes_red_and_broken_separately()
