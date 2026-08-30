# RED V8.2 Cross-Session Calibration Response Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Test whether known historical MAP_K states explain cross-session response changes and improve the RED predictor without weakening strict causal or runtime gates.

**Architecture:** Add a privacy-safe detailed MAP_K event fixture and an offline `cross_session_calibration.py` laboratory. Reconstruct session-start K state from manifest creation times, match outcomes by frozen RPM×MAP operating regions, compare independent sessions under different K states, and run a blind RED-versus-RED+K-prior ablation. No Android source changes.

**Tech Stack:** Python 3 standard library, existing `lab/red_blend` modules, governed WU-006 fixture, GitHub Actions standard runners.

**Spec:** `docs/superpowers/specs/2026-08-30-red-v82-cross-session-calibration-response-design.md`

## Global Constraints

- RPM × MAP remains the operating coordinate; Tinj remains the response distribution.
- MAP_K state is explanatory calibration evidence, not a replacement coordinate.
- Strict G8 causal status is unchanged by observational cross-session evidence.
- `P(improve)` remains null.
- No automatic ECU write.
- No Android runtime source change during this experiment.
- Raw private `.omegas` files are never committed.
- Historical Curva K is out of scope until an authoritative state source is found.

---

### Task 1: Privacy-safe detailed MAP_K fixture + contracts

**Files:**
- Create: `tests/fixtures/science/k_history/confirmed_map_k_events_20260818.json`
- Create: `lab/red_blend/test_cross_session_calibration.py`

**Interfaces:**
- Consumes: governed episode fixture, corpus manifest, detailed K event fixture.
- Produces test contracts for `load_k_event_fixture`, `reconstruct_session_k_states`, `audit_cross_session_response`, and blind ablation.

- [ ] **Step 1: Commit the privacy-safe event fixture** derived from the private owner snapshot. Require source SHA `8ce231682c5b49a513201759bbeccc3fd6fe5521067b88c048eae57769850b11`, 133 events, 11 intervention keys, valid ACK/readback envelopes, and no raw adjustment/device identity.

- [ ] **Step 2: Write failing tests** proving:
  - tamper or readback mismatch fails closed;
  - session before all adjustments receives first-known `before` value for touched cells;
  - session after final adjustment receives final `after` value;
  - a session beginning during an adjustment window is not allowed to consume later events from that batch;
  - unknown cells remain unknown;
  - matching is by `rpm_bin,map_bin` outcome region while K lookup uses physical `petrol_ms × RPM` map axes;
  - multiple episodes in one session never count as independent state contrasts;
  - real audit is deterministic and returns one of `UTILITY_PROVEN`, `NO_UTILITY_GAIN`, `INSUFFICIENT_SUPPORT`.

- [ ] **Step 3: Run GitHub Actions to verify RED**. Expected failure: missing `lab.red_blend.cross_session_calibration` while all inherited gates remain green.

- [ ] **Step 4: Commit the RED evidence SHA/run** to Issue #11 after the run is observable.

---

### Task 2: Session-start K-state reconstruction

**Files:**
- Create: `lab/red_blend/cross_session_calibration.py`

**Interfaces:**
- `load_k_event_fixture(path) -> list[dict]`
- `reconstruct_session_k_states(manifest: dict, events: list[dict]) -> dict[str, SessionCalibrationState]`
- `lookup_k_for_episode(state, episode) -> KLookup | None`

- [ ] **Step 1: Implement fixture validation** for finite axes, writable row/column, U8 before/after/readback, confirmed/finalized state, readback==after, deterministic event proof and monotonic event ordering.

- [ ] **Step 2: Reconstruct cell state chronologically** using first-known `before` as initial state and applying only events with `timestamp_ms <= session.created_at_ms`.

- [ ] **Step 3: Implement physical K-cell lookup** by nearest known Petrol-Inj/RPM axis coordinate with deterministic tie-breaking; do not fabricate the never-observed 1250 RPM column.

- [ ] **Step 4: Run focused tests** and verify the Task 1 reconstruction contracts turn GREEN.

