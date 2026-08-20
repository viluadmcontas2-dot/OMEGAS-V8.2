from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
APP_GRADLE = REPO_ROOT / "app/build.gradle.kts"
WORKFLOW_DIR = REPO_ROOT / ".github/workflows"


def test_taytech_build_profile_keeps_explicit_32bit_abi_override():
    gradle = APP_GRADLE.read_text(encoding="utf-8")

    # The target profile is selected at build time with
    # `-PomegasAbis=armeabi-v7a`; source must keep that override wired all the
    # way into Android's abiFilters without hard-coding a TayTech-only default.
    assert 'providers.gradleProperty("omegasAbis")' in gradle
    assert 'abiFilters += targetAbis' in gradle
    assert 'orElse("arm64-v8a")' in gradle  # generic non-TayTech default may remain


def test_build_profile_contract_does_not_require_a_permanent_apk_workflow():
    workflows = list(WORKFLOW_DIR.glob("*.yml")) + list(WORKFLOW_DIR.glob("*.yaml")) if WORKFLOW_DIR.is_dir() else []
    workflow_text = "\n".join(path.read_text(encoding="utf-8") for path in workflows)

    # CI governance is local/ephemeral-first. The ABI contract belongs to the
    # Gradle build profile, not to a permanently enabled remote workflow.
    assert "build-v82-apk" not in {path.stem for path in workflows}
    assert "Verify TayTech APK ABI" not in workflow_text
