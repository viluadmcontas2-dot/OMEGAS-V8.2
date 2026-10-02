# OMEGAS VERDE — Scientific Consolidation Design

**Status:** APPROVED BY OWNER  
**Approved:** 2026-09-15  
**Repository:** `viluadmcontas2-dot/OMEGAS-V8.2`  
**Branch:** `OmegasVerde`  
**Baseline:** `d51b26d0f0886c24ad71a69e9e4801b8a4b26181`  
**Tracker:** #50

## 1. Goal

Finish the already-established scientific engine inside the existing Verde runtime, without creating a parallel engine, and eliminate the excessive volatility of the learning-map cell values before generating another APK.

The implementation must preserve the current application architecture and use existing seams wherever possible: `AdaptivePetrolReference`, `LearningSnapshotReconciler`, `LearningStabilityV7`, `V7SessionRuntime`, `AssistedCalibrationAdvisor`, `AdvisorSuggestionAdapterV7`, and the existing manual writer/readback path.

## 2. Non-negotiable scientific invariants

1. Gasoline equivalence geometry is `RPM × MAP`.
2. Mapa K addressing geometry is `RPM × Petrol Injection`.
3. F2 is an immutable prior, not a measurement or universal truth.
4. Runtime gasoline reference is conceptually `Tref = S * F2 + local residual`.
5. `S` uses carry-forward from the prior accepted scale and updates cautiously; an arbitrary early gasoline window must not aggressively reset it.
6. Residual learning uses real gasoline evidence only. Prediction/interpolation never becomes evidence.
7. Physical gasoline evidence arriving later supersedes a prior adaptive estimate.
8. GNV error is measured against the gasoline reference; GNV never redefines that reference.
9. Curve K captures the global Petrol-Inj trend first; Mapa K receives the residual left after global removal.
10. Calibration state is a property of the combined confirmed Curve K + Mapa K state, not merely a drive/session.
11. First manual correction is approximately `0.75 × supported error`; escalation toward `0.90` is allowed only after a later confirmed calibration state provides causal evidence that the response direction/gain is consistent.
12. No automatic ECU write. Human prepare/review/confirm/ACK/readback semantics remain mandatory.
13. The failed TRUST confidence experiment is not an automatic authorization gate. Confidence remains metadata/abstention information.
14. No opaque ML, no hardcoded spline for MAP≈0.775, no universal learned gain curve, and no assigning total GNV error directly to Mapa K.

## 3. Existing architecture to reuse

### Adaptive gasoline reference
`AdaptivePetrolReference` already contains frozen F2 coefficients and an anisotropic local quadratic residual candidate using roughly 240 rpm / 0.06 bar, up to 60 support regions and ridge regularization. `LearningSnapshotReconciler` already prefers physical gasoline references and uses the adaptive reference only as fallback.

### Stable learning memory
`LearningStabilityV7` already implements a robust state machine over independent visits using weighted median, MAD, direction consensus, `LEARNING`, `CONSOLIDATED`, and `REVALIDATING` states. This is the preferred stabilization mechanism; do not add arbitrary cosmetic EMA smoothing if this robust state can be used correctly.

### Calibration state and readback
`CalibrationStateV7` already contains complete Curve K + Mapa K material values plus a revision pair. CNG evidence is grouped by calibration revision and manual application already requires writer success plus readback. Extend this existing state identity rather than create a second ledger.

### Suggestion decomposition
`AssistedCalibrationAdvisor` already computes a global curve first and then a residual map. `AdvisorSuggestionAdapterV7` and `V7SessionRuntime` already persist/revalidate suggestions and keep application manual.

## 4. Root-cause hypothesis for learning-map volatility

The UI comparison layer currently selects a per-cell raw comparison when there is no `consolidatedErrorPercent`. Multiple comparisons commonly have no `confidence/samples/weight` fields consumed by the JS selector, making later entries replace earlier ones. During `LEARNING`, the displayed value can therefore become effectively a single latest visit, even though `LearningStabilityV7` already has a robust recent center.

This can produce sequences such as `+1.7% -> +15% -> -10%` without the underlying scientific memory actually justifying such jumps.

This hypothesis must be reproduced and measured before production changes. Other possible contributors (adaptive-reference movement, rebinning, epoch mixing, low effective support) must be checked by the probe, not assumed away.

## 5. Stable cell semantics

The learning map must distinguish three concepts:

- **instant/raw:** latest comparison evidence, useful for diagnostics only;
- **recent robust center:** weighted-median/MAD summary while still learning or revalidating;
- **consolidated:** stable scientific value retained until repeated contrary evidence promotes a new generation.

