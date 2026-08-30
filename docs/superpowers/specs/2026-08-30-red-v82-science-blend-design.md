# RED V8.2 Science Blend — Design

Date: 2026-08-30
WorkUnit: `RED-BLEND-001`
Issue: `#11`
Branch: `work/red-v82-science-blend`
Base: `hotfix/v8.0-red-performance@1be2048e1ca6fc736f6bf38ddcb86aa6329144b7`
RED baseline SHA for comparison: `b637f5fff19b1ece93f22d1fced9640618609a60`
State: `DESIGN_SPEC_READY_FOR_OWNER_REVIEW`

## 1. Human objective

Preserve the OMEGAS V8.0 RED fast-learning behavior and evolve its scientific sophistication without importing the V8.2 implementation wholesale. The new branch must determine empirically, using the owner's real historical logs, whether the debated RPM×MAP/Tinj model is correct, where it fails, and whether a science layer can improve reliability without slowing the RED Fast Core.

The algorithm must remain useful quickly. Scientific rigor may restrict transfer/actionability when evidence is insufficient, but it must not erase valid dense local evidence.

## 2. Isolation and authority

- The RED source branch remains untouched by this WorkUnit.
- This branch starts exactly from RED HEAD `1be2048...`.
- The functional/CI RED comparison anchor remains `b637f5...`.
- `work/wu-006-calibration-science-hardening` is research input only; it is not merged into this branch.
- Source code, tests and evidence committed to this branch are the engineering authority.
- Raw Google Drive logs remain private source evidence, not public repository content.
- UI/UX redesign is deferred until the motor/science contract is proven.

## 3. Frozen scientific correction

> **RPM×MAP define a região operacional. Todas as observações válidas nessa região contam integralmente para caracterizar a distribuição local de Tinj. Repetição consistente aumenta fortemente a certeza sobre essa distribuição. Sessão/época não desconta artificialmente essa evidência local; elas servem para medir persistência e transferência. Se Tinj apresentar dispersão ou multimodalidade incompatível dentro da mesma região, isso é tratado como evidência de estado oculto, transiente ou mudança de regime — nunca simplesmente esmagado numa média.**

This corrects an overly restrictive interpretation in which temporally correlated frames would be discounted for every purpose. The design separates two different scientific questions.

### 3.1 Local knowledge

Question: `What is the empirical distribution of Tinj in this RPM×MAP region?`

All valid observations contribute. The model must retain or derive at least:

- raw valid sample count / density;
- center (median and/or robust mean);
- dispersion;
- quantiles;
- stability over increasing N;
- support geometry around RPM×MAP;
- evidence of multimodality / multiple regimes.

A thousand valid, tightly concentrated observations in one region are strong local evidence even when acquired in one session.

### 3.2 Persistence and transfer

Question: `Does this local behavior persist outside the exact observed slice, and how far can it be transferred?`

Session, trajectory, epoch, distance from observed support, interventions and future hold-outs matter here. They may reduce transfer confidence or actionability but do not retroactively erase the local empirical distribution.

### 3.3 Hidden state rule

If the same narrow RPM×MAP region shows persistent broad or multimodal Tinj, do not average the modes into one false reference. First identify whether the split is explained by:

- transient acceleration/deceleration or cutoff/recovery;
- fuel transition;
- calibration epoch / MAP_K intervention;
- invalid/plausibility state;
- another already-recorded physical state.

Only promote an additional primary model dimension when falsification shows RPM×MAP is insufficient in that region. No dimensionality is added pre-emptively.

## 4. Existing RED behavior that must be preserved

The RED already provides the useful fast path:

- continuous learning;
- global trend over Petrol Injection time;
- local continuous RPM×MAP residual;
- `DIRECT`, `NEAR`, `GLOBAL_ONLY` support;
- physical gasoline reference chosen primarily by RPM+MAP;
- bounded interpolation/extrapolation;
- write-free prediction;
- manual calibration application only;
- confirmation + ACK + readback on the ECU write path;
- low-cost/offline Android runtime.

The RED currently also contains mechanisms such as dwell saturation, novelty/effective evidence, visit-based confidence and visit-dependent correction fractions. These mechanisms are not assumed wrong. They are hypotheses to test for role separation:

- local distribution precision;
- persistence/return evidence;
- transfer/actionability.

The blend must not remove a conservative mechanism until a RED test demonstrates exactly which scientific quantity it is suppressing incorrectly.

## 5. Proposed architecture

```text
TELEMETRY
   |
   v
RED FAST CORE
   |-- global Petrol Inj trend
   |-- local RPM×MAP residual
   |-- DIRECT / NEAR / GLOBAL_ONLY
   |
   +------------------------------+
                                  |
                                  v
                     LOCAL DISTRIBUTION LAYER
                     Tinj | RPM, MAP
                     density / center / spread
                     quantiles / modes / stability
                                  |
                 +----------------+----------------+
                 |                                 |
                 v                                 v
          FAST LOCAL KNOWLEDGE              TRANSFER SCIENCE
                                             session / epoch
                                             walk-forward
                                             support distance
                                             drift / OOD
                                             causal MAP_K
                                             sensitivity
                                             risk / coverage
                                             P(improve)
                 |                                 |
                 +----------------+----------------+
                                  v
                           ACTIONABILITY GATE
                           suggestion or ABSTAIN
                                  |
                                  v
                                HUMAN
                                  |
                          confirm -> ECU -> ACK/readback
```

