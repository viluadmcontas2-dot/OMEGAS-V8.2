#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


adaptive = read("app/src/main/java/com/omegas/prohub/learning/AdaptivePetrolReference.kt")
advisor = read("app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt")
adapter = read("app/src/main/java/com/omegas/prohub/calibration/AdvisorSuggestionAdapterV7.kt")
coordinator = read("app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt")
causal = read("app/src/main/java/com/omegas/v7/runtime/CalibrationCausalTransitionV7.kt")
continuous = read("app/src/main/java/com/omegas/prohub/learning/ContinuousLearningMath.kt")
learning_ui = read("app/src/main/assets/ui/screens/learning.js")
stability_test = read("app/src/test/java/com/omegas/v7/runtime/LearningStabilityV7Test.kt")
causal_test = read("app/src/test/java/com/omegas/v7/runtime/CalibrationCausalTransitionV7Test.kt")
causal_step_test = read("app/src/test/java/com/omegas/prohub/calibration/AdvisorSuggestionAdapterV7CausalStepTest.kt")

# Frozen gasoline prior F2: coefficients and domain stay explicit; no runtime refit surface.
for coefficient in (
    "2.14396620", "0.34083653", "1.87748601", "-0.66741389",
    "-0.54659976", "1.24856920", "-0.32307503", "1.27193350",
    "2.00071259", "0.47671656", "-0.25959427",
):
    assert coefficient in adaptive, coefficient
assert "internal fun f2" in adaptive
assert "RPM_SCALE = 240.0" in adaptive
assert "MAP_SCALE = 0.060" in adaptive
assert "RIDGE = 0.001" in adaptive
assert "MAX_SUPPORT_REGIONS = 60" in adaptive
assert "localQuadraticResidual" in adaptive

# S is carried, persisted by the caller, and each independent session moves it cautiously.
assert "acceptedScale" in adaptive
assert "SESSION_CARRY_FORWARD_BOUNDED_0_5_PERCENT" in adaptive
assert "MAX_SESSION_SCALE_STEP_RATIO = 0.005" in adaptive
assert "previous.promotedSessionId == sessionId" in adaptive
assert "REAL_PETROL_ONLY" in adaptive
assert "PetrolReferenceSelector.estimate(regions, request, policy)" in adaptive
assert "if (physical.available) return physical" in adaptive
assert "PRIOR_PLUS_RESIDUAL" in adaptive

# Scientific decomposition remains global first, then local residual in RPM x Petrol Inj.
assert "val global = globalCurve(samples)" in advisor
assert "val residual = residualMap(samples, global)" in advisor
assert advisor.index("val global = globalCurve(samples)") < advisor.index("val residual = residualMap(samples, global)")
assert "ContinuousLearningMath.bilinearWeights(sample.rpm, sample.petrolObservedMs)" in advisor
assert '"bilinear-rpm-petrol-after-supported-global-removal"' in advisor

# First suggestion remains 0.75; the base advisor cannot escalate from visit count.
for marker in (
    "MIN_CORRECTION_FRACTION = 0.75",
    "FIRST_VISIT_MAX_CORRECTION_FRACTION = 0.75",
    "EARLY_VISITS_MAX_CORRECTION_FRACTION = 0.75",
    "MAX_CORRECTION_FRACTION = 0.75",
    '"SCIENTIFIC_FIXED_075_MANUAL"',
):
    assert marker in advisor, marker
assert '.put("automatic", false)' in advisor
assert '.put("humanConfirmationRequired", true)' in advisor

# 0.90 is unlocked only by the latest causal result for the same physical target.
assert "CONFIRMED_CAUSAL_FRACTION = 0.90" in adapter
assert adapter.count("maxByOrNull { it.appliedAtMs }") >= 2
assert "CausalTransitionStatusV7.CONFIRMED" in adapter
assert "causalTransitions = active.state.calibrationTransitions" in coordinator
assert "contradictedOrUnrelatedTransitionCannotUnlock090" in causal_step_test

# Calibration identity is material Curve+Map and causal confirmation uses only post-state evidence.
assert "fun CalibrationStateV7.materialFingerprint()" in causal
assert 'append("curve:")' in causal
assert 'append("|map:")' in causal
assert "comparison.revision == after.revision" in causal
assert "comparison.createdAtMs >= suggestion.updatedAtMs" in causal
assert "AWAITING_POST_EVIDENCE" in causal
assert "CONFIRMED" in causal and "CONTRADICTED" in causal and "INCONCLUSIVE" in causal
assert "wrong material" in causal_test.lower() or "material" in causal_test.lower()

# Volatile display is robust: isolated contrary visit revalidates without replacing consolidated truth.
assert "CONSOLIDATED" in stability_test
assert "REVALIDATING" in stability_test
assert "um outlier isolado apenas revalida" in stability_test
assert "mudanca repetivel promove novo consolidado" in stability_test
assert "stableComparisonError" in learning_ui
assert "state === 'CONSOLIDATED' || state === 'REVALIDATING'" in learning_ui
assert "state === 'LEARNING'" in learning_ui

# The existing bilinear physical geometry is the only spatial propagation currently authorized.
assert "fun bilinearWeights" in continuous
assert "fun gaussianSpatialKernel" in continuous
runtime_kotlin = "\n".join(
    path.read_text(encoding="utf-8")
    for base in (
        ROOT / "app/src/main/java/com/omegas/prohub/learning",
        ROOT / "app/src/main/java/com/omegas/v7/runtime",
    )
    for path in base.glob("*.kt")
)
assert runtime_kotlin.count("gaussianSpatialKernel(") == 1, "Gaussian spatial smoothing must remain un-wired until falsified"
assert "0.775" not in runtime_kotlin
assert ".775" not in runtime_kotlin

print("VERDE_SCIENTIFIC_RUNTIME_CONTRACT=PASS")
