from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
engine = (ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdIndependentLearningEngine.kt").read_text(encoding="utf-8")
confidence = (ROOT / "app/src/main/java/com/omegas/prohub/blue/BlueWitnessConfidence.kt").read_text(encoding="utf-8")
suggestion = (ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdMapKSuggestion.kt").read_text(encoding="utf-8")
assert "gnvModeDeclared" in engine
for forbidden in ["petrolMs", "PETROL", "ltft", "KWriteManager"]:
    assert forbidden not in engine, forbidden
assert "OPTIONAL_NON_BLOCKING_CONFIDENCE" in confidence
assert "OBD_CONFLICT_COLLECT_MORE" not in confidence
assert "ADDRESS_UNRESOLVED" in suggestion and "manualOnly" in suggestion
assert "startWrite(" not in suggestion and "startBatchWrite(" not in suggestion
print("BLUE_OBD_GNV_ONLY_INTEGRATION_CONTRACT=PASS")
