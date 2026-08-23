# Step 149 — IdealTarget / confidence / StepPolicy separation

**Goal:** Make the Predictor's physical target K*-driven only, expose the relative physical correction `deltaStar`, and move damping into a separate pure StepPolicy boundary.

**Execution authority:** Linear VIT-303.
**Starting SHA:** `705dc13a87853b857bc1922291d6056d0a9b4815`.

## Design

- `PredictorContract` remains the physical target authority. `IdealTargetCandidate.targetK` continues to come only from observed K*.
- Add `deltaStar = ln(K*/Kcurrent)` to the typed candidate when both values are positive; it is physical evidence, not a damped next step.
- Confidence/support changes cannot change `targetK` or `deltaStar` when K*/currentK are unchanged.
- Add a separate pure `PredictorStepPolicy` with no writer/USB/serial/UI dependency. It accepts `currentK`, `idealTargetK`, and an externally selected `beta` and computes `K_next = K_current * exp(beta * deltaStar)`.
- `beta` is policy state, not Predictor evidence. `beta=0` means no step and `beta=1` reaches the ideal target (within rounding); invalid/non-finite beta fails closed.
- Legacy `PredictorSurface(JSONObject, ...)` may continue displaying `suggestedDeltaPercent` as Advisor diagnostic data, but must never convert it into `targetK`.
- Because the legacy interpolator currently consumes only that fabricated `targetK`, residual-only JSON loses interpolation authority at this step and remains UNKNOWN/diagnostic until a K*-driven support path is introduced by the later spatial steps. Conservative abstention is preferred to preserving a scientifically invalid label.
- Do not change spatial confidence math, direction-conflict policy, shrinkage, or model calibration in Step 149.

## TDD

RED first:
1. same K*/currentK under low/high support produces identical IdealTarget + deltaStar;
2. `PredictorStepPolicy` beta changes K_next but never IdealTarget;
3. beta=0 -> currentK; beta=1 -> idealTarget; positive/negative delta preserve direction;
4. invalid beta or zero/non-positive log-domain endpoints fail closed;
5. legacy residual `suggestedDeltaPercent` remains visible but `targetK` is null;
6. residual-only legacy interpolation cannot create a predicted target;
7. static source check finds no `MapKManualPlanner.target` or other `suggestedDeltaPercent -> targetK` conversion in PredictorSurface.

## Verification

Use exact remote blobs for the typed contract/StepPolicy with focused Kotlin probes and property tests. For JSON legacy behavior, source-level contract tests remain in the Android/JUnit suite; full Android Gradle is claimed only when a real build fabric is available. Independent audit and meta-audit must close VIT-303 before Step 150.
