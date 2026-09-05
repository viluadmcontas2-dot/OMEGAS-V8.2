#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java"
ASSETS = ROOT / "app/src/main/assets/ui"

# Spec Kit is now governance, not optional prose.
for required in [
    ROOT / ".specify/memory/constitution.md",
    ROOT / "specs/001-blue-runtime-convergence/spec.md",
    ROOT / "specs/001-blue-runtime-convergence/plan.md",
    ROOT / "specs/001-blue-runtime-convergence/tasks.md",
]:
    assert required.exists(), f"missing Spec Kit artifact: {required.relative_to(ROOT)}"

# No compatibility/legacy decision authority is allowed to survive the hard cut.
forbidden_paths = [
    MAIN / "com/omegas/v7/runtime/BlueEquivalenceCompatibility.kt",
    MAIN / "com/omegas/prohub/calibration/AdvisorSuggestionAdapterV7.kt",
    MAIN / "com/omegas/prohub/calibration/V7CalibrationCoordinator.kt",
    MAIN / "com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt",
    MAIN / "com/omegas/prohub/learning/PredictorSurface.kt",
]
for path in forbidden_paths:
    assert not path.exists(), f"legacy decision runtime still present: {path.relative_to(ROOT)}"

# Dead tests must not keep deleted AutoMatch engines alive at compile time.
legacy_test_names = {
    "AutoMatchDraftReviewValidatorTest.kt",
    "AutoMatchKFactorDraftTest.kt",
    "AutoMatchResidualPlannerTest.kt",
    "AutoMatchSnapshotAnalysisTest.kt",
    "AutoMatchV5EngineTest.kt",
    "V7EquivalenceEngineTest.kt",
    "VisitConfidenceTest.kt",
    "PredictorInterpolatorTest.kt",
    "PredictorSpatialConfidenceTest.kt",
}
for path in ROOT.glob("app/src/test/**/*.kt"):
    assert path.name not in legacy_test_names, f"stale legacy test remains: {path.relative_to(ROOT)}"

# RPM may locate/describe a cell, but must never authorize a write.
write_authority_paths = [
    MAIN / "com/omegas/prohub/calibration/CalibrationWriteSafetyPolicy.kt",
    MAIN / "com/omegas/prohub/calibration/KWriteManager.kt",
    MAIN / "com/omegas/prohub/calibration/KFactorManager.kt",
    MAIN / "com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt",
    MAIN / "com/omegas/prohub/autocal/AutoCalNativeActionManager.kt",
    MAIN / "com/omegas/prohub/web/HubJavascriptBridge.kt",
    ASSETS / "screens/map.js",
    ASSETS / "screens/curve.js",
    ASSETS / "screens/autocal-cockpit.js",
]
threshold = re.compile(r"\brpm\b\s*(?:[<>]=?)\s*\d+|\d+\s*(?:[<>]=?)\s*\brpm\b", re.I)
for path in write_authority_paths:
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    hits = [m.group(0) for m in threshold.finditer(text)]
    assert not hits, f"RPM write gate found in {path.relative_to(ROOT)}: {hits[:3]}"

settings = (MAIN / "com/omegas/prohub/settings/AppSettings.kt").read_text(encoding="utf-8")
assert 'prefs.getInt("sessionKeepCount", 30)' in settings, "useful-session default must be 30"
assert "coerceIn(20, 100)" in settings, "useful-session setting must never go below 20"

recorder = (MAIN / "com/omegas/prohub/diagnostics/SessionRecorder.kt").read_text(encoding="utf-8")
for token in ["SessionRelevance", "PROBE", "VALID", "PROTECTED", "segment"]:
    assert token in recorder, f"logical/relevance session contract missing token: {token}"
assert "keep - 1" not in recorder, "raw directory count must not prune useful sessions"

service = (MAIN / "com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
assert 'sessionRecorder.stop("MP48 desconectado")' not in service, "transient USB disconnect must not end logical session"
assert "onUsbDisconnected" in service or "recordUsbSegment" in service, "USB segment boundary must be recorded"

vault_candidates = list((MAIN / "com/omegas/prohub").rglob("*SessionVault*.kt"))
assert vault_candidates, "user-controlled session vault implementation is missing"
vault_text = "\n".join(p.read_text(encoding="utf-8") for p in vault_candidates)
assert "takePersistableUriPermission" in vault_text or "persisted" in vault_text.lower(), "session vault must persist SAF permission"
assert "spool" in vault_text.lower(), "vault must preserve private spool semantics"

autocal = (MAIN / "com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")
assert "BLUE_ENGINE_PROPOSAL_NOT_BOUND_YET" not in autocal, "Auto-Cal is still not bound to Blue proposal"
assert "BlueAutoCalAdapter" in autocal or "blueProposal" in autocal, "Auto-Cal must consume Blue proposal"

learning = (ASSETS / "screens/learning.js").read_text(encoding="utf-8")
assert "prediction ||" not in learning and "|| prediction" not in learning, "measured deviation must not silently fall back to prediction"
assert "Blue" in learning or "BLUE" in learning, "Learning must expose Blue proposal separately"

print("BLUE_RUNTIME_CONVERGENCE_CONTRACT=PASS")
