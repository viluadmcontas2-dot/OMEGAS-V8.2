from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_ui_boundary_forbids_direct_transport_science_and_writer_authority():
    architecture = read("app/src/main/java/com/omegas/prohub/model/ArchitectureContracts.kt")
    for marker in (
        "MAY_TOUCH_USB_DIRECTLY = false",
        "MAY_PARSE_MP48_DIRECTLY = false",
        "MAY_WRITE_ECU_DIRECTLY = false",
        "MAY_OWN_SCIENTIFIC_MATH = false",
    ):
        assert marker in architecture


def test_learning_tools_and_overlay_do_not_own_serial_or_writer_entry_points():
    learning = read("app/src/main/assets/ui/screens/learning.js")
    tools = read("app/src/main/assets/ui/components/drawers.js")
    overlay = read("app/src/main/java/com/omegas/prohub/service/TelemetryOverlayController.kt")
    router = read("app/src/main/assets/ui/core/router.js")

    for forbidden in ("startKWrite(", "startKBatchWrite(", "startKFactorWrite(", "readKMap(", "readKFactorCurve("):
        assert forbidden not in learning
        assert forbidden not in tools
    for forbidden in ("UsbSerialManager", "Mp48SerialScheduler", "KWriteManager", "KFactorManager"):
        assert forbidden not in overlay
    for forbidden in ("startSession(", "ingest(", "onCalibrationAdjustment("):
        assert forbidden not in router


def test_tools_self_test_is_in_memory_protocol_validation_not_serial_io():
    runtime = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")
    self_test = runtime.split("fun selfTestJson(): String", 1)[1].split("fun exportLearning", 1)[0]
    assert "Mp48Protocol.readKRow(0)" in self_test
    assert "Mp48Protocol.writeKCell(0, 0, 0x93)" in self_test
    assert "serialAdmission.execute" not in self_test
    assert "serialScheduler" not in self_test
    assert "usb." not in self_test


def test_collecting_state_has_native_learning_authority_not_ui_heuristic_contract():
    doc = read("docs/contracts/068B-presentation-authority.md")
    assert "Kotlin Learning `SampleDecision` / Learning projection" in doc
    assert "UI never infers" in doc
    assert "zero UI disk I/O is **not** claimed" in doc
