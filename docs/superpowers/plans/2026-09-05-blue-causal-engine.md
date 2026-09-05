# OMEGAS Blue — Single Causal Engine Implementation Plan

**Issue:** #16  
**Branch:** `work/omegas-blue-causal-engine`  
**Base:** `hotfix/v8.0-red-performance`

## Task 1 — Blue CI identity
- Extend the existing RED remote CI workflow to run on the Blue branch without changing the FAST → FULL → APK safety structure.
- Add a contract proving Blue is descended directly from RED and that the single-engine invariant is declared.

## Task 2 — Core causal contracts (TDD)
Create tests first for:
- `BlueOperatingPoint(rpm,mapBar,petrolMs)`;
- `BlueCalibrationStateId` and state boundaries;
- `BluePetrolReference` from stable microburst summary;
- `BlueCngError = ln(PetrolCng/PetrolRef)`;
- `BlueActuatorGain = -Δe/Δln(K)` with invalid/degenerate cases rejected;
- immutable `BlueCausalSnapshot` and `BlueCorrectionProposal`.

Implement the minimal `BlueCausalEngine` required to pass those tests.

## Task 3 — Single authority structural gate
Add a repository contract that fails while competing correction motors remain reachable. The allowed decision authority is only `BlueCausalEngine`.

Classify existing code:
- pure telemetry/protocol/window-quality utilities may remain;
- any class that owns independent equivalence prediction, visit-confidence, K-target math or Auto-Cal residual planning must be removed, replaced or reduced to a passive adapter.

## Task 4 — Gasoline reference ingestion
- Reuse only the healthy-window mechanics that are pure quality filtering.
- Feed accepted gasoline microbursts into `BlueCausalEngine`.
- Confidence derives from burst quality/stability and support, not number of visits.
- Calibration writes/fuel transitions/telemetry loss split evidence boundaries.

## Task 5 — CNG equivalence and global/local decomposition
- Compare CNG microbursts only against comparable gasoline reference.
- Compute log-ratio error.
- Aggregate smooth/global signal by petrol-injection region for Curve K.
- Send only systematic post-global residual to Map K, addressed by RPM + petrol injection geometry.

## Task 6 — Causal actuator learning
- Every confirmed write/readback creates a new calibration state.
- Record before/after comparable observations.
- Estimate bounded gain from actual interventions rather than assuming 1.0 or 0.7.
- Failed/divergent readback produces no causal evidence.

## Task 7 — Auto-Cal replacement
Tests first must prove Auto-Cal cannot calculate a correction independently.
Then:
- remove/retire `AutoMatchV5Engine`, `AutoMatchKFactorDraft`, `AutoMatchResidualPlanner` as decision authorities;
- add a Blue Auto-Cal adapter that reads `BlueCorrectionProposal` only;
- keep acquisition, user review, confirmation, ACK and readback;
- return confirmed transition to the Blue engine.

## Task 8 — Predictor/Advisor/Learn convergence
- Make all user-facing consumers read the same `BlueCausalSnapshot` and proposal.
- Eliminate legacy fallback paths (`PredictorInterpolator`, `PredictorSpatialConfidence`, visit-count confidence, independent Advisor math) from runtime.
- Delete obsolete decision-engine source/tests once no references remain.

## Task 9 — Historical causal replay
- Add normalized causal-event/pair fixtures derived from historical logs.
- Verify state isolation, direction of response, gain learning and no pooling across K states.
- Use resampling/bootstrap checks where appropriate to ensure estimates are not driven by one event.

## Task 10 — Remote verification and read-back
- FAST contracts PASS.
- Full JVM tests PASS.
- lintDebug PASS.
- assembleDebug PASS.
- Structural search proves no reachable legacy correction motor.
- Record exact SHA, workflow run, APK checksum and remaining physical-validation limitation.

No task may claim fuel-economy improvement without controlled vehicle validation.
