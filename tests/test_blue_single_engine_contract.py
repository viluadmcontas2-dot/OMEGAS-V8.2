#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java"

engine = MAIN / "com/omegas/prohub/blue/BlueCausalEngine.kt"
autocal_adapter = MAIN / "com/omegas/prohub/blue/BlueAutoCalAdapter.kt"

assert engine.exists(), "BlueCausalEngine must be the single runtime equivalence/correction authority"
assert autocal_adapter.exists(), "Auto-Cal must delegate through BlueAutoCalAdapter"

# These classes own independent decision math in RED and must not survive Blue as
# reachable/parallel engines. Pure telemetry/protocol/manual-write utilities are
# intentionally not prohibited.
forbidden_authorities = [
    MAIN / "com/omegas/v7/runtime/V7EquivalenceEngine.kt",
    MAIN / "com/omegas/prohub/learning/PredictorInterpolator.kt",
    MAIN / "com/omegas/prohub/learning/PredictorSpatialConfidence.kt",
    MAIN / "com/omegas/prohub/learning/VisitConfidence.kt",
    MAIN / "com/omegas/prohub/autocal/AutoMatchV5Engine.kt",
    MAIN / "com/omegas/prohub/autocal/AutoMatchKFactorDraft.kt",
    MAIN / "com/omegas/prohub/autocal/AutoMatchResidualPlanner.kt",
]
for path in forbidden_authorities:
    assert not path.exists(), f"legacy decision authority still present: {path.relative_to(ROOT)}"

engine_text = engine.read_text(encoding="utf-8")
for required in [
    "class BlueCausalEngine",
    "ln(",
    "calibrationState",
    "actuatorGain",
    "petrolReference",
]:
    assert required in engine_text, f"Blue engine missing causal contract token: {required}"

adapter_text = autocal_adapter.read_text(encoding="utf-8")
assert "BlueCausalEngine" in adapter_text
for forbidden_math in ["AutoMatchResidualPlanner", "AutoMatchV5Engine", "AutoMatchKFactorDraft"]:
    assert forbidden_math not in adapter_text, f"Auto-Cal adapter still depends on legacy math: {forbidden_math}"

print("BLUE_SINGLE_ENGINE_CONTRACT=PASS")
