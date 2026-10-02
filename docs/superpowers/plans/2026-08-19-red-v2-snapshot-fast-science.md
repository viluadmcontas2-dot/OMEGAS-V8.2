# RED V2 Snapshot Bus + Fast Science Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make route changes paint from RAM immediately while reducing scientific publications per telemetry frame without weakening fuel-transition safety or serial/writer invariants.

**Architecture:** Keep `ResponseDrivenEcuEngine` as the single MP48 authority. Add a latest-only native UI snapshot seam and a revisioned science snapshot seam, then make the WebView consume them through one scheduler-owned Data Pump. Learning uses a progressive 4/6/8/10 window policy plus a publication gate based on new-frame novelty; confidence reports precision/effective mass separately from independent visits.

**Tech Stack:** Kotlin/Android, org.json, JavaScript WebView shell, JUnit4, Python unittest contracts, GitHub Actions.

**Spec:** Notion `OMEGAS V8.0 — RED HOTFIX / PERFORMANCE RECOVERY`, section `RED V2 — Arquitetura aprovada e execução autorizada — 19/08/2026 20:49 BRT`; Linear `VIT-182`.

## Global Constraints

- `PROJECT_ID=OMEGAS_V8_0_RED` and `WORK_BRANCH=hotfix/v8.0-red-performance`.
- GitHub remote is the only source mutation surface; no local source writes.
- One physical MP48 acquisition authority and one writer only.
- No second polling loop, transport, serial thread, or prediction→observation path.
- Manual ECU writes keep human confirmation + ACK/readback.
- Fuel transition/cutoff/gap recovery keeps a full 10-frame confirmation window.
- `MICRO_CANDIDATE` at 4 frames is visual-only and cannot mutate learning memory.
- `FAST_ACCEPT` at 6 frames requires exceptional quality and strong injection stability.
- `STANDARD_ACCEPT` at 8 frames requires stable gates; 10 frames is the conservative fallback.
- Scientific publication must not happen on every frame once a window is full.
- UI route paint must never require rebuilding the full science payload synchronously.

---

### Task 1: Progressive sampler state machine

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/AdaptiveSampleWindow.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/MotorSampleAnalyzer.kt`
- Modify: `app/src/test/java/com/omegas/prohub/learning/MotorSampleAnalyzerTest.kt`
- Modify: `app/src/test/java/com/omegas/prohub/learning/AdaptiveSampleWindowTest.kt`

**Interfaces:**
- Produces: `AdaptiveSampleWindow.stageFor(sample, desiredFrames, toleratedGapCount, fullWindowRequired, strongPetrolOscillationRatio)` returning a stage enum/string contract used by `MotorSampleAnalyzer`.
- Preserves: `SampleDecision.reasonCode`, `minimumFrames`, `desiredFrames`, fuel-transition behavior.

- [ ] **Step 1: Write failing regression tests**
  - `reset then six exceptional readings can produce FAST_ACCEPT without treating session start as a continuity fault`.
  - `planned operation still requires complete target`.
  - `four healthy readings expose MICRO_CANDIDATE but learningEligible stays false`.
  - `eight stable readings produce STANDARD_ACCEPT when six-frame quality is below fast threshold`.
- [ ] **Step 2: Run targeted JUnit and verify the new tests fail for the intended missing behavior.**
- [ ] **Step 3: Implement the minimum progressive stage logic.** Session start/reset must clear samples without falsely applying the same full-window penalty used for loss/transition; explicit planned operation, continuity loss, cutoff, and fuel transition keep full-window protection.
- [ ] **Step 4: Re-run targeted JUnit and verify green.**

### Task 2: Novelty-driven science publication gate

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/learning/SciencePublicationGate.kt`
- Create: `app/src/test/java/com/omegas/prohub/learning/SciencePublicationGateTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt`

**Interfaces:**
- Produces: `SciencePublicationGate.shouldPublish(newFrames, totalFrames, acceptedStage, fuelJustStabilized, continuityLost): Boolean`.
- Rule: first accepted evidence publishes; afterward require at least 75% new-frame mass or a safety/state boundary. Duplicates never publish.

- [ ] **Step 1: Write failing tests** proving 10-frame rolling overlap does not emit on every frame, while a 6-frame fresh sample and state boundaries do publish.
- [ ] **Step 2: Run targeted test and verify RED.**
- [ ] **Step 3: Implement gate and integrate before memory/advisor mutation in `SignalLearningStore.ingest`.** Keep visible live decision even when science publication is suppressed.
- [ ] **Step 4: Verify targeted test green and existing novelty tests unchanged.**

