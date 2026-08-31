# RED V8.2 causal bridge and Predictor gate

**Goal:** Use only committed privacy-safe derivatives to test the three already-approved scientific candidates, preserve RED, and promote nothing to Android unless a blind held-out gate wins.

**Authority:** `work/red-v82-science-blend` at fresh remote parent `e9e093e79c68c47e002f1c1424b1ab6a196c4e95`. Raw logs and Drive imports are out of scope.

## Task 1: Prove or reject the shared chronology

**Files:**
- Add `lab/red_blend/calibration_chronology.py`
- Add `lab/red_blend/test_calibration_chronology.py`
- Modify `lab/red_blend/causal_science.py`
- Modify `lab/red_blend/test_real_causal_science.py`

1. Add failing tests for a structured clock bridge derived from session manifest, reconstructed episode timestamps, and confirmed MAP_K adjustment timestamps.
2. Require hard invariants: matching session identities, episode time inside the declared session ordering envelope, monotonic orders, and interventions inside the observed corpus time span with distinct pre/post sessions.
3. Reject labels or partial evidence; retain fail-closed abstention.
4. Build comparable pre/post outcomes without counting cells as interventions and without using in-batch observations.
5. Run focused tests and commit when GREEN.

## Task 2: Blind RED versus calibration-aware ablation

**Files:**
- Add `lab/red_blend/calibration_ablation.py`
- Add `lab/red_blend/test_calibration_ablation.py`
- Add/update a privacy-safe evidence JSON under `evidence/red_blend/`

1. Add failing chronological held-out tests with synthetic fixtures proving no future leakage and RED fallback.
2. Evaluate the committed corpus using only states that the chronology classifies `EXPLICIT` or `CHAIN_RECONSTRUCTED`.
3. Compare median, P90, P95 and coverage to RED. If full map/curve state is unavailable, emit a deterministic `DEFER` evidence result rather than fabricating a candidate.
4. Commit only the valid experiment and evidence.

## Task 3: AutoCal 18-zone explanatory gate

**Files:**
- Add `lab/red_blend/autocal_regime_ablation.py`
- Add `lab/red_blend/test_autocal_regime_ablation.py`
- Add/update a privacy-safe evidence JSON under `evidence/red_blend/`

1. Add failing tests enforcing the separation of 18 acquisition zones, 30-point Curve K vectors, and 12x12 MAP_K.
2. Accept only protocol-proven, temporally aligned 18-zone snapshots.
3. Run the held-out regime/OOD ablation when real aligned support exists; otherwise emit `DEFER` with exact missing evidence.
4. Do not modify `AutoCalProtocol.kt` or Android runtime in this task.

## Task 4: Promotion decision and remote verification

**Files:**
- Add `docs/evidence/red-v82-causal-predictor-gate.md`
- Update checksum manifest under `evidence/red_blend/`

1. Consolidate proved, falsified and unknown findings, including the exact held-out promotion decision.
2. Modify Android/Predictor only if the candidate has zero leakage, median non-regression, and no material P90/P95 regression. Otherwise preserve RED byte-for-byte.
3. Run focused tests, Science Blend, and Exhaust Existing Tests.
4. Push the isolated branch, verify both remote workflows GREEN on the same SHA, and record the result in Issue #11.

