from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
APK_WORKFLOW = REPO_ROOT / ".github/workflows/build-v82-apk.yml"
APP_GRADLE = REPO_ROOT / "app/build.gradle.kts"


def test_taytech_apk_workflow_targets_current_branch_and_32bit_abi():
    workflow = APK_WORKFLOW.read_text(encoding="utf-8")
    gradle = APP_GRADLE.read_text(encoding="utf-8")

    assert "rebuild/v8.2-final-implementation" in workflow
    assert "-PomegasAbis=armeabi-v7a" in workflow
    assert "armeabi-v7a" in workflow
    assert "arm64-v8a" in gradle  # generic default may remain for non-TayTech builds


def test_taytech_apk_workflow_verifies_artifact_abi_before_receipt():
    workflow = APK_WORKFLOW.read_text(encoding="utf-8")

    assert "Verify TayTech APK ABI" in workflow
    assert "armeabi-v7a" in workflow
    assert "arm64-v8a" in workflow
    assert "exit 1" in workflow