- [ ] **Step 5: Commit minimal implementation** without changing thresholds/tests.

---

### Task 3: Cross-session observational response audit

**Files:**
- Modify: `lab/red_blend/cross_session_calibration.py`
- Modify: `lab/red_blend/test_cross_session_calibration.py`

**Interfaces:**
- `audit_cross_session_response(episodes, manifest, events) -> CrossSessionAudit`

- [ ] **Step 1: Add tests** requiring session-balanced grouping by `rpm_bin,map_bin` and known K state, with independent session counts rather than episode counts.

- [ ] **Step 2: Implement per-session region centers** for GNV `petrol_ms`; preserve episode density only for within-session center precision.

- [ ] **Step 3: Build region contrasts** only when at least two distinct K values and at least two independent sessions per side exist; mark transfer-oriented support only at 3+ sessions per side.

- [ ] **Step 4: Bootstrap over sessions, not frames**, returning median/mean `delta_k`, `delta_petrol_ms`, relative response, and interval. No `P(improve)`.

- [ ] **Step 5: Compare unexplained between-session variance** before and after K-state conditioning where estimable.

- [ ] **Step 6: Run real-corpus CLI audit** in Actions and capture exact supported regions/results.

---

### Task 4: Blind RED versus RED+K-prior ablation

**Files:**
- Modify: `lab/red_blend/cross_session_calibration.py`
- Modify: `lab/red_blend/test_cross_session_calibration.py`

**Interfaces:**
- `blind_k_prior_ablation(episodes, manifest, events) -> PredictorAblationReport`

- [ ] **Step 1: Add future-leakage regression test**: adding a later session or later K event cannot change an earlier prediction.

- [ ] **Step 2: Add anchor-preservation test**: when no cross-session K contrast is supported, the RED prediction must be returned unchanged.

- [ ] **Step 3: Implement prior estimation using training-prefix sessions only**. Fit only region-local observational slope/contrast supported by earlier independent sessions.

- [ ] **Step 4: Apply the prior conservatively** to RED neighbor predictions only where a known target-session K state and earlier multi-state support exist; otherwise preserve RED.

- [ ] **Step 5: Report coverage, median, P90, P95 and max absolute relative error** for RED and RED+K-prior, plus count of predictions actually modified.

- [ ] **Step 6: Promotion decision**:
  - `UTILITY_PROVEN` only if holdout utility improves without material median/P90/P95 regression;
  - `NO_UTILITY_GAIN` if safe but no gain;
  - `INSUFFICIENT_SUPPORT` if multi-state overlap is inadequate.

---

### Task 5: CI/evidence and runtime guard

**Files:**
- Modify: `.github/workflows/red-v82-science-blend.yml`
- Create: `docs/evidence/red-blend-cross-session-calibration-response.md`

**Interfaces:**
- Workflow runs focused cross-session tests and compact real audit before performance/Android jobs.

- [ ] **Step 1: Add focused unit-test step** after existing causal/sensitivity gates.

- [ ] **Step 2: Add compact real-corpus CLI audit step** using existing governed fixtures.

- [ ] **Step 3: Run complete Science Blend Actions** and require all prior gates plus cross-session gate GREEN.

- [ ] **Step 4: Confirm performance runtime-identity gate still reports no Android/build input delta** from proven RED baseline.

- [ ] **Step 5: Write evidence** with exact SHA, run, fixture hashes, session/state counts, region contrasts, ablation metrics and claim limits.

- [ ] **Step 6: Update Issue #11**. Keep `CAUSAL_MAP_K_PROVEN=false`, `P_IMPROVE_PROVEN=false`, and `ECU_AUTO_WRITE=false` unless separately proven.

## Self-review

- Spec coverage: all design requirements map to Tasks 1–5.
- Placeholder scan: no TBD/TODO or unspecified implementation step remains.
- Type consistency: fixture → session state → region audit → predictor ablation forms one directed dependency chain.
- Scope: MAP_K only; Curva K explicitly deferred because no authoritative historical state is present in the available snapshots.
