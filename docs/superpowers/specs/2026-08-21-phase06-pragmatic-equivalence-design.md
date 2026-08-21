# Phase 06 Pragmatic Equivalence Design — RPM + MAP → Petrol Tinj

Status: DRAFT_FOR_OWNER_REVIEW

This design supersedes the 2026-08-20 Phase 06 physics/equivalence design **only after owner approval**. Until that approval, the older document remains historical input and no production behavior is changed by this file.

## 1. Goal

Make OMEGAS V8.2 learn and compare fuel equivalence quickly and continuously from the variables that are operationally authoritative for the owner:

- RPM and MAP locate the engine state;
- petrol injection time (`Tinj`) is the observed equivalence signal;
- gasoline establishes the reference surface;
- petrol `Tinj` observed while running on CNG is compared with that gasoline reference;
- all physically valid evidence contributes with continuous weight instead of being discarded by binary stability thresholds.

The design must preserve the V8.2 performance work already completed: bounded memory, revision-driven downstream recomputation, asynchronous/coalesced persistence, no heavy restore on startup, and no historical scan on each frame.

## 2. Explicit non-goals

The primary equivalence path does not require or gate on K2, K3, K4, coolant temperature, gas temperature, differential pressure, A/C state, or gas deadtime. Those signals may remain available as telemetry, provenance, diagnostics, reverse-engineering evidence, or future optional models, but they cannot block, alter, or manufacture the primary RPM+MAP→Tinj equivalence result.

The design does not add automatic ECU writes. Predictor and Advisor remain decision-support producers; manual write authority and existing ACK/readback protections remain unchanged.

Monte Carlo/bootstrap are offline validation oracles, never runtime algorithms on the TayTech unit.

## 3. Architectural choice

Use one incremental, compact equivalence surface inside the existing Learning flow. Do not create a second Learning engine and do not run KNN over the entire historical evidence set.

The runtime path is:

`Mp48Telemetry → MotorSampleAnalyzer → weighted observation → SignalLearningStore → EquivalenceSurface → Advisor → PredictorSurface → Map/Curve projection`

Responsibilities:

- `MotorSampleAnalyzer`: computes robust RPM/MAP/Tinj centers plus the stability diagnostics it already knows how to compute. It stops turning ordinary RPM/MAP/Tinj instability into a binary scientific rejection.
- `ContinuousWindowNovelty`: remains the authority for how much of an overlapping window is genuinely new evidence.
- `EquivalenceSurface`: bounded numeric accumulator keyed by an internal RPM×MAP lattice. It owns gasoline-reference moments and CNG residual moments; it never touches USB, UI, JSON hot paths, or writes.
- `SignalLearningStore`: remains the orchestration boundary. It applies novelty, updates the surface, advances material/scientific revisions, and exposes snapshots.
- `Advisor`: consumes published equivalence estimates and uncertainty; it does not rescan raw history on each frame.
- `PredictorSurface`: remains downstream. It projects already-published science onto calibration space and never becomes the authority for gasoline↔CNG equivalence.
- `PredictorSpatialConfidence`: remains a projection-confidence tool for calibration-space interpolation. It does not gate the primary RPM+MAP matcher.

## 4. Evidence admission: valid evidence gets weight, not PASS/FAIL

Hard-zero remains only for observations that are not physically meaningful for this comparison: invalid/implausible telemetry, engine off, cutoff, unresolved fuel transition, severe continuity loss, or missing/invalid RPM, MAP, or petrol Tinj.

For an otherwise valid window, define normalized instability ratios using the diagnostics already produced by the analyzer:

- RPM center shift / RPM center reference;
- RPM oscillation / RPM oscillation reference;
- MAP center shift / MAP center reference;
- MAP oscillation / MAP oscillation reference;
- petrol Tinj center shift / Tinj center reference;
- petrol Tinj oscillation / Tinj oscillation reference.

Let `r` be the maximum finite normalized ratio. Runtime stability weight is:

`w_stability = 1 / (1 + r²)`

This deliberately removes the discontinuity at the legacy threshold. A point just below and just above the old limit has nearly the same scientific influence.

