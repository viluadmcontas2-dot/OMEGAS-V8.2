# Predictor Human Authority Step156 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move Predictor human wording, reviewability and risk disclosure out of the WebView and into one typed upstream projection driven by scientific state, canonical MagnitudeAuthority and calibrated-risk inputs.

**Architecture:** Add a pure Kotlin `PredictorHumanStateProjector`. Publish its result into the existing V7 calibration-state `predictor.cells[].humanState` object at the native service projection boundary. Refactor `predictor-model.js` and `predictor.js` to render that object only. No second Store, Router, Scheduler, writer or transport is created.

**Spec:** Notion FASE 07 Step156.

## Constraints
- GitHub remote is source authority; Linear is execution writer.
- No GitHub Actions.
- UI must not infer authority, confidence, reason or reviewability from raw target/currentK/state.
- Missing/invalid humanState fails closed.
- PHYSICALLY_ANCHORED/EMPIRICALLY_BOUNDED may expose target/range language; POLICY_ONLY/UNKNOWN never say ideal/correct.
- PREDICTED_SHRUNK must explicitly disclose shrinkage from distance/uncertainty.
- Runtime actionability remains separate from authority and requires the upstream risk gate.
- No automatic ECU write path.

### Task 1 — Pure typed human-state projection
**Files:**
- Create `app/src/main/java/com/omegas/prohub/learning/PredictorHumanState.kt`
- Create `app/src/test/java/com/omegas/prohub/learning/PredictorHumanStateTest.kt`

- [ ] RED: authority × state × risk wording matrix.
- [ ] RED: same numeric target, different authority => different wording/review state.
- [ ] RED: POLICY_ONLY/UNKNOWN forbidden words (`ideal`, `correto`).
- [ ] RED: PREDICTED_SHRUNK exact disclosure.
- [ ] RED: actionable=true with uncalibrated risk or ineligible authority fails closed.
- [ ] GREEN: implement pure projection using canonical `MagnitudeAuthority` and Predictor scientific/actionability states.
- [ ] Property/fuzz monotonic fail-closed checks.

### Task 2 — Publish humanState through the existing native calibration state
**Files:**
- Modify `app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt`
- Create focused compatibility test if feasible without Android runtime; otherwise exact-source projection probe with `org.json` stubs.

- [ ] Add one projection pass over the already-built Predictor snapshot before it is inserted into `v7CalibrationStateJson()`.
- [ ] Prefer typed `fieldState`, `magnitudeAuthority`, risk/actionability fields when present.
- [ ] Legacy absent authority/risk defaults to UNKNOWN/unverified/non-actionable.
- [ ] Legacy state mapping is compatibility-only and cannot grant authority/actionability.
- [ ] Publish complete `humanState` fields needed by UI.
- [ ] No additional cache/store/router/scheduler.

### Task 3 — Refactor WebView to consume humanState only
**Files:**
- Modify `app/src/main/assets/ui/core/predictor-model.js`
- Modify `app/src/main/assets/ui/screens/predictor.js`
- Add Node probe under `scripts/` or test fixture only if repository conventions allow; otherwise execute external focused Node probe against exact blob.

- [ ] RED behavior probe showing current JS reconstructs stateLabel/reason/confidence/reviewability.
- [ ] GREEN `explainCell`: consume precomputed `humanState`; missing humanState => safe no-target/no-review placeholder.
- [ ] `openMapReview` accepts only upstream `reviewEnabled` and upstream display/current/target state.
- [ ] Screen grid/inspector consume humanState confidence/visual state/labels/reason/actionability/button wording.
- [ ] Static scan forbids raw derivation patterns for reviewability/confidence/reason.
- [ ] Node snapshot/behavior fixture for authority wording and missing-humanState fail-closed.

### Task 4 — Verify and close
- [ ] Fresh compare branch.
- [ ] Reproduce exact production blobs/hash before compile/Node claims.
- [ ] Kotlin exact compile/property battery.
- [ ] Node exact behavior/syntax battery.
- [ ] Independent audit + meta-audit in VIT-312.
- [ ] Mark Done only after PASS.
