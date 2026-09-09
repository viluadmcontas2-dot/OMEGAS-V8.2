# OMEGAS Blue Causal Suggestions and OBD Diagnosis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce exact Blue proposals only from identifiable confirmed interventions, consume them in the manual editors, and expose actionable OBD connection diagnostics.

**Architecture:** Correct evidence identity/time first, add a pure single-actuator attribution model, bind it to the coordinator and confirmed writers, then project one exact Curve or Map change. OBD transport remains unchanged until the real failing stage is observed.

**Tech Stack:** Kotlin/JVM, Android foreground service and JavaScript WebView UI, JUnit 4, Node browser contracts, Python FAST contracts, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-09-blue-causal-suggestions-obd-diagnosis-design.md`

## Global Constraints

- Branch: `work/omegas-blue-causal-engine`.
- GitHub remote is authority; do not create a local checkout or branch.
- `BlueCausalEngine` is the sole correction authority.
- No gain fallback, invented `K_effective`, automatic write or MP48 protocol change.
- STFT GNV is optional confidence evidence; LTFT has no correction vote.
- Every production change follows RED -> GREEN -> broader exact-SHA CI.
- Physical OBD and economy claims remain unverified without vehicle evidence.

---

### Task 1: Evidence identity, provenance and physical time

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueDomain.kt`
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt`
- Modify: `app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt`
- Test: `app/src/test/java/com/omegas/prohub/calibration/BlueEvidenceJourneyTest.kt`

**Interfaces:**
- Produces: one `FuelEvidence` per region; `BluePetrolReference.evidenceIds/spreadMs`; physical `createdAtMs`.

- [ ] Add failing journeys proving 20 visits produce one evidence/comparison and newest physical timestamp wins.
- [ ] Run the focused JVM selector in remote CI and record the expected failures.
- [ ] Import regions once, preserve visits only as audit metadata, carry reference IDs/spread, and stop replacing evidence time with wall clock.
- [ ] Rerun focused tests and adjacent Blue engine tests to GREEN.

### Task 2: Pure identifiable intervention and gain closure

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/blue/BlueCausalAttribution.kt`
- Create: `app/src/test/java/com/omegas/prohub/blue/BlueCausalAttributionTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueDomain.kt`

**Interfaces:**
- Produces: `BlueCausalIntervention`, `BlueActuatorAddress`, `BlueGainObservation`, `BlueAttributionResult`.
- Consumes: before/after `FuelComparison`, exact before/after K, confirmed old/new revisions.

- [ ] Add failing tests for one Curve point, one Map cell, multi-actuator abstention, wrong region, wrong revision, zero step and wrong response direction.
- [ ] Run focused tests and prove RED is due to the missing attribution API.
- [ ] Implement the pure state-free attribution evaluator using `BlueCausalEngine.actuatorGain`.
- [ ] Rerun focused tests to GREEN.

### Task 3: Persistent coordinator ledger and confirmed-writer binding

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/blue/BlueCausalLedger.kt`
- Create: `app/src/test/java/com/omegas/prohub/blue/BlueCausalLedgerTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt`
- Modify: `app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt`
- Modify: `app/src/main/java/com/omegas/prohub/web/BlueJavascriptBridge.kt`

**Interfaces:**
- Produces: `prepareIntervention(payload): JSONObject`, `confirmIntervention(id, readback): JSONObject`, persisted pending/confirmed gain state.
- Consumes: current Blue comparison, exact write preview, writer completion with ACK/readback.

- [ ] Add failing tests showing failed/partial/multi writes cannot confirm and restart restores pending ledger safely.
- [ ] Prove focused RED.
- [ ] Prepare before writer start, confirm only after existing writer reports full readback, and persist atomically in runtime root.
- [ ] Close a confirmed pending intervention only with matching post-write evidence.
- [ ] Rerun coordinator/ledger tests to GREEN.

### Task 4: Exact Curve/Map proposal and manual-editor consumption

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueAutoCalAdapter.kt`
- Modify: `app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt`
- Modify: `app/src/main/assets/ui/screens/curve.js`
- Modify: `app/src/main/assets/ui/screens/map.js`
- Test: `app/src/test/java/com/omegas/prohub/calibration/BlueEvidenceJourneyTest.kt`
- Test: `tests/ui/blue-curve-native-authority.test.cjs`
- Create: `tests/ui/blue-proposal-consumption.test.cjs`

**Interfaces:**
- Produces: proposal with exactly one of `curveChanges` or `mapChanges`, gain provenance, rationale and `automaticWrite=false`.
- Consumes: accepted gain observation and current confirmed calibration.

- [ ] Add failing full journeys for no-gain abstention, Curve target, Map target, clamp/normalization, stale gain and UI preview-without-write.
- [ ] Prove focused RED.
- [ ] Generate exact Kotlin targets and project only the matching actuator payload.
- [ ] Bind Curve/Map navigation to preview preparation only.
- [ ] Rerun JVM and Node selectors to GREEN.

### Task 5: OBD diagnostic surface and retry behavior

**Files:**
- Modify: `app/src/main/assets/ui/screens/obd.js`
- Test: `tests/ui/obd-runtime-controls.test.cjs`
- Test: `app/src/test/java/com/omegas/prohub/obd/ElmConnectionStateTest.kt`

**Interfaces:**
- Consumes: existing `connectionStage/errorCode/detail/retryable` status.
- Produces: visible diagnostic and bounded retry action; no transport change.

- [ ] Add failing browser cases for PERMISSION, RFCOMM, ELM_INIT, PROTOCOL and STFT errors with visible code/detail/retry.
- [ ] Prove focused RED.
- [ ] Render the native diagnostic fields and retry control without adding a writer or new connection scheduler.
- [ ] Rerun OBD UI/JVM tests to GREEN.

### Task 6: Broad verification and exact-SHA readback

**Files:**
- Modify: `docs/workunits/OMEGAS-BLUE-ALGO-VERIFY-001.md`
- Modify: `STATUS.md`
- Modify: issue `#28` only after evidence exists.

**Interfaces:**
- Produces: requirement -> test -> SHA -> CI result traceability and explicit physical limitations.

- [ ] Run FAST contracts on the final source SHA.
- [ ] Run full `testDebugUnitTest lintDebug` on the same SHA.
- [ ] Poll GitHub Actions until terminal and inspect failed job logs rather than retrying blindly.
- [ ] Read back branch HEAD, changed files and CI conclusions.
- [ ] Record confirmed, partial, failed and unverified criteria in workunit/status.
- [ ] Do not generate another APK unless separately authorized.
