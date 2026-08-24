# Predictor Target Geometry Step 152 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the canonical typed Predictor project the relative K* field on the current Calibration Identity geometry using `Tpet_ref × RPM`, while preserving MAP/ΔP/environmental values as conditioning context only.

**Architecture:** Reuse `LearningCalibrationBinding` as the only Predictor-side runtime geometry boundary. Add a pure projection contract that validates the binding/fingerprint and delegates bilinear row/column weights to the existing `ContinuousLearningMath` explicit-axis overload. Adapt the Step151 relative field to use reference petrol time and explicit runtime axes; keep the historical `KMapPhysicalAxes` fixture outside this canonical path. Add offline holdout/ablation validation for current-vs-midpoint-vs-reference coordinate and 2D-vs-context models without granting runtime actionability.

**Tech Stack:** Kotlin/JVM; existing `LearningCalibrationBinding`, `ContinuousLearningMath`, `PredictorRelativeField`, `PredictorSpatialConfidence`; JUnit source tests plus focused exact-blob Kotlin probes.

**Spec:** Notion `FASE 07 — 147–164 — Predictor e Suggestions`, Step 152 (`3bd8ee52-ac54-81cc-b96c-d19e00aeb132`).

## Global Constraints

- Target field coordinate is `Tpet_ref × RPM`; current `petrolOnCngMs` is not a geometry coordinate.
- Runtime geometry comes from `LearningCalibrationBinding`, itself derived from `CalibrationIdentity` + KNOWN `MapGeometrySnapshot`; no Predictor ECU/serial read.
- Final Map K remains 12×12 and 2D; MAP/ΔP/water/gas temperature are context/conditioning only.
- Historical `KMapPhysicalAxes` is not runtime authority for the canonical typed relative path.
- Geometry unknown/mismatch fails closed with no target.
- Contextual dimension promotion is offline/report-only and requires holdout error and risk improvement.
- No Store, Router, Scheduler, writer, USB, serial or UI authority is added.
- No GitHub Actions and no physical-device claim.

---

### Task 1: Freeze target-coordinate and runtime-geometry contract

**Files:**
- Create: `app/src/test/java/com/omegas/prohub/learning/PredictorTargetGeometryTest.kt`
- Create: `app/src/main/java/com/omegas/prohub/learning/PredictorTargetGeometry.kt`

**Interfaces:**
- Consumes: `LearningCalibrationBinding`, `ContinuousLearningMath.bilinearWeights(rpm, petrolMs, rpmAxis, petrolAxisMs)`.
- Produces:
  - `data class PredictorEquilibriumCoordinate(val rpm: Double, val petrolReferenceMs: Double)`
  - `data class PredictorTargetCellWeight(val row: Int, val column: Int, val weight: Double)`
  - `data class PredictorGeometryProjection(val available: Boolean, val reason: String, val geometryFingerprint: String, val coordinate: PredictorEquilibriumCoordinate?, val weights: List<PredictorTargetCellWeight>)`
  - `object PredictorTargetGeometry { fun project(calibration: LearningCalibrationBinding, expectedGeometryFingerprint: String, rpm: Double, petrolReferenceMs: Double): PredictorGeometryProjection }`

- [ ] **Step 1: Write failing tests**

Tests must assert:
- KNOWN runtime axes project a point to bilinear weights whose rows use the runtime petrol-reference axis and columns use the runtime RPM axis;
- same `rpm + Tpet_ref` yields identical projection when current `petrolOnCngMs` differs because current petrol time is not an input to `project`;
- changing `Tpet_ref` changes the time-axis projection;
- geometry fingerprint A/B mismatch returns `available=false`, `reason="GEOMETRY_MISMATCH"`, empty weights;
- geometry missing returns `available=false`, `reason="GEOMETRY_UNKNOWN"`;
- invalid/non-finite coordinate returns fail-closed `INVALID_TARGET_COORDINATE`.

- [ ] **Step 2: Verify RED**

Expected structural failure: `PredictorTargetGeometry`, `PredictorEquilibriumCoordinate`, and `PredictorGeometryProjection` do not exist at the Step151 baseline.

- [ ] **Step 3: Implement the pure projection**

Implementation rules:
```kotlin
if (!calibration.geometryKnown()) return unavailable("GEOMETRY_UNKNOWN")
if (calibration.geometryFingerprint != expectedGeometryFingerprint) return unavailable("GEOMETRY_MISMATCH")
if (!rpm.isFinite() || rpm <= 0.0 || !petrolReferenceMs.isFinite() || petrolReferenceMs <= 0.0) {
    return unavailable("INVALID_TARGET_COORDINATE")
}
val weights = ContinuousLearningMath.bilinearWeights(
    rpm = rpm,
    petrolMs = petrolReferenceMs,
    rpmAxis = calibration.rpmAxis.map(Int::toDouble).toDoubleArray(),
    petrolAxisMs = calibration.petrolAxisMs.toDoubleArray(),
).map { PredictorTargetCellWeight(it.row, it.column, it.weight) }
```
Do not import `MapGeometryReader`, serial/USB types, or `KMapPhysicalAxes`.

- [ ] **Step 4: Run focused tests/probe and commit**

Expected: runtime geometry controls row/column weights; no fixture fallback is reachable.

---

