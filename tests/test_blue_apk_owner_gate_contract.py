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
    assert "assembleDebug" not in full, "normal push/full CI must not generate APK"
    gate = "if: ${{ github.event_name == 'workflow_dispatch' && inputs.build_apk == true }}"
    assert gate in apk, "APK job must require manual dispatch plus explicit build_apk=true"
    assert "assembleDebug" in apk, "artifact job must remain capable after owner authorization"
    assert "actions/upload-artifact" in apk
    assert "OWNER_AUTHORIZED=true" in apk
    assert "READY_FOR_APK_GENERATION=true" in full
    assert "APK_GENERATED=false" in full
    print("BLUE_APK_OWNER_GATE_CONTRACT=PASS")


if __name__ == "__main__":
    main()
