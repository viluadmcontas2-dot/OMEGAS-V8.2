# Predictor Physics Mechanism + Local Conflict Step 154 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the canonical typed Predictor consume Physics `CorrectionMechanism` and transform truly local/context-comparable contradictory evidence into bounded local variance/confidence penalty without any global direction veto.

**Architecture:** Extend the existing `PredictorRelativeObservation/Input/Prediction` seam rather than add a second spatial predictor. Physics remains the mechanism authority. The Predictor filters support by the supplied mechanism, uses the existing runtime geometry/distance weighting, and computes a continuous local-conflict score from opposite-sign support weighted by physical proximity and caller-supplied query-context comparability. The score only widens uncertainty and reduces diagnostic confidence. It never creates evidence, chooses a mechanism, or grants actionability.

**Spec:** Notion `FASE 07 — 147–164 — Predictor e Suggestions`, Step 154.

## Constraints
- Predictor consumes, never infers, `CorrectionMechanism`.
- `MAP_LOCAL` and `CURVE_MUL_ACT` are actuator-correction mechanisms. `ENVIRONMENTAL_DIAGNOSTIC`, `NO_ACTION`, and `UNKNOWN` cannot manufacture an actuator target.
- Only support from the same mechanism may contribute to that mechanism's field.
- Distant opposite signs are legitimate shape; no global `DIRECTION_CONFLICT` veto.
- Conflict authority requires both physical locality and explicit query-context comparability. Unknown comparability contributes zero conflict authority.
- No hard distance radius or conflict threshold.
- Conflict penalty is continuous: local contradiction increases variance and lowers published confidence; actionability remains `ABSTAIN/RISK_NOT_CALIBRATED`.
- Final 2D geometry and Step152 runtime-axis binding remain unchanged.
- No Store/Router/Scheduler/writer/USB/serial/UI path.

### Task 1 — Freeze mechanism consumption contract
**Files:**
- Modify `PredictorRelativeField.kt`
- Add `PredictorMechanismFieldTest.kt`

**Contract additions:**
- `PredictorRelativeObservation.mechanism: CorrectionMechanism`
- `PredictorRelativeObservation.queryContextComparability: Double` in `0..1`; this is snapshot/query-preparation metadata, not a global learned constant.
- `PredictorRelativeFieldInput.mechanism: CorrectionMechanism`
- `PredictorRelativePrediction.mechanism: CorrectionMechanism`
- `PredictorRelativePrediction.localConflictScore: Double`
- `PredictorRelativePrediction.baseSpatialConfidence: Double`

**RED tests:**
1. `UNKNOWN`, `ENVIRONMENTAL_DIAGNOSTIC`, and `NO_ACTION` inputs abstain before actuator prediction.
2. `MAP_LOCAL` ignores CURVE support and vice versa; insufficient same-mechanism support abstains.
3. Canonical Step151/152 fixtures are updated to explicitly use `MAP_LOCAL`; no default UNKNOWN authority may silently predict.

### Task 2 — Freeze continuous local conflict penalty
**RED tests:**
1. Symmetric distant positive/negative clusters produce positive sign near the positive cluster, negative sign near the negative cluster, and near-zero correction around a symmetric middle without a global veto.
2. A co-local/context-comparable mixed-sign fixture has a larger `localConflictScore`, wider uncertainty, and lower final confidence than an otherwise matched consistent fixture.
3. Setting context comparability to zero removes conflict authority without removing the underlying observation from same-mechanism interpolation.
4. Opposite sign support under another mechanism does not increase the current mechanism conflict score.

**Implementation:**
- Filter support to `observation.mechanism == input.mechanism`.
- Existing distance contribution remains `quality/(1+distance)`.
- Conflict weight per support = interpolation weight × `queryContextComparability`.
- `positiveWeight` / `negativeWeight` split by sign of `deltaStar`; zeros are neutral.
- `localConflictScore = if (positiveWeight+negativeWeight>0) 2*min(pos,neg)/(pos+neg) else 0`, bounded 0..1.
- `conflictThetaStd = localConflictScore * localStd`; add in root-sum-square uncertainty.
- `baseSpatialConfidence = spatial.confidence`; final `spatialConfidence = base * (1-localConflictScore)`.
- No binary conflict reason and no threshold.

### Task 3 — Verification and closure
- Exact remote blob hash match before compile/probe.
- Focused property/fuzz: 1,000 random local conflict monotonic cases; 500 mechanism-isolation cases; 500 distant-cluster fixtures.
- Static scan: canonical typed Step154 path contains no global `DIRECTION_CONFLICT` gate, writer/transport/Store/Router/Scheduler/Android/JSON.
- Independent audit + distinct meta-audit in VIT-310.
- Full Android/device proof remains later PREAPK work and is never inferred from focused probes.