### Task 2: Make Step151 relative field explicitly reference-coordinate bound

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/PredictorRelativeField.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/PredictorSpatialConfidence.kt`
- Modify: `app/src/test/java/com/omegas/prohub/learning/PredictorRelativeFieldTest.kt`

**Interfaces:**
- `PredictorRelativeObservation` gains explicit `petrolReferenceMs: Double` semantics and optional `petrolOnCngMs: Double?` context. Preserve a source-compatible alias only if required by existing callers.
- `PredictorRelativeFieldInput` consumes `calibration: LearningCalibrationBinding`, `expectedGeometryFingerprint: String`, `targetRpm`, `targetPetrolReferenceMs`, `currentK`, uncertainty, support.
- `PredictorSpatialConfidence.evaluateRelative(...)` gains explicit `rpmAxis` and `petrolReferenceAxisMs` parameters for canonical relative evaluation.
- `PredictorSpatialConfidence.physicalDistance(...)` gains an explicit-axis overload used by `PredictorRelativeField`.

- [ ] **Step 1: Write failing tests**

Freeze these properties:
- support differing only in `petrolOnCngMs` produces the same relative-field geometry/prediction;
- changing `petrolReferenceMs` changes geometry/locality as expected;
- geometry A evidence under geometry B input abstains;
- MAP/ΔP/water/gas context changes do not change the 2D target coordinate;
- canonical relative prediction cannot execute when runtime geometry is unknown;
- canonical relative source path contains no `KMapPhysicalAxes` dependency.

- [ ] **Step 2: Verify RED**

Expected failure: Step151 input does not yet carry runtime calibration geometry and `PredictorSpatialConfidence` relative path still normalizes through the historical fixture.

- [ ] **Step 3: Implement minimal canonical binding**

At the beginning of `PredictorRelativeField.predict` call `PredictorTargetGeometry.project(...)` for the query and fail closed when unavailable. Validate every support observation against the same runtime geometry by using its `rpm + petrolReferenceMs` only. Pass explicit runtime axes into relative spatial confidence/distance helpers.

Do not change the legacy JSONObject interpolator path in this task. Do not call `LearningCalibrationAuthority.snapshot()` from the typed Predictor.

- [ ] **Step 4: Property/fuzz verification and commit**

Fuzz geometry A/B, reference times, RPM and arbitrary `petrolOnCngMs` values. Required invariant: changing current petrol-on-GNV time with fixed `Tpet_ref + RPM + K* physics` never changes target cell weights.

---

### Task 3: Add offline coordinate-choice and contextual-ablation falsifiers

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/learning/PredictorGeometryAblationValidator.kt`
- Create: `app/src/test/java/com/omegas/prohub/learning/PredictorGeometryAblationValidatorTest.kt`

**Interfaces:**
- `enum class PredictorCoordinateCandidate { CURRENT_PETROL_ON_GAS, MIDPOINT, PETROL_REFERENCE }`
- `data class PredictorCoordinateHoldout(val epochId: String, val candidate: PredictorCoordinateCandidate, val absoluteLogError: Double, val riskLoss: Double)`
- `data class PredictorCoordinateValidationReport(val preferred: PredictorCoordinateCandidate?, val petrolReferenceValidated: Boolean, val reason: String, val p90ByCandidate: Map<PredictorCoordinateCandidate, Double>, val meanRiskByCandidate: Map<PredictorCoordinateCandidate, Double>)`
- `data class PredictorContextAblationOutcome(val epochId: String, val base2dAbsoluteLogError: Double, val base2dRiskLoss: Double, val contextualAbsoluteLogError: Double?, val contextualRiskLoss: Double?, val contextAvailable: Boolean)`
- `data class PredictorContextAblationReport(val promoteContextualDimension: Boolean, val reason: String)`
- `object PredictorGeometryAblationValidator { fun validateCoordinate(...): PredictorCoordinateValidationReport; fun validateContextual(...): PredictorContextAblationReport }`

- [ ] **Step 1: Write failing tests**

Tests:
- synthetic holdouts where Tpet_ref has lower P90 and mean risk select `PETROL_REFERENCE`;
- no candidate may be selected if it silently worsens either P90 or mean risk relative to another candidate;
- contextual model promotes only when it strictly improves both held-out error and risk on every comparable epoch;
- missing MAP/context makes contextual promotion unavailable without invalidating base 2D;
- partial/missing contextual outcomes never count as improvement.

- [ ] **Step 2: Verify RED**

Expected structural failure: validator/report types do not exist.

- [ ] **Step 3: Implement report-only validator**

No runtime actionability mutation, no persistence side effect, no threshold. Use strict comparisons of caller-provided holdout outcomes. P90 is a deterministic quantile of absolute log error.

- [ ] **Step 4: Run deterministic/property tests and commit**

Required: no silent P90/risk degradation, and missing context remains a valid base-2D case.

---

### Task 4: Exact-blob verification, audit and closure

**Files:**
- Verify all Step152 source/test files from final remote SHA.
- Update this plan only if implementation reality diverges from it.

- [ ] **Step 1: Source freshness**

Compare branch against exact target SHA; require identical.

- [ ] **Step 2: Exact-blob compile/property probes**

Reconstruct remote production blobs locally and verify `git hash-object` matches GitHub. Compile with `kotlinc` using only minimal stubs for unrelated Android/JSON surfaces. Run fuzz for:
- Tpet_ref invariance against current petrol-on-GNV changes;
- geometry A/B fail-closed;
- runtime-axis-only projection;
- MAP/context non-axis behavior;
- coordinate holdout P90/risk selection;
- contextual ablation fail-closed/missingness.

- [ ] **Step 3: Static authority scan**

Step152 canonical typed production must have no imports/references to USB, serial, writer, UI, Store, Router, Scheduler, `MapGeometryReader`, or runtime `KMapPhysicalAxes` fixture authority.

- [ ] **Step 4: Independent audit + meta-audit**

Record exact SHA, hashes, tests, limitations and false-pass checks in VIT-308. Mark Done only if both PASS.

- [ ] **Step 5: Continue directly to Step153**

Use the final Step152 SHA as Step153 source base. Full Android Gradle/device validation remains a later PREAPK/build-fabric gate and is not inferred from focused probes.
