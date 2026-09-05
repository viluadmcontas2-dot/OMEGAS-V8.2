#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/omegas/prohub"
LEARNING = SOURCE / "learning"
RUNTIME = SOURCE / "ecu/NativeRuntimeManager.kt"

# The Blue runtime has one passive evidence store. Historical decision wrappers
# are not compatibility layers: they must not exist in production source.
forbidden_files = {
    "LiveOnlyLearningStore.kt",
    "DeferredLiveOnlyLearningStore.kt",
    "LearningTelemetrySchemaMigration.kt",
    "MotorLearningMemory.kt",
}
existing = {path.name for path in LEARNING.glob("*.kt")}
leftovers = sorted(forbidden_files & existing)
assert not leftovers, f"retired learning authorities still exist: {leftovers}"

forbidden_symbols = (
    "LiveOnlyLearningStore",
    "DeferredLiveOnlyLearningStore",
    "LearningTelemetrySchemaMigration",
    "MotorLearningMemory",
    "advisorSnapshot",
    "AssistedCalibrationAdvisor",
)
violations = []
for path in sorted(SOURCE.rglob("*.kt")) + sorted(SOURCE.rglob("*.java")):
    text = path.read_text(encoding="utf-8")
    for symbol in forbidden_symbols:
        if symbol in text:
            violations.append(f"{symbol}: {path.relative_to(ROOT)}")
assert not violations, "retired decision symbols remain:\n" + "\n".join(violations)

runtime = RUNTIME.read_text(encoding="utf-8")
assert "BlueEvidenceStore" in runtime, "NativeRuntimeManager must own BlueEvidenceStore directly"
assert "private val learning = BlueEvidenceStore(" in runtime, (
    "runtime must bind directly to the passive Blue evidence store"
)

store = LEARNING / "BlueEvidenceStore.kt"
assert store.is_file(), "BlueEvidenceStore.kt must be the single persisted learning store"
store_text = store.read_text(encoding="utf-8")
assert "BlueCausalEngine" not in store_text, "storage cannot become a second decision authority"
assert "automaticWrite" not in store_text, "storage cannot authorize calibration writes"
assert "AssistedCalibration" not in store_text, "storage cannot carry an advisor authority"
assert "MotorLearningMemory" not in store_text, "Blue evidence storage cannot delegate to retired memory"

print("BLUE_LEARNING_STORAGE_CONTRACT=PASS")
