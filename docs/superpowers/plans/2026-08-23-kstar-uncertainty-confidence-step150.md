# Step 150 — K* observation, uncertainty and calibratable confidence

## Goal
Extend the existing Physics K* seam without creating a second estimator. Preserve `KStarEstimator` as the only target estimator, reuse `PlantGain`, `PhysicsOracleValidator`, and `ConditionalActuatorTargets`, and add a pure calibration layer for uncertainty, evidence stage, and decomposed confidence.

## Authority reuse
- `KStarEstimator`: sole owner of `e = ln(Tpet_GNV/Tpet_ref)` and `theta* = theta + e/g`.
- `PlantGain`: sole typed gain mean/bounds/authority input.
- `PhysicsOracleValidator`: offline bootstrap/Monte-Carlo oracle authority.
- `ConditionalActuatorTargets`: actuator decomposition; F* is resolved before MAP/Curve allocation and Curve remains blocked until local residual is removed.
- Predictor remains downstream; Step 150 does not add Store/Router/Scheduler/writer/serial paths.

## TDD contract
1. RED: require a pure `KStarObservationCalibration` layer and Monte-Carlo propagation API that do not exist yet.
2. Physics invariants:
   - zero error => target factor equals current factor and delta*=0;
   - positive/negative log error preserves sign;
   - unknown gain abstains through existing KStarEstimator.
3. Uncertainty:
   - use first-order log-space propagation around the existing target equation;
   - reference/observation/gain/context/model/contradiction uncertainties enter as explicit components;
   - increasing one component while holding the rest fixed cannot narrow the target interval;
   - contradiction is variance, not a direction veto.
4. Fast evidence stage:
   - strong comparable reference + resolved context: 4 frames => DIRECT_PROVISIONAL; 6 => DIRECT_CONFIRMED;
   - unresolved/weak context never becomes direct authority solely because frames accumulate; at 8+ publish a non-actionable fallback-collection state.
5. Confidence:
   - keep reference, observation, effective-sample, independent-visit, locality, context, model-fit, and calibration-freshness components explicit;
   - combine monotonically with no actionability threshold; stronger consistent component values cannot reduce the diagnostic score.
6. Oracle:
   - extend the existing `PhysicsOracleValidator` with deterministic seeded Monte-Carlo log-space propagation;
   - compare runtime analytic 95% interval with the oracle in tests; tolerance is test-only numerical validation, never a runtime actionability threshold.
7. Regression:
   - existing KStarEstimator evidence-role/self-comparison/no-weight/unknown-gain behavior remains unchanged;
   - existing ConditionalActuatorTargets continues preventing the same residual from being applied to MAP and Curve simultaneously.

## Verification
- exact branch SHA / no source drift;
- focused Kotlin executable probe from exact remote blobs;
- property/fuzz over uncertainty monotonicity, 4→6 stage, sign, unknown/NaN and confidence monotonicity;
- independent audit + distinct meta-audit;
- full Android Gradle build remains a separate PREAPK/build-fabric gate and is never inferred from focused probes.