The science layer must not become a blocking synchronous dependency for telemetry ingestion or fast local learning.

## 6. Evidence model

No single scalar `confidence` may be the sole scientific authority. The runtime may expose a convenience confidence for UI, but the underlying decision must be reconstructible from explicit evidence dimensions.

Minimum conceptual dimensions:

1. `local_density`: number/mass of valid observations characterizing the region.
2. `local_precision`: concentration/stability of Tinj inside supported RPM×MAP.
3. `local_multimodality`: whether one center is a valid summary.
4. `support_distance`: DIRECT/NEAR/global transfer geometry.
5. `persistence_support`: evidence across independent returns/sessions/epochs.
6. `transfer_risk`: held-out risk for using local/global estimates outside direct support.
7. `causal_support`: whether intervention outcome supports a calibration effect.
8. `p_improve`: empirical probability of improvement only when calibrated; otherwise null.

## 7. Google Drive corpus policy

Private corpus root: `OMEGAS – Pacotes, Aprendizado & LOGS`.

Files already identified include gasoline, GNV, blind-test, Portmon and `.omegas` learning/K-history packages. Raw files can contain sensitive device/network metadata and therefore must never be committed to this public repository.

### 7.1 Derivation pipeline

1. Read raw packages from Google Drive/private execution context.
2. Verify raw file bytes by SHA-256.
3. Extract only scientific fields required for replay.
4. Remove secrets, network/device identifiers and unrelated metadata.
5. Assign privacy-safe deterministic logical session/epoch identifiers.
6. Preserve frame order and timestamps needed for temporal tests.
7. Preserve fuel, RPM, MAP, petrol_ms and only additional physical fields required for falsification.
8. Do not use `sample_state` or algorithm-produced acceptance labels as ground truth.
9. Emit deterministic compact fixture + manifest.
10. Commit only privacy-safe derived data and deterministic reconstruction/check code.

For transfer statistics, duplicate exports of the same logical session are deduplicated. For local-distribution characterization, valid physical frames are not discarded merely because they came from the same session.

## 8. Exploratory Drive findings — not yet PROVEN

Initial direct reads support, but do not prove, the central hypothesis:

### Gasoline examples

- ~850 RPM / 0.41 bar: N=1,793, Tinj mean ~2.683 ms, CV ~1.06%, P10 ~2.65 ms, P90 ~2.72 ms.
- ~900 RPM / 0.41 bar: N=965, CV ~1.06%.
- ~850 RPM / 0.42 bar: N=857, CV ~1.25%.
- exact 777 RPM / 0.43 bar: N=232, mean ~2.558 ms, std ~0.029 ms, CV ~1.13%.
- exact 938 RPM / 0.41 bar: N=161, CV ~1.24%.

### GNV examples

- ~1000 RPM / 0.42 bar: N=308, Tinj mean ~4.683 ms, CV ~1.94%.
- ~2000 RPM / 0.48 bar: N=138, mean ~5.542 ms, CV ~3.35%.
- ~1950 RPM / 0.47 bar: N=68, CV ~1.70%.

The canonical harness must search the complete derived corpus for counterexamples, especially high-RPM/high-load regions and multimodal regions.

## 9. Falsification-first hypotheses

### H1 — Local concentration
Dense valid observations in a narrow RPM×MAP region usually converge to a narrow Tinj distribution.

Failure: sufficiently supported regions remain broad or multimodal after known transients/regimes are separated.

### H2 — Repetition improves local precision
Increasing valid N in the same region materially stabilizes the local Tinj estimate. Gain may saturate but cannot be assumed negligible simply because frames are from one session.

Failure: additional valid observations do not improve out-of-sample local estimate stability or reveal systematic drift hidden by within-session repetition.

### H3 — Local precision and transfer are distinct
One-session dense evidence can be excellent local evidence while having limited persistence/transfer proof.

Failure: local-distribution metrics cannot be separated operationally from session/epoch effects.

### H4 — Multimodality means unresolved regime
Persistent separated Tinj modes at the same RPM×MAP indicate a missing regime/state or invalid aggregation.

Failure: modes are explainable solely as expected measurement distribution and one robust center predicts held-out data better.

### H5 — Science can stay off the RED hot path
The blend can calculate richer evidence without material degradation of RED telemetry/runtime behavior.

Failure: latency, CPU, allocations/memory or time-to-useful-learning regress beyond empirically frozen budgets.

### H6 — Blend must beat or safely tie RED
On blind future sessions/epochs the blend must improve error/risk calibration/abstention or preserve RED quality with better evidence semantics.

Failure: added sophistication worsens predictive error, useful coverage, learning speed or runtime performance without compensating proven benefit.

## 10. Test program in GitHub Actions

