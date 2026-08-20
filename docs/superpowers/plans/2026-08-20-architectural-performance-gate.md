# OMEGAS V8.2 Architectural Performance Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove source-proven sustained overhead before the final APK candidate while preserving MP48 acquisition, bounded Learning, manual-only calibration writes, ACK/readback safety, and existing user-visible functionality.

**Architecture:** Keep the proven native core + WebView split for now. First remove work that is demonstrably redundant: admit visual fan-out before building payloads, make heavy scientific/calibration projections revision/signature-driven, and deduplicate UI consumers. Only reconsider a larger native/hybrid UI rewrite if these lower-risk changes leave a structural bottleneck.

**Tech Stack:** Android/Kotlin, WebView JavaScript, JUnit 4, Python structural contract tests, GitHub remote source authority.

**Spec:** Linear VIT-84 with child batches VIT-188..VIT-191; Notion OME-HW-001, OME-MASTER-TRACE, OME-PREAPK-CAUSAL, UIUX-CUSTOMROM/UIUX-OMEGADEV.

## Global Constraints

- Source authority is `viluadmcontas2-dot/OMEGAS-V8.2`, branch `rebuild/v8.2-final-implementation`.
- Source mutation happens through GitHub remote APIs; local/ephemeral runtimes are test-only.
- No physical TayTech/RK3326 validation before the final APK candidate is ready.
- Host/static evidence must not be promoted to a physical CPU/RSS/GC/thermal/latency claim.
- One MP48 serial authority remains; no new writer, no UI→serial path, no Prediction→Observation path.
- Learning remains event-driven and bounded; routes/timers never become scientific producers.
- Manual ECU writes retain review → writer → ACK → readback → reconciliation semantics.
- Prefer high-return, low-risk architectural waste removal over framework replacement.

---

### Task 1: Native visual fan-out admission (VIT-188)

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/service/VisualFanoutAdmission.kt`
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`
- Create: `app/src/test/java/com/omegas/prohub/service/VisualFanoutAdmissionTest.kt`

**Interfaces:**
- Consumes: elapsed realtime supplied by caller.
- Produces: `VisualFanoutAdmission.tryAcquire(nowMs, force)` deciding whether expensive overlay projection may run.

- [ ] Write a JVM test proving first call passes, a second call inside 250 ms is rejected, a call at/after 250 ms passes, and `force=true` passes immediately.
- [ ] Add the pure admission class with no Android dependency.
- [ ] Move overlay admission ahead of `status()` and `obd.statusJson()` in `TelemetryForegroundService`.
- [ ] Preserve `TelemetryOverlayController`'s own 250 ms rendering guard as a second safety boundary.
- [ ] Review the diff to prove telemetry acquisition, Learning, serial scheduling, and writer paths are untouched.

### Task 2: Revision-aware heavy UI context (VIT-189)

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt`
- Modify: `app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt`
- Modify: `app/src/main/assets/ui/core/native-api.js`
- Modify: `app/src/main/assets/ui/app.js`
- Create/modify focused JVM/Python contract tests under `app/src/test/**` and `tests/**`.

**Interfaces:**
- Produces a cheap revision/signature snapshot whose fields come from existing state owners, not from rebuilding Learning/Predictor projections.
- UI context refresh reads this cheap snapshot first and invokes expensive `getLearningMaps()` / V7 state only when their relevant revision changed or when a force-refresh event occurs.

- [ ] Characterize which owner revisions already exist and add only missing monotonic/signature fields.
- [ ] Add a cheap `getUiRevisions()` bridge surface that does not parse persisted Learning files or build Predictor cells.
- [ ] Gate heavy context fetches by revision; route selects consumers but does not generate science.
- [ ] Force context invalidation on explicit `omegas-refresh`, route entry, write completion, import, and restore boundaries.
- [ ] Add falsifiers proving unchanged revisions suppress heavy calls and changed revisions deliver updated state.

### Task 3: UI consumer dedupe and Store wake-up discipline (VIT-190)

**Files:**
- Modify: `app/src/main/assets/ui/core/store.js`
- Modify: `app/src/main/assets/ui/app.js`
- Modify: `app/src/main/assets/ui/screens/predictor.js`
- Modify affected JS contract tests under `tests/**`.

**Interfaces:**
- Store subscriptions may declare a selector/equality boundary or an equivalent narrow wake-up contract.
- Predictor uses the application coordinator for context refresh instead of owning a duplicate periodic `getState()` loop.

- [ ] Add regression coverage showing an unrelated Store patch does not wake an expensive selected subscriber.
- [ ] Remove Predictor's duplicate context hook/direct periodic `getState()` when the coordinator already owns the same domain refresh.
- [ ] Preserve immediate route-entry rendering and manual refresh semantics.
- [ ] Audit remaining `scheduler.addHook` and whole-Store subscriptions; change only material expensive duplicates.

### Task 4: Structural projection reuse + startup/IME cleanup (VIT-191)

**Files:**
- Likely modify: `app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt`
- Likely modify: `app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt`
- Review/modify: `app/src/main/assets/ui/index.html`, screen modules, and numeric input interaction only where evidence justifies it.

**Interfaces:**
- Structural Learning/Predictor projections are cached by owner revision/signature and invalidated on the exact persistence/calibration events that change them.

- [ ] Characterize payload rebuild cost paths and cache only immutable-by-revision structure.
- [ ] Keep live telemetry/current-cell presentation on the lightweight live path rather than invalidating structural caches.
- [ ] Review startup DOM/script footprint and remove only work that can be deferred without breaking route functionality.
- [ ] Prefer nudge/step controls for frequent numeric adjustment while retaining direct numeric entry for advanced use.

### Task 5: Integrated source verification and final-candidate handoff

**Files:**
- Update: `ci/preapk-rerun.txt` only once the coherent source batch is ready for the authorized verification workflow.
- Update execution evidence in Linear/Notion ledger; do not build APK yet.

- [ ] Re-resolve exact remote branch HEAD.
- [ ] Trigger one bounded verification run (`pytest`, JVM unit tests, Android lint) after integration rather than on every small commit.
- [ ] Inspect combined status and first failure if any; remediate source before claiming readiness.
- [ ] Run the existing PREAPK contract path for the final source candidate.
- [ ] Only after PREAPK is green, proceed to the final APK build gate.
- [ ] Physical TayTech/RK3326 validation occurs only on that final APK candidate.
