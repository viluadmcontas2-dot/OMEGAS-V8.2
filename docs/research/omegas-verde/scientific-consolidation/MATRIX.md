# OMEGAS VERDE — Scientific Coverage Matrix

Baseline: `d51b26d0f0886c24ad71a69e9e4801b8a4b26181`  
Tracker: #50  
Status: execution in progress

| Discovery | Runtime requirement | Existing seam | Evidence/test | Initial status | Action | Final status |
|---|---|---|---|---|---|---|
| F2 is frozen prior | Exact approved F2 coefficients; no runtime refit | `AdaptivePetrolReference.f2` | adaptive-reference tests | IMPLEMENTED | verify regression | PENDING |
| S carry-forward | Previous accepted S survives into later session/context | adaptive reference path | new `AdaptivePetrolScaleStateTest` | NOT IMPLEMENTED | add persisted carried scale | PENDING |
| S cautious update | Early/small cluster cannot aggressively reset S; sustained evidence can move it | adaptive reference path | new scale-state tests | NOT IMPLEMENTED | bounded evidence-dependent update | PENDING |
| Local residual needs curvature | Anisotropic local quadratic residual over real gasoline evidence | `AdaptivePetrolReference` | existing + new regression | IMPLEMENTED | verify | PENDING |
| Continuous gasoline reference | GNV can get reference without exact discrete petrol pair | `AdaptivePetrolReference` + `LearningSnapshotReconciler` | #48 regression | IMPLEMENTED | verify | PENDING |
| Prediction != evidence | Interpolation/prediction cannot increment real evidence | reconciler/store | #48 regression | IMPLEMENTED | verify | PENDING |
| Later physical petrol supersedes prediction | Existing adaptive comparison can be refreshed to direct reference without duplicate vote | `LearningSnapshotReconciler` | #48 refresh regression | IMPLEMENTED | verify | PENDING |
| Gasoline geometry = RPM×MAP | Reference selection does not use Petrol Inj as matching dimension | `PetrolReferenceSelector` / adaptive reference | equivalence tests | IMPLEMENTED | verify | PENDING |
| Mapa K geometry = RPM×Petrol Inj | Local residual is distributed on physical K map geometry | `ContinuousLearningMath` / advisor | advisor tests | IMPLEMENTED | verify | PENDING |
| Curve global before Map residual | Remove supported global trend before local residual | `AssistedCalibrationAdvisor.globalCurve/residualMap` | advisor tests | IMPLEMENTED | verify | PENDING |
| Calibration state is Curve+Map material state | Same material values => same scientific state across sessions; one value change => new state | `CalibrationStateV7`, `V7SessionRuntime` | new material-state identity tests | PARTIAL | add deterministic material state ID; preserve revisions | PENDING |
| GNV evidence belongs to confirmed calibration state | Post-change GNV evidence cannot mix with prior state | `cngEvidenceByRevision` | runtime tests | PARTIAL | bind to material state + transition evidence | PENDING |
| Readback defines successful intervention | No causal transition on failed/missing readback | `V7SessionRuntime.applySuggestionToEcu` | runtime tests | IMPLEMENTED/PARTIAL | persist transition receipt | PENDING |
| First correction ≈ 0.75 | No visit-count escalation | `AssistedCalibrationAdvisor` | #48 regression | IMPLEMENTED | verify | PENDING |
| ~0.90 only after causal confirmation | Readback + post-state evidence + correct response required | runtime/advisor | new `CausalSuggestionFractionTest` | NOT IMPLEMENTED | add minimal causal-response state | PENDING |
| Do not relearn gain from scratch | Use established prior response; no opaque learner | advisor/runtime | code audit | IMPLEMENTED BY POLICY | verify | PENDING |
| Failed TRUST is not write authority | Confidence is metadata/readiness, not automatic permission | stability/advisor/manual writer | code audit | IMPLEMENTED | verify | PENDING |
| No auto-write | Human confirmation + ACK/readback mandatory | writer/runtime | writer tests | IMPLEMENTED | verify | PENDING |
| Gasoline collectible at any time | Before/after GNV, suggestions and readback | learning stores/runtime | #48 regression | IMPLEMENTED | verify | PENDING |
| No MAP≈0.775 hardcode | Structural weakness handled by local residual/uncertainty, not special spline | adaptive reference | code audit | IMPLEMENTED | verify | PENDING |
| No opaque ML | Transparent math only | all learning seams | code audit | IMPLEMENTED | verify | PENDING |
| Learning-map primary value must be robust | Single new comparison cannot make cell jump +1.7→+15→-10 if robust state exists | `LearningStabilityV7` + `learning.js` | new volatility test/contract | PARTIAL | use robust recent/consolidated precedence | PENDING |
| REVALIDATING preserves consolidated truth | Recent contrary trend shown separately until repeatable | `LearningStabilityV7`, `V7SessionRuntime`, UI | stability tests | PARTIAL | make primary display semantics explicit | PENDING |
| Independent visits matter more than raw frame count | Stability promotion driven by effective/unique visits and spread | `LearningStabilityV7` | stability tests | IMPLEMENTED | verify | PENDING |
| Spatial prior confidence should not become hardcoded truth | Historical weak zones inform uncertainty only when supported; no TRUST resurrection | adaptive quality/stability | code audit | N/A/REJECTED AS HARDCODE | preserve no special production map | PENDING |

## Baseline volatility diagnosis

Current UI comparison selection uses `consolidatedErrorPercent` when available, otherwise it can fall back to a single raw comparison selected per cell. Raw comparison objects do not reliably carry the score fields used by the JS `indexByCell` chooser; ties therefore allow later entries to replace earlier ones. During `LEARNING`, `LearningStabilityV7` already exposes a robust `recentErrorPercent`, but the primary cell can ignore it and show the raw visit instead.

This is the primary hypothesis for the observed high-frequency jumps. The fix must first use the robust state already present rather than add cosmetic EMA smoothing.

## Metrics contract

The focused volatility regression records:
- raw max jump;
- robust/primary max jump;
- sign flips;
- state sequence;
- effective visits / unique visits;
- whether a single outlier moved the consolidated primary;
- whether repeated coherent contrary evidence eventually promoted a new generation.
