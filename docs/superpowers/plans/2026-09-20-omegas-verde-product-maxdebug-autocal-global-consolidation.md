# OMEGAS Verde Product MAXDEBUG + AutoCal Global Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate OMEGAS Verde into a coherent in-car product, beginning with a physically legible AutoCal cockpit and continuing through runtime integrity, learning semantics and a global consumer/dead-code audit, without generating an APK.

**Architecture:** Preserve Kotlin/native/protocol authority and make the WebView a deterministic human projection. AutoCal is one cockpit: native Gasoline/GNV reference + optional native same-pressure equivalence + the live AGORA cursor are rendered together; AGORA is a separate data/render layer only so it cannot redefine the reference scale. Production slices are built as unreferenced ephemeral commits, verified by AgentRed/MMMACHINE against exact SHAs, then only the coordinator may fast-forward `OmegasVerde`.

**Tech Stack:** Kotlin/JVM, Android WebView, JavaScript/CSS, Python contract tests, Node runtime tests, Gradle/JUnit, GitHub remote API, AgentRed read-only/verification jobs.

**Spec:** `docs/superpowers/specs/2026-09-20-omegas-verde-product-maxdebug-autocal-global-consolidation-design.md`

## Global Constraints

- Authorized repo: `viluadmcontas2-dot/OMEGAS-V8.2`.
- Authorized branch: `OmegasVerde`; no new branch/worktree/checkout.
- GitHub remote is source authority; MMMACHINE/AgentRed are ephemeral executors/caches only.
- No APK, no installation, no claimed physical validation.
- No RESET_ALL and no automatic Map K / Curve K write.
- Critical physical/scientific computation remains native/Kotlin.
- Start/Pause AutoCal is operational one-touch, but ACK/readback/session/mutual exclusion/receipt remain mandatory.
- Destructive resets remain protected.
- Notion is used only as read-only UX reference in this mission; GitHub Issues + repo remain execution/control evidence.
- Binding UX references consulted: `UIUX-CUSTOMROM`, `UIUX-OMEGADEV`, `OME-STATE-HUMAN-UI`.
- Product invariant from those blueprints: expose human intention/consequence, one state authority, compact normality, technical detail on demand, immediate feedback, preserve context, and understand the main screen in about two seconds.
- **AutoCal cockpit invariant:** AGORA is overlaid on the same native reference plot; no separate AGORA/reference tab or extra navigation.

## Review Focus

1. Native reference valid + live point out of range: live cursor must clamp/edge-mark or report out-of-range without changing reference x/y domain.
2. Native monitor absent/partial but manual reader ready: source selection must be deterministic and freshness/source-labelled.
3. Reconnect/session generation change: stale snapshot/correlation/telemetry from prior session must not leak into the new session.
4. `ACQUIRED_ZONES_*=[1,0,1,0]`: visible dots must preserve physical positions and must not become “first two dots”.
5. 1280×720 with long errors/stale states: primary CTA and reference plot stay visible; technical detail may expand but normal state stays compact.

---

### Task 1: Freeze executable evidence and fan-out read-only audits

**Files:**
- Modify only GitHub issue comments/evidence; no production file write.

**Interfaces:**
- Consumes: spec, issues #57/#58/#59/#61/#63, current remote HEAD, AgentRed receipts #1157–#1168.
- Produces: root-cause matrix and exact write-surface ownership for Tasks 2–10.

- [ ] **Step 1: Reconcile the exact remote HEAD**

```powershell
$head = gh api repos/viluadmcontas2-dot/OMEGAS-V8.2/branches/OmegasVerde --jq .commit.sha
if (-not $head) { throw "HEAD_MISSING" }
$head
```

Expected: a concrete SHA; if it differs from the plan commit parent, inspect diff before continuing.

- [ ] **Step 2: Dispatch independent read-only AgentRed audits**

Audit domains: monitor/projection, native analysis/equivalence, zones/correlation, LEVELS, Start/Pause, USB permission/reconnect, telemetry coherence, suggestions lifecycle, Difference, Map K geometry, bridge parity/dead buttons, persistence/session generation, no-auto-K/RESET_ALL, 1280 runtime harness.

Expected: each job reports evidence only; no job mutates `OmegasVerde`.

- [ ] **Step 3: Classify every major hypothesis**

Use only: `CONFIRMADA`, `FALSIFICADA`, `INCONCLUSIVA`, `BLOQUEADA POR HARDWARE/AMBIENTE`.

### Task 2: Build one native AutoCal UI projection

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt`
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt`
- Modify: `app/src/main/assets/ui/core/autocal-api.js`
- Test: `app/src/test/java/com/omegas/prohub/autocal/AutoCalUiProjectionTest.kt`

