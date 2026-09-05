# OMEGAS Blue Runtime Convergence — Implementation Plan

**Goal:** finish the RED→Blue hard cut so one causal engine owns decisions, calibration writes are RPM-independent, sessions are durable/relevance-aware, Auto-Cal consumes Blue, and Learning is didactic.

**Architecture:** preserve proven MP48/serial/manual writers; replace only decision/session/UI orchestration layers. Session hot path stays private and fast; user vault is immutable promotion. CI fails early on legacy/drift before Android build.

## Task 1 — Make convergence failures explicit
Files: `tests/test_blue_runtime_convergence_contract.py`, `app/src/test/.../CalibrationWriteSafetyPolicyTest.kt`.
Write failing assertions for legacy facade, placeholder Auto-Cal binding, retention <20/default !=30, missing logical segments/vault, and any RPM threshold in write-authority paths. Verify FAST fails for these reasons.

## Task 2 — Repair the existing red FULL failure
Delete stale AutoMatch test sources whose production engines were intentionally removed. Run targeted/full unit compilation and confirm failure moves to real remaining incompatibilities rather than dead tests.

## Task 3 — Remove write RPM gate everywhere
Trace Map K/Curve K/Auto-Cal bridges/planners. Remove only RPM authorization checks; retain RPM as evidence/cell location and retain service/USB/ready/fresh telemetry/confirmation/ACK/readback. Test a safe 5000 RPM status.

## Task 4 — Hard cut decision legacy
Move remaining callers from `V7EquivalenceEngine`/advisor/predictor math to `BlueCausalEngine`/Blue projections. Delete compatibility facades and dead decision classes/tests/assets after references are gone. Strengthen structural gate.

## Task 5 — Bind Auto-Cal to Blue proposal
Expose current Blue comparison/gain/proposal from runtime/service and bridge it to Auto-Cal. Replace placeholder responses with proposal or explicit measurement reason. No independent formula in bridge/UI.

## Task 6 — Rebuild session lifecycle
Implement pure relevance policy first: PROTECTED on confirmed calibration/readback/explicit protection; VALID on ≥20 telemetry frames spanning ≥5s; otherwise PROBE. Retention defaults 30 useful sessions and config min 20. USB reconnect while service survives creates a segment boundary, not a new logical session.

## Task 7 — Add public session vault
Persist SAF tree URI; promote immutable qualified closed sessions from private spool; retain spool until verified copy succeeds. Surface pending/failed vault status. Recording never depends on vault availability.

## Task 8 — Simplify Learn semantics
Primary grid layers: Gasolina, GNV, Desvio. Direct measured deviation must not silently fall back to prediction. Blue correction is a separate proposal card. Cell detail order: location, petrol reference, CNG observed, deviation, meaning, correction status; audit counts secondary.

## Task 9 — Convergence and exact-SHA verification
Run cheap structural/Spec Kit/drift gates, then full Python/Node/Kotlin tests, Android lint and APK. Remove remaining dead code revealed by references/CI. Record exact SHA and limits; do not claim vehicle economy without physical test.
