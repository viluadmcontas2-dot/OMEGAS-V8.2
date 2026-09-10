# OMEGAS Blue Causal Suggestions and OBD Diagnosis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

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

- [x] Add failing journeys proving 20 visits produce one evidence/comparison and newest physical timestamp wins.
- [x] Run the focused JVM selector in remote CI and record the expected failures.
- [x] Import regions once, preserve visits only as audit metadata, carry reference IDs/spread, and stop replacing evidence time with wall clock.
- [x] Rerun focused tests and adjacent Blue engine tests to GREEN.

Evidence: RED `ffc213c7d2c1f81b10e0803d73e7fdae4d98f325` / run `34415483437`; GREEN `78629472e5a3fc1fd48b6d27c01ad9b513fc89f7` / run `34416027249`.

### Task 2: Pure identifiable intervention and gain closure

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/blue/BlueCausalAttribution.kt`
- Create: `app/src/test/java/com/omegas/prohub/blue/BlueCausalAttributionTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueDomain.kt`

**Interfaces:**
- Produces: `BlueCausalIntervention`, `BlueActuatorAddress`, `BlueGainObservation`, `BlueAttributionResult`.
- Consumes: before/after `FuelComparison`, exact before/after K, confirmed old/new revisions.

- [x] Add failing tests for one Curve point, one Map cell, multi-actuator abstention, wrong region, wrong revision, zero step and wrong response direction.
- [x] Run focused tests and prove RED is due to the missing attribution API.
- [x] Implement the pure state-free attribution evaluator using `BlueCausalEngine.actuatorGain`.
- [x] Rerun focused tests to GREEN.

Evidence: RED `a33324787a6532fdef841cad4c48406a14b6713d` / run `34416371213`; GREEN `19c4145612540b283c24e401c5ff2d0c628b44b5` / run `34416702453`.

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

- [x] Add failing tests showing failed/partial/multi writes cannot confirm and restart restores pending ledger safely.
- [x] Prove focused RED.
- [x] Prepare before writer start, confirm only after existing writer reports full readback, and persist atomically in runtime root.
- [x] Close a confirmed pending intervention only with matching post-write evidence.
- [x] Rerun coordinator/ledger tests to GREEN.

Evidence: RED `efadfbdef28f4453f0918424394d6c9a37f3269d` / run `34416991278`; an intermediate fix at `b269dc6cad64164aa88a92551107058b1b28facb` still failed, then fixture/API disambiguation `7b2987d2f3a4a6f224da765fabfc2a70d5b1c81b` reached GREEN in run `34417700941`.

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

- [x] Add failing full journeys for no-gain abstention, Curve target, Map target, clamp/normalization, stale gain and UI preview-without-write.
- [x] Prove focused RED.
- [x] Generate exact Kotlin targets and project only the matching actuator payload.
- [x] Bind Curve/Map navigation to preview preparation only.
- [x] Rerun JVM and Node selectors to GREEN.

Evidence: journey RED `8f33c0913881f610728ee3afbb910226d8299a7e` / run `34418073019`; first implementation `75d7432f7602110d640bba3066a6f1ebfb8230e5` exposed remaining binding gaps; explicit binding RED `c96f8164e3e1a3e70d705ae38b8f6ef27f1aa86d` / run `34419150983`; final binding `be6e4aa909fe6dc118e1b20a03d87e5997f18e2c` GREEN / run `34419319165`.

### Task 5: OBD diagnostic surface and retry behavior

**Files:**
- Modify: `app/src/main/assets/ui/screens/obd.js`
- Test: `tests/ui/obd-runtime-controls.test.cjs`
- Test: `app/src/test/java/com/omegas/prohub/obd/ElmConnectionStateTest.kt`

**Interfaces:**
- Consumes: existing `connectionStage/errorCode/detail/retryable` status.
- Produces: visible diagnostic and bounded retry action; no transport change.

- [x] Add failing browser cases for PERMISSION, RFCOMM, ELM_INIT, PROTOCOL and STFT errors with visible code/detail/retry.
- [x] Prove focused RED.
- [x] Render the native diagnostic fields and retry control without adding a writer or new connection scheduler.
- [x] Rerun OBD UI/JVM tests to GREEN.

Evidence: RED `8de50123332df6bc3932a1bc02847dc45587c813` / run `34419567232`, failing the actionable OBD diagnostic contract because `errorCode` was not rendered; GREEN `da8f25f0299dd4fe5a22515ae41198be57df5403` / run `34419736026`.

### Task 6: Broad verification and exact-SHA readback

**Files:**
- Modify: `docs/workunits/OMEGAS-BLUE-ALGO-VERIFY-001.md`
- Modify: `STATUS.md`
- Modify: issue `#28` only after evidence exists.

**Interfaces:**
- Produces: requirement -> test -> SHA -> CI result traceability and explicit physical limitations.

- [x] Run FAST contracts on the final source SHA.
- [x] Run full `testDebugUnitTest lintDebug` on the same SHA.
- [x] Poll GitHub Actions until terminal and inspect failed job logs rather than retrying blindly.
- [x] Read back branch HEAD, changed files and CI conclusions.
- [x] Record confirmed, partial, failed and unverified criteria in workunit/status.
- [x] Do not generate another APK unless separately authorized.

Software closure evidence: `da8f25f0299dd4fe5a22515ae41198be57df5403`, canonical run `34419736026`, FAST `success`, FULL JVM/lint `success`, Gradle `BUILD SUCCESSFUL`, `READY_FOR_APK_GENERATION=true`, `APK_GENERATED=false`; APK job skipped. Physical OBD behavior and fuel-economy impact remain explicitly unverified until vehicle retest.

## Closure rule

The checklist above records execution against the final software SHA. Because workunit/status/checklist reconciliation itself advances branch HEAD, the final documentation HEAD must also pass the canonical FAST -> FULL JVM/lint workflow before issue `#28` is closed. No APK generation is required or authorized by this documentation gate.