GitHub Actions standard hosted runners are the primary remote laboratory for this RED-derived line while the repository remains public and standard runners have no additional monetary cost. Paid/larger runners require explicit owner approval.

### Stage A — FAST

- governance contract;
- deterministic science fixture contract;
- unit tests;
- RED regression contracts;
- no automatic ECU write;
- no prediction-to-observation feedback path.

### Stage B — SCIENCE_LOCAL

For multiple RPM×MAP resolutions and exact-pair subsets where supported:

- N / density;
- median/robust mean;
- std/MAD/IQR/quantiles;
- coefficient of variation where meaningful;
- estimate convergence as N grows;
- within-region bootstrap or sequential stability;
- multimodality tests/cluster evidence;
- compare gasoline and GNV distributions separately;
- stratify high-RPM/high-load regions.

No arbitrary pass threshold is frozen before the empirical corpus distribution is reported. The first run establishes candidate limits; subsequent validation uses held-out data before thresholds become production gates.

### Stage C — SCIENCE_TRANSFER

- same-region comparison across logical sessions;
- same-region comparison across calibration epochs;
- blind walk-forward by whole future sessions/epochs;
- DIRECT vs NEAR vs GLOBAL_ONLY risk and coverage;
- local-density contribution separated from independent-return contribution;
- drift and contradiction detection.

### Stage D — CAUSAL

Using only confirmed manual MAP_K changes with intervention identity and ACK/readback evidence:

- reconstruct pre/post epochs;
- freeze comparison support;
- estimate direction/magnitude only where contexts are comparable;
- calibrate sensitivity;
- reject ambiguous interventions;
- no causal claim from simple temporal correlation.

### Stage E — RISK / P(IMPROVE)

- held-out risk by evidence stratum;
- coverage vs error;
- interval calibration;
- P(improve) only from held-out causal outcomes;
- `pImprove=null` and ABSTAIN when calibration is unsupported.

### Stage F — FALSIFICATION/OOD

- synthetic and historical counterexamples;
- multimodal same-region cases;
- sparse support;
- contradictory sessions;
- calibration drift;
- out-of-range RPM/MAP;
- fuel transition/cutoff/gaps/invalid telemetry;
- neighboring-region extrapolation stress.

### Stage G — PERFORMANCE_AB

Run RED baseline and Blend against the same deterministic fixture and workload. Measure at minimum:

- throughput;
- wall-clock latency per replay/update;
- time-to-useful-local-reference;
- time-to-actionable-suggestion when scientifically supported;
- JVM/runtime allocations or best available deterministic proxy;
- memory footprint/proxy;
- artifact size;
- output accuracy/coverage.

Performance budgets are frozen from measured RED baseline distributions plus an explicitly documented tolerance. They are not invented before benchmarking.

### Stage H — ANDROID

After necessary science gates:

- `testDebugUnitTest`;
- `lintDebug`;
- `assembleDebug`;
- artifact and evidence receipt.

Use Gradle cache, fail-fast dependencies and `concurrency.cancel-in-progress`. Docs-only changes must not trigger unnecessary Android builds. No raw Drive secrets or credentials are placed in workflow YAML or public artifacts.

## 11. Gates

1. `G0_RED_BASELINE_FROZEN`
2. `G1_DESIGN_CONTRACT`
3. `G2_PRIVACY_SAFE_CORPUS`
4. `G3_LOCAL_TINJ_DISTRIBUTION`
5. `G4_MULTIMODALITY_FALSIFICATION`
6. `G5_PERSISTENCE_TRANSFER`
7. `G6_BLIND_WALK_FORWARD`
8. `G7_GLOBAL_LOCAL_TUNING`
9. `G8_CAUSAL_MAP_K`
10. `G9_SENSITIVITY`
11. `G10_RISK_COVERAGE`
12. `G11_P_IMPROVE`
13. `G12_OOD_FALSIFICATION`
14. `G13_PERFORMANCE_VS_RED`
15. `G14_FULL_ANDROID`
16. `G15_APK_CANDIDATE`
17. physical vehicle validation later and separately.

Every gate may fail. A failed hypothesis is a valid scientific outcome and must not be massaged into a pass.

## 12. Promotion rule

The Blend is promoted only if evidence shows a material scientific benefit while preserving RED fast-path performance within frozen budgets. If a proposed sophistication fails to improve held-out behavior or materially hurts runtime, it is removed or kept offline-only.

The RED baseline remains a valid fallback throughout the entire WorkUnit.

## 13. Safety and non-scope

- no automatic ECU write;
- no removal of human confirmation, ACK or readback;
- no physical fuel-economy claim from software/replay;
- no UI/UX redesign in this WorkUnit;
- no V8.2 branch merge;
- no raw Drive corpus in public GitHub;
- no paid/larger Actions runners without explicit approval;
- no new model dimension merely because it is theoretically plausible.

## 14. Owner review gate

Implementation begins only after the owner approves this written design. After approval, the implementation plan must be written before code changes. The first implementation milestone is a deterministic privacy-safe corpus/harness plus tests that can falsify H1-H4 without modifying production runtime behavior.
