# Step 147 — Typed Predictor Contract Implementation Plan

**Goal:** Freeze one canonical typed Predictor input/output boundary that fails closed on stale or mismatched scientific identity/revisions while preserving the existing diagnostic JSON projection.

**Execution authority:** Linear VIT-301.
**Source authority:** GitHub remote branch `feature/phase06-pragmatic-equivalence-20260821`.
**Starting SHA:** `19046a92c5bca2a2d973cf7d2faaf86250f59a8a`.

## Constraints

- Reuse `LearningCalibrationBinding` / `CalibrationIdentity`; do not create a second calibration authority.
- Predictor is pure: no USB/raw serial/writer/UI/store/router/scheduler dependencies.
- Observation and prediction are distinct typed concepts; predictions cannot feed the observation input.
- Missing/UNKNOWN/NaN/stale/mismatched calibration, generation, geometry, map/curve hashes or source revisions fail closed.
- JSON remains compatibility/diagnostic projection only, never mandatory typed transport.
- Step 147 freezes the contract only; Step 148+ owns spatial reason taxonomy, StepPolicy, shrinkage and further interpolation policy.
- No GitHub Actions. Tests/builds run from an ephemeral checkout of the exact remote SHA.

## Task 1 — RED: freeze contract behavior

**Create:** `app/src/test/java/com/omegas/prohub/learning/PredictorContractTest.kt`

Tests:
1. Same scientific identity + current source revisions accepts a direct observation and emits a revisioned typed `IdealTargetCandidate`.
2. Calibration fingerprint/generation mismatch abstains.
3. Geometry fingerprint mismatch abstains.
4. Map/curve hash or revision mismatch abstains.
5. Missing/non-positive evidence/reference/physics revisions abstain.
6. NaN/UNKNOWN scientific values abstain.
7. Evidence from calibration B cannot become actionable under calibration A.
8. `PredictorPrediction` cannot be passed where `PredictorObservation` is required (compile-time type split; runtime test also confirms only observations enter the input collection).
9. Output contains no writer/USB/raw-serial/UI authority.

Run the focused test first and record the expected RED failure because the typed contract does not yet exist.

## Task 2 — GREEN: implement the minimal typed boundary

**Create:** `app/src/main/java/com/omegas/prohub/learning/PredictorContract.kt`

Types:
- `PredictorSourceIdentity`: calibration binding + map/curve hashes/revisions + evidence/reference/physics revisions + epoch/session provenance.
- `PredictorOperatingPoint`: RPM, petrol injection, MAP and optional reference temperature plus effective mass/capacity context when known.
- `PredictorObservation`: direct K* scientific observation with uncertainty/support/provenance; finite values only.
- `PredictorPrediction`: typed downstream prediction, intentionally not substitutable for `PredictorObservation`.
- `PredictorInputSnapshot`: immutable canonical input containing the expected source identity and direct observations.
- `IdealTargetCandidate`: typed target candidate with uncertainty/support/provenance/source revisions.
- `PredictorSnapshot`: immutable revisioned output with target candidates and abstention state.
- `PredictorContract.evaluate(...)`: pure validator/projection that fails closed on any identity/revision mismatch.

Keep validation deterministic and free of framework/JSON dependencies.

## Task 3 — Wire one real production seam without breaking legacy JSON

**Modify:** `app/src/main/java/com/omegas/prohub/learning/PredictorSurface.kt`

- Add a typed overload accepting `PredictorInputSnapshot` and returning `PredictorSnapshot` through `PredictorContract`.
- Keep the existing `JSONObject` overload as legacy diagnostic compatibility.
- Do not allow the legacy overload to become a writer or scientific observation authority.

**Modify tests:** `PredictorSurfaceTest.kt` only if needed to prove the typed overload delegates to the canonical contract while legacy tests remain unchanged.

## Task 4 — Focused verification

From an ephemeral checkout of the exact resulting remote SHA:
- run `PredictorContractTest`;
- run `PredictorSurfaceTest`, `PredictorInterpolatorTest`, `PredictorSpatialConfidenceTest`;
- run relevant calibration-binding/scientific-signal tests;
- run static grep proving no writer/serial/USB/UI imports in `PredictorContract.kt`.

If full Gradle/Android toolchain is unavailable, record that limitation and use the strongest executable Kotlin/Gradle evidence available without claiming unrun coverage.

## Task 5 — Independent audit + meta-audit

Using `code-verification` on the exact remote SHA:
- verify acceptance against VIT-301 and Phase 07 source of truth;
- verify no second Store/Router/Scheduler/runtime;
- verify prediction cannot become observation;
- verify mismatched identity/revisions fail closed;
- compare starting SHA → final SHA for scope drift;
- record audit and separate meta-audit receipts in Linear.

Mark VIT-301 Done only on PASS. Then advance to Step 148 using Linear as execution writer and Notion Phase 07 as requirement authority.