Presentation policy:

- `LEARNING`: show robust recent center, never a single arbitrary comparison when a robust summary exists.
- `CONSOLIDATED`: show consolidated center.
- `REVALIDATING`: keep showing consolidated center as the primary value and expose recent center separately as a revalidation trend.
- A lone contradictory visit must not move the primary displayed consolidated value.
- Repeated coherent contrary evidence must be able to promote a new generation and then move the primary value.

This is causal smoothing through evidence state, not visual smoothing.

## 6. Scale S policy

The current per-query median scale is insufficient because it can reset from whatever gasoline happens to be locally available. Introduce a small explicit scale state inside the existing persisted learning/session model rather than a new engine.

Required behavior:

- bootstrap to the frozen prior default only when no previous accepted scale exists;
- carry the previously accepted scale across a new session/calibration context;
- estimate a candidate scale only from real gasoline evidence;
- update toward the candidate with a bounded evidence-dependent step;
- one small/early gasoline cluster cannot move the carried scale aggressively;
- enough coherent gasoline evidence can gradually move the accepted scale;
- scale metadata includes accepted value, candidate value, support count/visits and update provenance;
- GNV never updates scale.

Exact bounds should be derived from existing scientific evidence and regression tests, not a broad hyperparameter search. The key acceptance property is robustness against the previously observed early-window errors while still converging when evidence is sustained.

## 7. Calibration-state identity and causal response

Do not add a parallel `CalibrationStateLedger`. Strengthen `CalibrationStateV7` with a deterministic material-state identity derived from the complete confirmed Curve K + Mapa K values.

The runtime must be able to answer:

- which material calibration state produced each GNV evidence group;
- which confirmed manual intervention led from state A to state B;
- whether post-readback evidence under state B moved error in the expected direction relative to state A.

Revision counters remain useful for compatibility, but scientific causality uses material state identity plus confirmed readback.

## 8. 0.75 -> 0.90 policy

The existing `0.75` first step remains the default. A suggestion may use a second-stage fraction near `0.90` only when:

1. a prior manual suggestion was actually applied and read back successfully;
2. post-change evidence exists under the resulting confirmed material state;
3. the same global/local target can be compared without mixing unrelated calibration states;
4. observed response has the expected direction and a plausible gain around the established prior;
5. uncertainty/readiness does not indicate revalidation/conflict.

Otherwise remain at `0.75` or abstain. No visit-count-only escalation.

## 9. Scientific coverage matrix gate

Before APK generation maintain a matrix with columns:

`Discovery | Runtime requirement | Existing seam | Test/evidence | Initial status | Action | Final status`

Every behavior-changing scientific finding must end as `IMPLEMENTED+TESTED`, `ALREADY_IMPLEMENTED+VERIFIED`, or `REJECTED/N/A` with explicit scientific justification.

## 10. REPL-first stability metrics

A reproducible probe must feed chronological evidence into the existing projection/stability path and report at minimum:

- raw per-update cell value;
- primary displayed value;
- recent robust center;
- consolidated value;
- maximum absolute jump between successive primary values;
- median absolute deviation of the displayed series;
- number of sign flips;
- effective/unique visit support;
- state transitions (`LEARNING/CONSOLIDATED/REVALIDATING`).

The probe must include a synthetic sequence containing ordinary values plus isolated positive/negative outliers and, where available, a replay from existing snapshot data.

Success is not zero movement. Success is: isolated noise cannot cause large primary-value jumps, while repeated coherent evidence can move the state.

## 11. Compatibility and safety

- Gasoline collection remains valid at any time: before GNV, after GNV, with pending suggestions, and after readback/new state.
- Existing MP48 decoding, fuel resolver, storage transport, UI layout, K writer protocol and readback safety are not redesigned.
- No automatic writer is introduced.
- Predictions do not increment samples, visits, sessions or confidence evidence.
- Existing historical snapshots must remain decodable or migrate deterministically.

## 12. Definition of Done

Another APK may be produced only when:

1. the scientific coverage matrix has no unresolved behavior-critical item;
2. the volatility probe demonstrates materially reduced pathological jumps with preserved responsiveness;
3. focused RED/GREEN tests cover every changed behavior;
4. broad unit tests and `assembleDebug` pass on the final remote SHA;
5. diff audit shows no unrelated architecture/UI/writer/protocol rewrite;
6. artifact and SHA-256 are published by CI;
7. issue #50 contains final metrics, SHAs, CI evidence and any remaining non-blocking limitation.
