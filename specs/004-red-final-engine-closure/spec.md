# OMEGAS RED Final Engine Closure — Specification

Parent Issue: #30

## 1. Problem statement
OMEGAS RED exists to make manual GNV calibration useful from real vehicle evidence. The current branch contains the intended continuous-learning architecture but accumulated regressions: gasoline reference was incorrectly constrained to a 30-second pre-switch window, supported spatial regions can remain visually/algorithmically empty, Predictor behavior drifted away from its original role, the Learning UI exposes engineering internals, and new computation must not worsen live telemetry fluidity.

This spec closes the existing engine. It does not authorize a rewrite or a parallel calibration engine.

## 2. Canonical execution units
- #31 — core gasoline surface, physical equivalence, interpolation and causal K gain.
- #36 — Predictor + Learning UI.
- #32 — runtime/hot-path/backpressure.
- #33 — OBD witness/independent learner.
- #34 — consumption.
- #35 — final integrated verification/review/APK.
- #25 — physical vehicle gate, separate from software completion.

## 3. Permanent gasoline authority — #31
- Valid gasoline evidence contributes to a persistent physical surface `(RPM, MAP) -> expected Petrol Inj.`.
- Gasoline does not expire because seconds/hours/days elapsed.
- Gasoline does not expire because fuel switched to GNV.
- Gasoline does not expire because GNV calibration epoch changed.
- Gasoline does not expire because Curve K or Map K changed.
- A 30-second or other short temporal window MUST NOT decide whether gasoline reference exists.
- Physical compatibility and evidence quality remain mandatory. Removing the temporal gate MUST NOT weaken RPM/MAP geometry or quality gates.
- GNV measured deviation is `PetrolInj_GNV / PetrolInj_GasolineExpected - 1`.

## 4. Continuous surface and interpolation — #31
- Grid cells are a projection of continuous physical evidence, not the scientific authority.
- Supported internal regions may be estimated from surrounding observations even when no frame landed exactly at the query point/cell.
- Interpolation must support horizontal, vertical, diagonal and irregular 2D RPM×MAP neighborhoods.
- Use the existing continuous-learning mathematics and data path; do not introduce a competing engine.
- `OBSERVED` means sufficiently direct evidence.
- `INTERPOLATED` means a value inferred inside supported observed geometry.
- `UNSUPPORTED` means local support is insufficient.
- Interpolation must not aggressively extrapolate outside supported geometry.
- Provenance/support/confidence remain available for audit.

## 5. Map K and causal intervention — #31
- For current GNV evidence, physical Map K location is current `RPM_GNV × PetrolInj_GNV`.
- Gasoline target Petrol Inj. is not the current GNV Map K address.
- Curve K represents broad/global correction tendency; Map K represents local residual behavior in its physical geometry.
- Short temporal windows are allowed only for actual before/after calibration interventions used to estimate causal actuator gain.
- No fabricated actuator gain and no fallback gain 1.0.
- Deadband is an action policy; it must not erase measured evidence.

## 6. Predictor — #36
- Predictor is a fast estimated projection of the same learned authority, not a second scientific truth.
- Predictor may provide useful estimates before complete local observation when the query is inside defensible support.
- Predictor output is explicitly distinguishable from observed and interpolated evidence.
- Conceptual model remains `error_ratio = global_curve(petrol_target_ms) + local_residual(rpm,map_bar) + noise`.
- Global trend may inform sparse regions; local residual is support-bounded and cannot be copied across distant RPM×MAP regions.
- As coherent real evidence increases, prediction must converge toward observed evidence and uncertainty should decrease.
- Strong direct observation overrides conflicting prior prediction.
- Outside local support, output may be `GLOBAL_ONLY` or `UNSUPPORTED`; never fabricated local observation.
- Predictor never writes to ECU.

## 7. GNV calibration epochs — #31/#36
- Confirmed Curve K/Map K changes start a new affected GNV calibration state/epoch.
- Prior GNV can remain historical evidence but is not silently pooled as current state.
- Gasoline surface persists across these GNV epochs.

## 8. Learning UI — #36
Primary operation must show only what helps calibration:
- RPM/MAP context.
- gasoline expected Petrol Inj.
- GNV observed Petrol Inj.
- difference/deviation.
- confidence.
- origin/state (`medido`, `interpolado`, `predito` or unsupported equivalent).
- suggested adjustment when available.

The primary pane must not require the operator to parse `BlueCausalEngine`, epoch history, internal calibration state, provenance IDs, ACK/readback explanations or forensic implementation details. Those may remain in diagnostics.

Grid cells should be compact: principal numeric value/state, without repeating long labels such as `ms gasolina`/`ms no GNV` in every cell.

## 9. Runtime and telemetry — #32
- Surface interpolation and Predictor reconstruction must not run as heavy work on every telemetry frame.
- UI render/poll ticks must not rebuild full historical surfaces.
- Recompute on meaningful evidence/calibration revision, cache the result, and coalesce rapid updates where appropriate.
- Latest relevant state wins; stale work must not build an unbounded queue.
- WebView scheduler remains self-paced and non-overlapping.
- Evidence persistence remains coalesced/atomic and must not dominate polling.

## 10. OBD witness — #33
- GNV STFT learning remains autonomous from MP48 comparison math.
- OBD cannot fabricate gasoline evidence or measured gasoline↔GNV comparison.
- Fresh READY coherent witness may increase confidence.
- Missing, stale, insufficient or conflicting witness must not erase or recalculate valid MP48 measurement.
- LTFT is not a correction-math input.

## 11. Consumption — #34
Consumption remains a separate feature with its own tests. Changes in this closure must not conflate estimated pressure/volume with confirmed distance/refill consumption.

## 12. Calibration safety
- Learning, interpolation, Predictor and suggestions are observational/advisory.
- No automatic ECU write.
- Existing manual path remains prepare → review → confirm → write → ACK → readback.
- Ambiguous/unresolved address causes abstention.

## 13. Verification — #35
Before `READY FOR APK GENERATION`, the exact final SHA must prove:
- #31 acceptance scenarios, including old gasoline and 2D/diagonal interpolation.
- #36 sparse prediction, convergence, observation dominance and UI contract.
- #32 hot-path/backpressure/scheduler contract.
- #33 OBD independence/witness contract.
- #34 consumption contract.
- adversarial integration matrix.
- `python3 -B tools/run_checks.py` GREEN.
- `./gradlew testDebugUnitTest lintDebug -PomegasAbis=armeabi-v7a --no-daemon --stacktrace` GREEN.
- independent adversarial diff review with no blocking finding.

Any product-code change after final review resets exact-SHA verification.

APK generation remains a separate owner-authorized workflow gate. Physical vehicle claims remain #25 and cannot be inferred from CI.