Pressure, temperatures, A/C state, K2/K3/K4 and deadtime are excluded from this primary weight.

## 5. Novelty and independence

The existing `ContinuousWindowNovelty` output remains binding:

`w_novelty = newFrames / totalFrames`

A fully duplicated window has zero new scientific weight because it is the same evidence, not because evidence was discarded. Overlapping windows contribute only in proportion to new physical frames.

Base runtime weights:

- CNG: `w = w_stability × w_novelty`
- gasoline: `w = w_stability² × w_novelty`

The extra gasoline exponent is intentional. Gasoline defines the reference ruler, so unstable gasoline evidence is preserved but gains authority more slowly. This matches replay findings in which low-weight rejected CNG evidence improved held-out behavior while indiscriminately letting unstable gasoline evidence build the initial baseline degraded prediction quality.

These formulas are policy candidates derived from the current corpus, not OEM truths. Offline replay/bootstrap must falsify them before release.

## 6. Continuous surface and bounded state

The internal storage lattice is RPM×MAP. The initial engineering resolution is approximately 80 RPM × 0.02 bar, based on the replay spike; this is a storage resolution, not an acceptance tolerance.

Each observation is distributed bilinearly over at most four neighboring lattice points. No evidence must land exactly on a cell center.

Each lattice point stores bounded numeric moments, not an unbounded sample list:

- `sumW`
- `sumW2`
- `sumWTinj`
- `sumWTinj2`
- material revision / last update metadata needed for invalidation

Optional bounded robust tails may be retained only where an existing bounded helper is reused and its memory ceiling remains explicit. Raw sample/provenance history is not allowed to grow with driving time in the hot state.

From the moments:

`T_hat = sumWTinj / sumW`

`variance = max(0, sumWTinj2 / sumW - T_hat²)`

`ESS = (sumW²) / sumW2`

The same compact pattern is used for CNG residual summaries once a gasoline reference exists.

## 7. Primary equivalence and uncertainty

For a CNG observation at `(rpm,map)`, query the gasoline surface locally and obtain:

- estimated gasoline petrol Tinj `T_ref`;
- local reference spread;
- effective support / ESS;
- spatial support distance;
- revision/provenance metadata.

Primary relative error is:

`e = (T_cng_petrol - T_ref) / T_ref`

Retain both relative error and absolute milliseconds for audit/export.

Runtime uncertainty is analytic and cheap. It combines at least:

- uncertainty of the gasoline reference derived from local spread, ESS, and support distance;
- uncertainty/repeatability of the CNG observation derived from the same RPM/MAP/Tinj stream;
- any justified interpolation-distance term.

Combination is root-sum-square when the terms are treated as independent approximations:

`u_total = sqrt(u_ref² + u_cng² + u_match²)`

The offline oracle must verify calibration/coverage of this approximation. No sensor-specific noise floor or ECU resolution may be invented; any floor must come from protocol evidence or corpus measurements.

The scientific decision uses error and uncertainty directly. A UI confidence percentage may be derived for presentation, but it is not the authoritative decision variable.

Initial operational deadband remains approximately ±2% as a policy target to be validated against the corpus, not a universal statistical guarantee.

A candidate is actionable when its useful margin is positive:

`usefulMargin = abs(e) - u_total - deadband`

There is no fixed minimum count such as 6 or 10 samples. Clean independent evidence can become useful quickly; repeated correlated frames gain diminishing influence through novelty/ESS.

## 8. Gasoline authority and provisional evidence

The system does not need an object-state machine such as `PROVISIONAL → CONFIRMED` with arbitrary visit counts. Promotion is represented continuously by accumulated weight, coherence, and uncertainty.

Unstable gasoline evidence is never silently deleted. Its squared stability weight makes it unable to dominate a new reference region by itself. Stable neighboring evidence naturally raises support and lowers uncertainty when it agrees.

A gasoline region with only weak/provisional evidence may be observable but must not create high-confidence extrapolation or an actionable CNG correction. This is expressed through uncertainty/support, not a binary sample-count gate.

## 9. Predictor integration and closure

The equivalence surface and Predictor solve different questions:

