# Step 151 — Relative correction field, shrinkage and abstention

## Goal
Add a typed spatial Predictor path whose learned quantity is `deltaStar = ln(K*/K_current)`. Preserve the legacy JSONObject path as diagnostic-only, preserve frozen direct support, and never reintroduce `suggestedDeltaPercent` as a learning label.

## Design
- New pure typed `PredictorRelativeField` consumes only direct `PredictorRelativeObservation` support.
- Reuse `PredictorSpatialConfidence` for physical hull/locality/trajectory-independence checks; do not create a second geometry authority.
- Interpolate deltaStar with trajectory-balanced distance/quality weights.
- Apply continuous shrinkage `delta_pred = raw_delta / (1 + distance + uncertainty)`; this cannot amplify a distant correction and tends to zero as distance/uncertainty grows.
- Propagate interval uncertainty by root-sum-square of support uncertainty, query uncertainty, local dispersion and physical distance; therefore uncertainty cannot decrease when distance/query uncertainty grows.
- Outside/insufficient physical support => `UNKNOWN_ABSTAIN`, no target.
- Inside support => `PREDICTED_INTERPOLATED` only when no shrink term exists; otherwise `PREDICTED_SHRUNK`.
- Prediction output remains a distinct type from direct support and cannot feed the support list.
- Risk is explicitly uncalibrated at this step; prediction is diagnostic-only/non-actionable until later calibrated outcome authority exists.
- No global direction veto: mixed distant signs shape the local field; local conflict handling belongs to Step154.

## TDD proof
1. RED: typed relative field API does not exist.
2. Positive/negative direct support preserves local sign.
3. Increasing query uncertainty shrinks |delta| and widens interval.
4. Increasing support distance shrinks |delta|; pure shrink function tends to zero as distance grows.
5. Outside hull / insufficient independent support abstains with no target.
6. Mixed distant signs do not globally veto a locally supported prediction.
7. Prediction and direct-support types are disjoint; no prediction-feedback path.
8. High diagnostic spatial confidence remains non-actionable while risk calibration is absent.
9. Exact remote SHA + focused property/fuzz + independent audit + meta-audit.
