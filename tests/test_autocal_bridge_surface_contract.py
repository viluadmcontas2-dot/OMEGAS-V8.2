from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")

FORBIDDEN = [
    "clearDraft",
    "createDraft",
    "getAnalysis",
    "getDraft",
    "getDraftReviewPayload",
    "getNativeActionReceipts",
    "getResidualAnalysis",
    "importSnapshotIntoLearning",
    "selectDraftPoint",
    "setDraftTargetFactor",
    "validateDraftReviewCurve",
]

for name in FORBIDDEN:
    assert f"fun {name}(" not in BRIDGE, f"dead JavaScript bridge method still exposed: {name}"

assert "private var draft:" not in BRIDGE, "dead local draft state still retained"
assert "private fun emptyDraft(" not in BRIDGE, "dead local draft helper still retained"
assert '.put("localDraft", false)' in BRIDGE, "identity must report that local draft is not exposed"
assert '.put("manualAutoMatchExposed", false)' in BRIDGE

REQUIRED = [
    "getStatus",
    "getSnapshot",
    "getNativeMonitorStatus",
    "getNativeMonitorSnapshot",
    "getUiProjection",
    "getSessionLedgerStatus",
    "listAutoCalSessions",
    "exportAutoCalSession",
    "startRead",
    "cancelRead",
    "getNativeActionStatus",
    "prepareNativeAction",
    "setAcquisitionEnabled",
    "executeNativeAction",
    "clearNativeActionPreparation",
    "getIdentity",
]
for name in REQUIRED:
    assert f"fun {name}(" in BRIDGE, f"live AutoCal bridge method was removed: {name}"

print("AUTOCAL_BRIDGE_SURFACE_CONTRACT=PASS")
