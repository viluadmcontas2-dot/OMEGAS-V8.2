from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/blue-ci.yml"


def main() -> None:
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "build_apk:" in text, "manual APK authorization input missing"
    assert "default: false" in text, "APK gate must default to disabled"
    assert "type: boolean" in text, "APK gate must be an explicit boolean"

    full_start = text.index("  full:")
    apk_start = text.index("  apk:", full_start)
    full = text[full_start:apk_start]
    apk = text[apk_start:]

    assert "testDebugUnitTest lintDebug" in full
    assert "assembleDebug" not in full, "ordinary push/full CI must not generate APK"

    dispatch_gate = "github.event_name == 'workflow_dispatch' && inputs.build_apk == true"
    rerun_gate = "github.event_name == 'push' && github.run_attempt > 1 && github.triggering_actor == github.repository_owner"
    assert dispatch_gate in apk, "workflow_dispatch owner gate must remain supported"
    assert rerun_gate in apk, "explicit owner-triggered re-run gate missing"
    assert "github.run_attempt > 1" in apk, "first-attempt push must remain unable to build APK"
    assert "github.triggering_actor == github.repository_owner" in apk, "re-run gate must require repository owner"

    assert '"STATUS.md"' in text, "STATUS evidence changes must create exact-SHA CI evidence"
    assert '"docs/workunits/OMEGAS-BLUE-ALGO-VERIFY-001.md"' in text, "workunit evidence changes must create exact-SHA CI evidence"

    assert "assembleDebug" in apk, "artifact job must remain capable after owner authorization"
    assert "actions/upload-artifact" in apk
    assert "OWNER_AUTHORIZED=true" in apk
    assert "OWNER_AUTHORIZATION_MODE=" in apk
    assert "APK_RECEIPT_VERIFICATION=PASS" in apk
    assert "VERIFIED_APK_SHA256=" in apk
    assert "VERIFIED_APK_BYTES=" in apk
    assert "READY_FOR_APK_GENERATION=true" in full
    assert "APK_GENERATED=false" in full
    print("BLUE_APK_OWNER_GATE_CONTRACT=PASS")


if __name__ == "__main__":
    main()