**Interfaces:**
- Consumes: native-monitor status/snapshot, manual-reader status/snapshot, session generation, freshness, snapshot hash/revision, existing `AutoMatchSnapshotAnalysis`.
- Produces: `getUiProjection(): JSON` with `source`, `freshness`, `generation`, `revision`, `reference`, `analysis`, `acquisitionZones`, `correlation`, `levelsRaw`, and human state.

- [ ] **Step 1: Write failing projection authority tests**

```kotlin
@Test fun manualReadyIsVisibleWhenMonitorHasNoUsableSnapshot() {
    val projection = project(monitor = emptyMonitor(), manual = readyManual(valid30PointSnapshot()))
    assertEquals("MANUAL_READER", projection.source)
    assertTrue(projection.referenceUsable)
}

@Test fun freshMonitorWinsOverOlderManualSnapshot() {
    val projection = project(monitor = freshMonitor(valid30PointSnapshot()), manual = oldManual(valid30PointSnapshot()))
    assertEquals("NATIVE_MONITOR", projection.source)
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests com.omegas.prohub.autocal.AutoCalUiProjectionTest
```

Expected: FAIL because the projection does not exist.

- [ ] **Step 3: Implement deterministic projection**

Implement explicit source ordering by session generation + semantic freshness; JavaScript never chooses “newer-looking” state.

- [ ] **Step 4: Run GREEN**

Same Gradle command. Expected: PASS.

### Task 3: Native monitor bootstrap + material revision refresh

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt`
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoCalSnapshotManager.kt`
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt`
- Test/Create: `app/src/test/java/com/omegas/prohub/autocal/NativeAutoCalMonitorBootstrapTest.kt`

**Interfaces:**
- Consumes: `AUTO_CAL_ENABLE`, native compact status/counters, `PETR_INJ_TBP`, `PETR_MNFLD_PRESS_RV`, `GAS_MNFLD_PRESS_RV`.
- Produces: initial usable snapshot when protocol allows, semantic revision/hash and material-change event.

- [ ] **Step 1: RED for fresh USB session bootstrap**

```kotlin
@Test fun newSessionRequestsMinimumNativeReferenceWithoutWaitingForLaterTrigger() {
    val result = bootstrapNewSession(fakeTransport())
    assertTrue(result.requestedKeys.containsAll(listOf(
        "AUTO_CAL_ENABLE", "PETR_INJ_TBP", "PETR_MNFLD_PRESS_RV", "GAS_MNFLD_PRESS_RV"
    )))
}
```

- [ ] **Step 2: RED for revision materiality**

Equal physical vectors must keep revision stable; changed vectors/counter materiality must increment revision.

- [ ] **Step 3: Implement minimum read and semantic hash**

Do not add dumb full-snapshot continuous polling.

- [ ] **Step 4: GREEN focused monitor tests**

```bash
./gradlew testDebugUnitTest --tests '*NativeAutoCalMonitor*'
```

### Task 4: Rebuild chart semantics in the same cockpit

**Files:**
- Modify: `app/src/main/assets/ui/screens/autocal-cockpit.js`
- Modify: `app/src/main/assets/ui/core/autocal-api.js`
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt`
- Test/Create: `tests/test_autocal_chart_semantics_contract.py`
- Test: `tests/ui/autocal-cockpit.test.cjs`

**Interfaces:**
- Consumes: Task 2 projection and native `AutoMatchSnapshotAnalysis`.
- Produces: one plot containing native Gasoline reference, GNV reference/equivalence, numeric axes/ticks, and AGORA overlay.

- [ ] **Step 1: RED — physical axes and no floating-cursor-only state**

```python
assert "Petrol Inj. (ms)" in cockpit
assert "MAP (bar)" in cockpit
assert "autocal-axis-tick-x" in cockpit
assert "autocal-axis-tick-y" in cockpit
assert "chart-empty" in cockpit
```

- [ ] **Step 2: RED — AGORA cannot affect reference domain**

A deterministic JS fixture uses a valid reference of X 2–10 ms/Y 0.2–1.2 bar plus live X=50 ms/Y=5 bar; assert reference xMin/xMax/yMin/yMax remain derived only from reference.

- [ ] **Step 3: RED — native same-pressure equivalence is consumed**

Fixture must assert `gasEquivalentTimeMs` from native analysis becomes the GNV X coordinate for the didactic equivalence path; no JS warping algorithm.

- [ ] **Step 4: Implement chart model**

Reference domain is computed from valid reference/analysis only. AGORA renders in the same SVG group hierarchy as an overlay, using edge/out-of-range presentation when outside domain.

- [ ] **Step 5: GREEN**

