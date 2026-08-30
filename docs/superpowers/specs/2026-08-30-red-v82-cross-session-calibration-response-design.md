# RED V8.2 Cross-Session Calibration Response — Design

Date: 2026-08-30
WorkUnit: `RED-BLEND-001`
Issue: `#11`
Branch: `work/red-v82-science-blend`

## Objective

Test whether known historical MAP_K states explain part of the between-session shift already observed in GNV `Tinj | RPM, MAP`, without requiring same-session pre/post alignment and without weakening the stricter causal gate.

The result is an **observational historical sensitivity prior**, not causal proof. It may become an input to a future Predictor only if it improves blind/leave-one-session-out performance without materially worsening tail error or RED runtime.

## Scientific distinction

Two questions remain separate:

1. **Strict causal intervention effect:** requires a proven common timebase / explicit intervention-outcome linkage. Existing G8 remains fail-closed because that bridge is not proven.
2. **Cross-session calibration response:** asks whether sessions observed under different known MAP_K states show systematic response differences in physically overlapping operating regions.

A positive result in (2) does not promote (1).

## Available evidence

The private owner `.omegas` snapshot from 2026-08-18 contains:

- 133 confirmed MAP_K cell-write events;
- 11 independent manual adjustment batches;
- per event: timestamp, writable row/column, physical `petrol_ms`/RPM axes, before, after, readback, confirmed flag, batch-finalized flag and final map hash;
- the final adjustment ends at `1786996252163` ms;
- the first learning session stored in the same snapshot starts at `1786996254166` ms, about two seconds later;
- 600 comparisons in that snapshot are all associated with the final map hash `b73b18326df3ba084dc15d0ba54b6cc4f604236708bc170ec24d6a52feb61847`.

The governed WU-006 corpus already contains V8 sessions both before and after that calibration window, with privacy-safe `session_key`, chronological `order`, session creation time in the manifest, and RPM/MAP/Petrol-Inj episodes.

The current `.omegas` snapshot does **not** contain a governed historical Curva K state. Curva K is therefore out of scope for this experiment rather than inferred from suggestions.

## Data model

### Privacy-safe detailed MAP_K event fixture

Create `tests/fixtures/science/k_history/confirmed_map_k_events_20260818.json` derived from the private owner snapshot. It contains no raw device ID, device name, raw adjustment ID, path, or user identity.

Each event contains:

- `adjustment_key`: SHA-256-derived 16-hex key;
- `timestamp_ms`;
- `row`, `column`;
- physical `petrol_ms`, `rpm` axes;
- `before`, `after`, `readback`;
- `confirmed`, `batch_finalized`;
- `final_map_hash`;
- deterministic `event_proof_sha256`.

The fixture includes the private source snapshot SHA-256 for provenance and must reproduce `133` events / `11` independent interventions.

### Calibration-state reconstruction

For each writable MAP_K cell:

- the first observed `before` value is the earliest known state;
- events are applied chronologically;
- a session receives the most recent known value at or before its `created_at_ms`;
- cells with no known initial state remain `UNKNOWN` rather than being guessed;
- events occurring after a session starts cannot change that session's assigned starting state in this experiment.

This session-level assignment is observational. It intentionally avoids comparing frame timestamps to intervention timestamps.

## Operating-coordinate matching

Outcome matching remains governed by the frozen science rule:

> RPM × MAP defines operating coordinate. Tinj is the response distribution observed repeatedly inside that coordinate.

MAP_K state is an explanatory calibration variable, not the operating coordinate.

For each GNV episode:

1. preserve its governed `rpm_bin,map_bin` for physical-region matching;
2. locate the relevant MAP_K cell using the event fixture's physical `petrol_ms × RPM` axis geometry;
3. require a known session-start K state for that cell;
4. compare only with episodes in the same `rpm_bin,map_bin` from other independent sessions whose K state differs;
5. never treat multiple episodes or multiple cells from one session/batch as independent interventions.

## Primary analyses

### A. Natural pre/post state contrast

For each sufficiently supported physical region:

- estimate session-balanced center of Petrol Inj / equivalence response under lower versus higher K states;
- report `delta_k`, `delta_response`, robust spread and bootstrap interval over independent sessions;
- require at least 2 independent sessions on each side for descriptive contrast, and at least 3 per side before any transfer-oriented claim.

### B. Session-effect decomposition with K state

Compare the existing random-effects/session drift with and without known K state grouping.

Question: does conditioning on MAP_K state reduce between-session residual variance / leave-one-session-out error?

A reduction is evidence that part of the previous `session_effect` was calibration-state variation rather than unexplained drift.

### C. Blind predictor ablation

Compare, chronologically and without future leakage:

- existing WU-006/RED neighbor baseline;
- baseline + observational K-state prior;

The K prior may only affect a prediction when historical sessions contain physically overlapping support under at least two distinct known K states. Otherwise it must abstain and return the original RED prediction unchanged.

## Promotion rule

The observational prior is **not** promoted into Android in this work unit unless all are true:

- zero future leakage;
- deterministic fixture identity;
- at least one region has independent cross-session state contrast;
- holdout median error does not worsen materially;
- holdout P90/P95 do not worsen materially;
- the K prior provides measurable utility beyond the RED anchor (coverage gain or error reduction);
- no strict-causal or `P(improve)` claim is inferred from the observational result.

If no utility is demonstrated, the correct result is `CANDIDATE_REJECTED` or `INSUFFICIENT_CROSS_SESSION_STATE_SUPPORT`.

## Safety and runtime invariants

- RED Android runtime remains unchanged during the experiment.
- No automatic ECU write.
- Human confirmation → ECU → ACK/readback remains invariant.
- `P(improve)` remains null unless separately calibrated on governed causal held-out outcomes.
- G8 strict causal gate remains unchanged.
- Raw private `.omegas` files are not committed.
- Only privacy-safe derived fixtures may enter the repository.

## Success output

Produce a compact audit with:

- number of sessions assigned to known K states;
- number of episodes with known relevant K state;
- number of physical RPM×MAP regions spanning multiple K states;
- per-region independent session counts;
- observed `delta_k → delta_response` contrasts;
- change in between-session residual variance where identifiable;
- blind/LOSO metrics for RED versus RED+K-prior;
- explicit status: `UTILITY_PROVEN`, `NO_UTILITY_GAIN`, or `INSUFFICIENT_SUPPORT`;
- `CAUSAL_MAP_K_PROVEN=false` unless the separate causal gate is independently cleared;
- `P_IMPROVE_PROVEN=false`.
