# Geometric Learning Field — Design

## Outcome

Make the existing RED learning system explain and test the physical relation
`RPM × MAP → Petrol Inj.` without creating another Android prediction authority.
Every valid frame contributes to local knowledge. Session/epoch counts describe
transfer and persistence; they do not erase dense local evidence.

## One scientific contract

1. **Coordinate:** RPM × MAP(bar).
2. **Observed response:** Petrol Inj. distribution at that coordinate.
3. **Equivalence:** compare GNV Petrol Inj. with the gasoline reference estimated
   at an equivalent RPM × MAP coordinate.
4. **Curve K:** global component indexed by the independent 30-point Petrol Inj.
   axis.
5. **Map K:** residual local component projected on the independent 12 × 12
   RPM × Petrol Inj. axes.
6. **AutoCal:** its 18 acquisition zones remain a separate explanatory state.

Temperature may qualify evidence when it exists on both sides, but it is not a
third coordinate and missing temperature must not invalidate RPM × MAP evidence.

## Geometric candidate

The offline candidate fits a weighted local affine plane around a target:

`Tinj = center + slope_rpm * ΔRPM + slope_map * ΔMAP`

The fit exposes the local center, gradients, evidence mass, independent sessions,
distance and chronological boundary. It is useful even when prediction promotion
fails: it explains direction, detects under-covered geometry and supports future
intervention matching.

Only observations with `training.order < target.order` may participate. Local
weight preserves `window_count`; independent sessions are reported separately.

## Promotion gate

The current RED neighbor predictor remains the anchor. A geometric candidate may
change Android prediction only after a chronological blind holdout proves:

- zero leakage;
- median error no worse than RED;
- P90 no worse than RED;
- P95 no worse than RED;
- no material loss of supported geometry.

Failure means `DEFER`, not manual selection of a favorable metric. The geometric
field remains offline diagnostic and RED remains runtime fallback.

## Learning screen

The detail panel must answer, in this order:

1. **Onde:** the RPM × MAP condition.
2. **Gasolina esperada:** the equivalent gasoline Petrol Inj.
3. **GNV observado:** the Petrol Inj. observed while on GNV.
4. **Diferença:** direction and percentage.
5. **Por que confiar:** support, dispersion, visits and sessions.
6. **O que isso significa:** whether the cell is observing or has a reviewable
   suggestion.

The screen must distinguish an exact observed pair from an aggregate cell
summary. No UI action writes automatically to the ECU.

## Safety

`AUTO_WRITE_ECU=false`. Suggestions remain manual, bounded and non-causal until
validated. ECU application continues to require human review, confirmation,
ACK and readback. `P_IMPROVE_PROVEN=false` and `VEHICLE_PROVEN=false` remain.