```bash
python -B tests/test_autocal_chart_semantics_contract.py
node --test tests/ui/autocal-cockpit.test.cjs
node --check app/src/main/assets/ui/screens/autocal-cockpit.js
```

### Task 5: Zones, maturity, correlation retry, and LEVELS raw

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/NativeAutoCalAnchorCorrelator.kt`
- Modify as needed at confirmed consumer boundary: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`
- Modify: `app/src/main/assets/ui/screens/autocal-cockpit.js`
- Test: `app/src/test/java/com/omegas/prohub/learning/NativeAutoCalAnchorCorrelatorTest.kt`
- Test/Create: `app/src/test/java/com/omegas/prohub/autocal/AutoCalZoneProjectionTest.kt`
- Test: `tests/test_mp48_extended_status_contract.py`

**Interfaces:**
- Consumes: actual `ACQUIRED_ZONES_PETROL/GAS` vector positions, maturity events, session generation, temporal telemetry window, MP48 `level_raw`.
- Produces: acquisition positions, maturity state, correlated-region state, retry state, `levelsRaw`.

- [ ] **Step 1: RED positional zone vectors**

```kotlin
assertEquals(listOf(true,false,true,false), projectZones(listOf(1,0,1,0)))
```

Also pin `[1,0,0,0]`, `[1,1,1,0]`, `[1,1,1,1]`.

- [ ] **Step 2: RED correlation retry**

A stale event returns non-correlated, then a later valid compatible frame in the same generation correlates exactly once.

- [ ] **Step 3: RED LEVELS consumer**

Valid `level_raw=173` reaches UI projection as raw `173`; stale/invalid telemetry marks/removes it. No unproved physical percent.

- [ ] **Step 4: Implement and GREEN**

Run focused JVM/Python contracts.

### Task 6: Start/Pause as safe one-touch operational action

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt`
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt`
- Modify: `app/src/main/assets/ui/core/autocal-api.js`
- Modify: `app/src/main/assets/ui/screens/autocal-cockpit.js`
- Test: `app/src/test/java/com/omegas/prohub/autocal/AutoCalNativeActionManagerTest.kt`
- Test/Create: `tests/test_autocal_operational_toggle_contract.py`

**Interfaces:**
- Consumes: USB connected/session generation/mutual exclusion/exact protocol command.
- Produces: `setAcquisitionEnabled(Boolean)` with immediate UI pending state, ACK, readback of `AUTO_CAL_ENABLE`, before/after receipt and inline failure.

- [ ] **Step 1: RED**

Assert Start/Pause does not require WebView review, Android AlertDialog or generic RPM<1200/stopped-car gate, while reset actions still do.

- [ ] **Step 2: RED ACK/readback failure**

Command echo without matching readback must end in failed state, not optimistic success.

- [ ] **Step 3: Implement and GREEN**

```bash
./gradlew testDebugUnitTest --tests com.omegas.prohub.autocal.AutoCalNativeActionManagerTest
python -B tests/test_autocal_operational_toggle_contract.py
```

### Task 7: 1280×720 AutoCal HMI and runtime states

**Files:**
- Modify: `app/src/main/assets/ui/styles-autocal-cockpit.css`
- Modify: `app/src/main/assets/ui/screens/autocal-cockpit.js`
- Test/Create: `tests/ui/autocal-runtime-1280.test.cjs`

**Interfaces:**
- Consumes: projection states and action states.
- Produces: one-screen automotive cockpit with top operational band, dominant plot, compact secondary strip, details drawer.

- [ ] **Step 1: RED runtime DOM fixtures**

Fixtures: disconnected, permission pending, connecting, paused, acquiring, full reference, partial reference, no reference, valid reference+AGORA, AGORA out of range, revision changed, stale telemetry, manual reader busy, Start/Pause success/failure, bridge unavailable.

- [ ] **Step 2: Assert interaction**

Primary CTA one touch; details open/close; sessions reachable; reset protected; every visible button has a handler; no horizontal overflow; no numeric form input on primary surface.

- [ ] **Step 3: Implement HMI**

Use CUSTOMROM/OmegaDev principles: one dominant action, compact normality, semantic cards only, explicit next action on errors, touch targets around 48dp or larger.

- [ ] **Step 4: GREEN runtime**

```bash
node --test tests/ui/autocal-runtime-1280.test.cjs tests/ui/autocal-cockpit.test.cjs
```

### Task 8: Runtime integrity — USB + telemetry coherence