- Equivalence: “At this RPM/MAP, what petrol Tinj does gasoline establish, and how certain is that estimate?”
- Predictor projection: “Given observed correction evidence, how far can that result be projected into calibration space?”

`PredictorSpatialConfidence` must therefore not gate the primary equivalence path with convex-hull or trajectory-count requirements. Those requirements may remain valid for extrapolating a calibration target into unobserved K-map space.

`PredictorSurface` remains a pure downstream snapshot builder: no USB, no writer, no raw-history scan, no duplicate confidence producer. Its revision token must change only when material upstream science or the confirmed map changes.

Predictor closure criteria for the related workstream:

- no second equivalence surface inside Predictor;
- no full historical scan per telemetry frame;
- no environmental/K-factor prerequisite reintroduced into primary equivalence;
- direct observed cells and spatially predicted cells remain distinguishable;
- prediction confidence cannot exceed what upstream equivalence support and projection geometry justify;
- no automatic write path is introduced;
- duplicate payload/revision exits remain preserved.

Read-only characterization, test-gap inventory, and performance profiling of Predictor may proceed independently while this design awaits approval. Any Predictor implementation that depends on the new equivalence-output contract waits for this spec approval and the implementation plan.

## 10. Persistence and migration

Recommended migration policy, requiring owner approval with this spec:

1. Do not reinterpret old environmental/K-factor physics as new primary science.
2. Preserve existing accepted gasoline regions by seeding the new surface once at restore/migration time with explicit `LEGACY_ACCEPTED_PETROL_REGION` provenance and conservative weight derived only from already-persisted support/quality fields.
3. Do not fabricate raw samples that no longer exist.
4. New live evidence immediately uses the new weighting model and can refine or outweigh the seed through accumulated support.
5. Existing CNG comparisons tied to a previous calibration epoch remain governed by the current live-only/reset rules; no stale CNG epoch is resurrected merely to populate the new surface.
6. Migration is versioned and auditable. If a stored state cannot be converted without inventing missing information, quarantine that part and preserve it for audit rather than guessing.

This preserves useful gasoline knowledge without claiming unavailable historical detail and avoids a forced cold-start solely because the statistical representation changed.

## 11. Performance invariants

The optimized V8.2 architecture is binding.

Runtime constraints:

- surface update is O(1), touching at most four lattice points;
- a primary reference query inspects a fixed, small local neighborhood; the implementation target is no more than roughly 16 compact support points unless a benchmark justifies another bound;
- no KNN or sort over the complete historical region list in the telemetry hot path;
- hot state uses primitive arrays/compact typed structures, not per-frame `JSONObject`, `List`, or map construction;
- JSON exists at status/export/persistence boundaries, not in per-frame scientific arithmetic;
- Advisor refresh remains revision-driven and executes off the telemetry/UI thread;
- persistence remains material-change-driven, asynchronous, and coalesced;
- restore remains off startup/UI critical paths;
- persisted provenance is bounded independently of scientific aggregates;
- memory use must be bounded by lattice dimensions, not session duration.

A benchmark must compare the new branch to the current optimized V8.2 baseline. Any material regression in ingest p95/p99 latency, allocation rate, advisor wakeups, snapshot frequency, or memory slope is a release blocker until explained and corrected. A 10% repeated-regression threshold may be used as an engineering alarm, but “no unbounded growth/no UI-thread blocking” is the harder invariant.

## 12. Offline replay, bootstrap, and Monte Carlo oracle

The validation corpus includes the real OMEGAS/V8.0 sessions already inventoried, especially gasoline+CNG sessions. Portmon byte captures are protocol evidence unless separately decoded; they are not treated as semantic Learning decisions merely because they are large.

Compare at least:

- current hard-gate behavior;
- continuous weighting candidate;
- gasoline-squared weighting candidate;
- bounded local-neighborhood alternatives where needed.

Use session-level/trajectory-level holdouts so a trip does not validate itself. Report median, P90 and P95 gasoline-reference error, supported coverage, time-to-first-useful-reference, time-to-first-actionable CNG residual, false-action rate inside the deadband, uncertainty interval coverage, ESS inflation under repeated frames, and memory/performance cost.