### Task 3: Native PresentSnapshot / ScienceSnapshot bus

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/runtime/RuntimeSnapshotBus.kt`
- Create: `app/src/test/java/com/omegas/prohub/runtime/RuntimeSnapshotBusTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`
- Modify: `app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt`
- Modify: `app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt`

**Interfaces:**
- `publishPresent(JSONObject)` replaces the latest present snapshot and increments `presentRevision`.
- `publishScience(JSONObject, revisionToken)` replaces science only when the token changes and increments `scienceRevision`.
- `presentJson()` returns constant-size latest UI state from RAM.
- `scienceJsonSince(lastRevision: Long)` returns `{changed:false, revision}` or the cached science payload; it must not synchronously trigger learning export/rebuild.

- [ ] **Step 1: Add failing JUnit tests** for latest-only replacement, unchanged science revision, and `scienceJsonSince` no-change response.
- [ ] **Step 2: Run targeted tests and verify RED.**
- [ ] **Step 3: Implement bus and wire service publication at existing telemetry/science update seams only; no new timer/thread.**
- [ ] **Step 4: Expose bridge methods `getPresentSnapshot()` and `getScienceSnapshotSince(revision)` that only read RAM.
- [ ] **Step 5: Verify tests green and confirm no new scheduler/serial owner exists.**

### Task 4: Single WebView Data Pump

**Files:**
- Modify: `app/src/main/assets/ui/core/native-api.js`
- Modify: `app/src/main/assets/ui/app.js`
- Modify: `app/src/main/assets/ui/screens/predictor.js`
- Modify: `app/src/main/assets/ui/components/predictor-current-cell.js`
- Create: `tests/test_red_single_data_pump_contract.py`

**Interfaces:**
- `NativeApi.presentSnapshot()` calls only `OmegasNative.getPresentSnapshot`.
- `NativeApi.scienceSnapshotSince(revision)` calls only one science bridge seam.
- App scheduler owns all calls; screens consume Store state and do not call native telemetry/science directly from their own hooks.

- [ ] **Step 1: Add failing Python contract** that rejects direct `api.telemetry()`/`v7.getState()` polling inside Predictor/current-cell and requires the two snapshot methods.
- [ ] **Step 2: Push test-only commit and verify RED workflow fails for the expected contract.**
- [ ] **Step 3: Implement NativeApi methods and move fast/context refresh into the app-level pump.** Route navigation renders cached Store state before any context refresh.
- [ ] **Step 4: Remove Predictor/current-cell native polling hooks; they render from Store only.
- [ ] **Step 5: Re-run RED workflow and verify green.**

### Task 5: Separate precision, effective mass, and independent visits

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt`
- Modify: `app/src/test/java/com/omegas/prohub/learning/MotorLearningMemoryTest.kt` (or nearest existing region/confidence test)
- Create/modify: `tests/test_red_learning_confidence_contract.py`

**Interfaces:**
- Region JSON exposes `precision_within_visit`, `effective_evidence_mass`, and `independent_visits`.
- Overlapping novelty-weighted windows add fractional effective mass rather than pretending to be full independent support.
- Existing comparison dedupe by `visitId` remains authoritative for GNV×petrol reproducibility.

- [ ] **Step 1: Add regression tests** proving two correlated windows increase effective mass by less than two while independent visit count remains one.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Persist/backfill the new effective mass field compatibly and expose the three dimensions; do not change writer or calibration epochs.
- [ ] **Step 4: Verify targeted and broad learning tests green.**

### Task 6: Integrated verification and evidence

**Files:**
- Update: `.github/workflows/verify-red-hotfix.yml` only if the new tests are not already included by existing globs.
- Create: `docs/incidents/2026-08-19-red-physical-soak-performance.md`
- Update: Notion RED and Linear `VIT-182` after fresh verification.

- [ ] **Step 1: Revalidate remote HEAD and inspect complete RED V2 diff.**
- [ ] **Step 2: Run lightweight RED workflow / Python suite on the integrated SHA.**
- [ ] **Step 3: Run `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` only once on the final candidate SHA.
- [ ] **Step 4: Perform read-only audit in a new audit epoch; do not mutate audited source during that run.
- [ ] **Step 5: Record residual physical risk: no CPU/latency percentage claim until TayTech/MP48 soak.