**Files:**
- Modify after root-cause proof: `app/src/main/java/com/omegas/prohub/usb/UsbSerialManager.kt`
- Modify after root-cause proof: `app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt`
- Modify after root-cause proof: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`
- Test: `app/src/test/java/com/omegas/prohub/telemetry/TelemetryStateStoreTest.kt`
- Test: `app/src/test/java/com/omegas/prohub/telemetry/TelemetryStateStoreSessionRaceTest.kt`
- Test/Create: `app/src/test/java/com/omegas/prohub/usb/UsbPermissionLifecycleTest.kt`

**Interfaces:**
- Consumes: device identity, permission pending, generation, canonical frame sequence/captured/published timestamps.
- Produces: no repeated permission prompt without identity/session cause; atomic telemetry snapshot for RPM/MAP/Petrol Inj/fuel.

- [ ] **Step 1: RED USB prompt loop reproduction**
- [ ] **Step 2: RED cross-frame falsification `MAP.sequence != PetrolInj.sequence`**
- [ ] **Step 3: Implement only if mechanism is confirmed**
- [ ] **Step 4: GREEN focused tests**

### Task 9: Learning semantics — Suggestions, Difference, Map K, MP48+OBD contract

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt`
- Modify: `app/src/main/java/com/omegas/prohub/calibration/AdvisorSuggestionAdapterV7.kt`
- Modify: `app/src/main/assets/ui/screens/learning.js`
- Modify only after call-site proof: Difference projection file discovered by Task 1 audit
- Modify physical geometry authority only after mismatch proof: `app/src/main/java/com/omegas/v7/runtime/CalibrationShapeV7.kt` and current JS projection consumer
- Test: `app/src/test/java/com/omegas/prohub/learning/AssistedCalibrationAdvisorTest.kt`
- Test: `tests/test_block3_suggestion_ui_contract.py`
- Test: `tests/test_map_kotlin_math_authority_contract.py`
- Test: `tests/test_mp48_k_map_axes_contract.py`

**Interfaces:**
- Produces suggestion lifecycle `OBSERVATION -> CANDIDATE -> STABLE/REVALIDATING -> APPLIED/SUPERSEDED`.
- Difference produces separate NOW / LOCAL_STABLE / TREND.
- Map K keeps one physical center/edge/bin/address authority.
- Paired evidence uses `E_inj = PetrolInj_GNV / PetrolInj_gasolina - 1`; GNV RPM×PetrolInj_GNV addresses Map K; STFT is witness; LTFT is diagnostic/common-mode, never blindly summed.

- [ ] **Step 1: RED one-strong-visit cannot publish durable suggestion**
- [ ] **Step 2: RED direction conflict enters REVALIDATING before inversion**
- [ ] **Step 3: RED Difference one frame cannot rewrite stable/trend narrative**
- [ ] **Step 4: RED Map K centers/borders 4.5/5.5/6.0/7.0 + under/over + JS↔Kotlin**
- [ ] **Step 5: Implement minimal coherent changes and GREEN**

### Task 10: Global consumer/dead-code audit and safe removal

**Files:**
- Modify only files classified `DEAD-PROVEN`; preserve `ACTIVE`, `COMPATIBILITY`, `TEST-ONLY`, `UNKNOWN`.

**Interfaces:**
- Producer → API → store → bridge → caller → route → renderer → persistence/dynamic/manifest.

- [ ] **Step 1: Generate producer/consumer graph for all visible controls and bridge methods**
- [ ] **Step 2: Prove button handler parity**
- [ ] **Step 3: Prove no automatic K write and no RESET_ALL**
- [ ] **Step 4: Remove only DEAD-PROVEN slices one at a time**
- [ ] **Step 5: Rerun affected regression after every removal**

### Task 11: Integrated verification and independent review

**Files:** no production write unless a reviewed Critical/Important finding is reproduced by RED first.

- [ ] **Step 1: Fresh remote reconciliation + exact diff**
- [ ] **Step 2: Focused Python/Node/JVM tests**
- [ ] **Step 3: Full `./gradlew testDebugUnitTest`**
- [ ] **Step 4: `./gradlew lintDebug`**
- [ ] **Step 5: repository fast checks**
- [ ] **Step 6: 1280×720 DOM/click smoke**
- [ ] **Step 7: protocol-byte regression + no-auto-K + no-RESET_ALL audit**
- [ ] **Step 8: independent code-verification reviewer against final SHA/spec/diff**
- [ ] **Step 9: independent product/HMI adversarial review against CUSTOMROM/OmegaDev principles**
- [ ] **Step 10: fix Critical/Important findings with RED→GREEN, then rerun final suites**

## Final acceptance

The final report uses PASS/PARTIAL/FAIL/INCONCLUSIVE per major area and ends with `READY FOR PHYSICAL TEST` or `NOT READY FOR PHYSICAL TEST`. It explicitly states that no APK was generated.
