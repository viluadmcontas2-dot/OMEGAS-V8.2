from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/contracts/068C-068U-product-freeze.md"


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_all_contract_owners_068c_through_068u_are_mirrored():
    text = DOC.read_text(encoding="utf-8")
    for suffix in "CDEFGHIJKLMNOPQRSTU":
        assert f"## 068{suffix}" in text
    for invariant in (
        "No silent clamp",
        "ACTIVE_COMPATIBLE | PRIOR_ONLY | OBSERVATIONAL | REJECTED_INCOMPATIBLE | CORRUPT",
        "SESSION_RECORDED_AT",
        "EXPORT_TIME_CURRENT",
        "MapDraftItem",
        "Erro de equivalência",
        "Coletando referência gasolina",
    ):
        assert invariant.lower() in text.lower()


def test_overlay_reuses_existing_store_and_does_not_create_transport_or_scheduler():
    floating = read("app/src/main/assets/ui/components/floating-telemetry.js")
    overlay = read("app/src/main/java/com/omegas/prohub/service/TelemetryOverlayController.kt")
    assert "this.store = app.store" in floating
    assert "new ns.Store" not in floating
    assert "new ns.Router" not in floating
    assert "new ns.Scheduler" not in floating
    for forbidden in ("UsbSerialManager", "Mp48SerialScheduler", "KWriteManager", "KFactorManager"):
        assert forbidden not in overlay


def test_current_export_surfaces_remain_separate_until_sessionbundle_owner():
    recorder = read("app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt")
    archive = read("app/src/main/java/com/omegas/prohub/learning/LearningArchiveManager.kt")
    assert "fun exportSession(" in recorder
    assert "fun export(resolver: ContentResolver, uri: Uri)" in archive
    # Freeze test deliberately does not pretend SessionBundle is already implemented.
    assert "SessionBundle" not in recorder


def test_map_editor_has_reusable_per_cell_override_but_contract_truth_is_documented_separately():
    editor = read("app/src/main/assets/ui/map-editor.js")
    doc = DOC.read_text(encoding="utf-8")
    assert "targetOverrides = new Map()" in editor
    assert "setTargetOverride(row, column, target)" in editor
    assert "selectedCells + oneGlobalDelta" in doc
    assert "120 ≤ K ≤ 200" in doc
    assert "No silent clamp" in doc