Bootstrap should resample real sessions/trajectories when estimating robustness of metric differences. Monte Carlo may perturb measured RPM/MAP/Tinj within empirically supported noise distributions to stress uncertainty calibration, but synthetic assumptions may not replace the real holdout result.

The previous spike values around 80 RPM × 0.02 bar and cross-session median/P90/P95 reference errors are benchmark evidence, not release guarantees. The implementation plan must rerun the oracle from a reproducible corpus manifest before freezing runtime constants.

## 13. Verification and release gates

TDD is required for implementation slices. Verification is local/ephemeral-first; GitHub Actions are not used.

Required proof classes:

1. Unit: stability weight continuity/monotonicity, novelty composition, gasoline/CNG asymmetry, bilinear mass conservation, weighted moments, ESS, uncertainty arithmetic, hard-zero states.
2. Contract: pressure/temp/K2/K3/K4/deadtime cannot gate or alter primary equivalence; only RPM+MAP locate the state and petrol Tinj forms the error.
3. Migration: accepted gasoline memory is preserved without fabricated raw history; stale CNG epochs are not resurrected; incompatible state is quarantined explicitly.
4. Replay: held-out real sessions show recovered useful evidence without material degradation of reference error/false-action behavior.
5. Oracle: analytic uncertainty is calibrated against bootstrap/Monte Carlo/holdout behavior within the chosen acceptance envelope.
6. Performance: bounded memory, fixed-neighborhood update/query, revision-gated Advisor, coalesced persistence, and no hot-path historical scan.
7. Predictor: downstream separation, projection confidence discipline, revision dedupe, no automatic write.
8. Integrated: fresh exact-source verification maps every acceptance criterion to executable evidence and records any unverified hardware-only claims separately.

Formal Phase 06 close still requires the project’s independent normative audit and distinct meta-audit after implementation and fresh verification.

## 14. Failure behavior

The system fails by lowering authority/raising uncertainty, not by fabricating precision.

- No gasoline support nearby: preserve CNG observation/provenance if useful, but do not manufacture a comparison target.
- Weak gasoline-only support: expose low-support reference state; do not make it actionable merely from count.
- High local Tinj dispersion: widen uncertainty; do not hide the mode by averaging to false precision.
- Persistent multi-modality at the same RPM/MAP: first use robust dominant-mode/continuity handling; split latent regimes only if real corpus evidence proves the single surface insufficient. A/C state is not made mandatory preemptively.
- Telemetry/fuel discontinuity: hard-zero that comparison window and restart continuity as already protected by the analyzer.
- Persistence/restore failure: telemetry remains independent; no synchronous recovery work is moved onto the UI/startup critical path.

## 15. Governance and scope

This design belongs to parent project `OMEGAS_V8_2` Phase 06. The separate `OMEGAS_GNV_EQ` campaign remains its own clean-room scientific project with Linear-controlled execution and must not be mutated by app-code work. Its log-derived evidence may inform owner decisions only through explicit, provenance-preserving handoff; app implementation must not rewrite that project’s frozen scientific scope.

The existing Phase 06 Notion/Linear wording that makes K2/K3/K4/deadtime/environment central to operation is obsolete relative to the owner decision. It must be reconciled only after this written spec is approved, so a rejected draft cannot silently rewrite normative execution authority.

## 16. Owner approval boundary

Approving this spec authorizes the implementation plan to treat the following as binding:

- primary equivalence is RPM+MAP→gasoline petrol Tinj, compared with petrol Tinj observed on CNG;
- physically valid evidence is continuously weighted rather than rejected by ordinary stability thresholds;
- gasoline uses more conservative weighting than CNG to protect the reference surface;
- environmental/K/deadtime signals are not primary gates or required context;
- runtime uses bounded incremental numeric state and preserves all current performance architecture invariants;
- accepted historical gasoline regions are preserved as conservative, provenance-marked migration seeds rather than discarded;
- Predictor remains downstream and is closed as a separate projection responsibility, not a competing equivalence engine.

Any future change that materially alters one of those bullets is an owner/architectural decision and must stop the dependent workstream for explicit authorization while independent safe work may continue.
