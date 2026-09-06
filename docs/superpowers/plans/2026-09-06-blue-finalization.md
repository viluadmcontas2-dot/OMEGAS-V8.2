# OMEGAS Blue Finalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the current Blue release by clearing the known CI legibility failure, making Curve K batch-editable without jank, and promoting gasoline-relative OBD correction into first-class Blue physical evidence while preserving MP48/write authority.

**Architecture:** Keep `BlueCausalEngine` as the single calibration decision authority. MP48 supplies fuel/state/location; OBD supplies timestamp-paired physical trim evidence. Curve K batching remains UI-only preparation and reuses the existing native preview/review/write path.

**Tech Stack:** Android/Kotlin, WebView JavaScript/CSS, Node test runner, Python contract checks, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-06-blue-finalization-design.md`

## Global Constraints
- Canonical branch: `work/omegas-blue-causal-engine`.
- No merge from `main`.
- No automatic ECU write.
- Manual write remains prepare -> review -> confirm -> write -> ACK -> readback.
- MP48 is authoritative for fuel; transition is gasoline; cut-off is excluded.
- OBD remains unable to reach writer APIs.
- No shipped CSS text below 10 px.
- TDD: every new behavior gets a failing test before production code.
- Final proof is remote `FAST -> FULL JVM/unit -> lint -> APK` on one exact SHA.

---

### Task 1: Clear the existing multimedia legibility failure

**Files:**
- Modify: `app/src/main/assets/ui/styles-expansion.css`
- Modify: `app/src/main/assets/ui/styles-expansion-panels.css`
- Existing test: `tests/ui/multimedia-distance-legibility.test.cjs`

**Interfaces:**
- Consumes: existing CSS selectors.
- Produces: identical feature behavior with every declared `font-size` at least `10px`.

- [ ] **Step 1: Use the already-failing regression test as RED**

Remote evidence: run `34042101992`, FAST job `101510605947`, fails because these two stylesheets still contain `6.8px`, `7.5px`, and `8.5px` declarations.

- [ ] **Step 2: Replace every sub-10px declaration with a readable value**

Rules:
```text
6.8px / 7.5px -> 10px minimum
8.5px -> 10px minimum
primary/important cockpit text -> 11px or larger where layout allows
```
Do not delete content or weaken the test.

- [ ] **Step 3: Push and verify FAST**

Expected: `multimedia-distance-legibility.test.cjs` becomes GREEN and no prior FAST test regresses.

---

### Task 2: Curve K multi-selection and batch nudge

**Files:**
- Create: `tests/ui/curve-batch-edit.test.cjs`
- Modify: `app/src/main/assets/ui/screens/curve.js`
- Modify: `app/src/main/assets/ui/index.html`
- Modify: `app/src/main/assets/ui/styles-witness-multimedia.css` or the existing Curve K stylesheet section that owns the point states.

**Interfaces:**
- Produces UI state: `selectedIndices: Set<number>`.
- Produces methods: `selectOnly(index)`, `toggleSelection(index)`, `clearSelection()`, `nudgeSelection(delta)`, and pointer-drag selection over `data-curve-index` targets.
- Reuses: `api.previewCurvePoint(index, requested)` and existing `proposals: Map`.

- [ ] **Step 1: Write RED Node contract**

The test must require:
```js
for (const token of [
  'selectedIndices',
  'toggleSelection',
  'clearSelection',
  'nudgeSelection',
  'pointerdown',
  'pointerenter',
  'Limpar seleção',
]) assert.equal(source.includes(token), true);
```
It must also reject the old one-point-only nudge binding:
```js
assert.equal(source.includes('data-curve-nudge]') && source.includes('nudgeActive('), false);
```

- [ ] **Step 2: Run FAST and confirm RED for missing batch behavior**

Expected failure reason: required multi-select/batch tokens are absent.

- [ ] **Step 3: Implement minimal batch selection**

Constructor:
```js
this.selectedIndices = new Set();
this.dragSelecting = false;
```

Selection semantics:
```js
selectOnly(index) { this.selectedIndices.clear(); this.selectedIndices.add(index); this.activeIndex = index; }
toggleSelection(index) { /* add/remove index; keep an active point when possible */ }
clearSelection() { this.selectedIndices.clear(); this.activeIndex = null; this.renderChart(); }
```

Batch nudge semantics:
```js
nudgeSelection(delta) {
  const indices = [...this.selectedIndices];
  if (!indices.length || !delta) return;
  for (const index of indices) {
    const point = this.points().find(item => Number(item.index) === index);
    const current = finite(this.proposals.get(index)?.targetFactor ?? point?.factor);
    if (current === null) continue;
    const requested = Math.max(0.6, Math.min(4, current + delta));
    const preview = this.api.previewCurvePoint(index, requested);
    if (preview?.ok) this.acceptPreview(preview, true);
  }
  this.renderChart();
  this.renderProposalList();
}
```
Exactly one chart/proposal render occurs after the loop.

- [ ] **Step 4: Add drag selection without writing**

Pointer behavior uses the existing invisible 30-point hit circles. `pointerdown` starts selection, `pointerenter` adds traversed points while pressed, `pointerup/pointercancel` ends it. Selection never calls writer APIs.

- [ ] **Step 5: Update didactic copy**

Replace `edite cada ponto individualmente` and `toque em qualquer um dos 30 pontos` with copy explaining tap/drag multi-selection. Add `Limpar seleção` near the existing editor actions.

- [ ] **Step 6: Run FAST and confirm GREEN**

Expected: new batch test and all prior UI/native-authority contracts pass.

---

### Task 3: MP48 fuel gate + first-class OBD physical correction

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/obd/ObdFuelState.kt`
- Create: `app/src/test/java/com/omegas/prohub/obd/ObdFuelStateTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/obd/ObdWitnessEngine.kt`
- Modify: `app/src/test/java/com/omegas/prohub/obd/ObdWitnessEngineTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueWitnessConfidence.kt`
- Modify: `app/src/test/java/com/omegas/prohub/blue/BlueWitnessConfidenceTest.kt` if present; otherwise create it.
- Modify: `app/src/main/assets/ui/screens/obd.js`

**Interfaces:**
- `ObdFuelState.normalize(raw: String): ObdScientificFuel?`
- `ObdScientificFuel.PETROL`, `ObdScientificFuel.CNG`, with cut-off/unknown returning null.
- `ObdWitnessResult` adds `correctionRatio: Double?`, `errorLog: Double?`, `correctionPercent: Double?`.

- [ ] **Step 1: Write RED tests for fuel normalization**

Required expectations:
```kotlin
assertEquals(PETROL, ObdFuelState.normalize("GASOLINA"))
assertEquals(PETROL, ObdFuelState.normalize("TRANSITION"))
assertEquals(PETROL, ObdFuelState.normalize("TRANSICAO"))
assertEquals(CNG, ObdFuelState.normalize("GNV"))
assertNull(ObdFuelState.normalize("CUT_OFF"))
assertNull(ObdFuelState.normalize("CUTOFF"))
assertNull(ObdFuelState.normalize("UNKNOWN"))
```

- [ ] **Step 2: Write RED test for physical correction math**

For gasoline STFT `0%` and GNV STFT `+10%` in compatible conditions:
```kotlin
assertEquals(1.10, result.correctionRatio!!, 1e-9)
assertEquals(kotlin.math.ln(1.10), result.errorLog!!, 1e-9)
assertEquals(10.0, result.correctionPercent!!, 1e-9)
```
For gasoline `+2%` and GNV `+8%`, expected ratio is `1.08 / 1.02`, not a naive six-point subtraction.

- [ ] **Step 3: Run JVM test and confirm RED**

Expected: missing fuel policy/result fields.

- [ ] **Step 4: Implement the pure fuel policy and correction math**

`ObdWitnessEngine` uses the shared policy instead of a private gasoline/GNV parser. After median gasoline/GNV trims:
```kotlin
val gasolineFactor = 1.0 + gasolineMedian / 100.0
val cngFactor = 1.0 + gnvMedian / 100.0
val ratio = cngFactor / gasolineFactor
val errorLog = ln(ratio)
val correctionPercent = (ratio - 1.0) * 100.0
```
Reject pathological non-positive factors.

- [ ] **Step 5: Gate collection from MP48 fuel state**

`TelemetryForegroundService.pairObdStftWitness` calls `ObdFuelState.normalize(frame fuel)`; transition becomes gasoline; cut-off/unknown returns before `observe`.

- [ ] **Step 6: Promote physical correction in Blue projection without creating another authority**

`BlueWitnessConfidence.project` includes the OBD physical fields in `obdWitness` and keeps correction targets untouched. If Blue and OBD disagree in sign, `CONFLICTS` remains fail-closed. The UI labels the measurement as `Correção física OBD`, not as an independently calculated K target.

- [ ] **Step 7: Make OBD/MDT screen show the first-class measurement**

Show live STFT, gasoline reference, GNV STFT, correction percent/ratio, matched RPM/MAP/Petrol Inj., fuel, quality/support and conflict/insufficient state. Do not add writer controls.

- [ ] **Step 8: Run targeted JVM + FAST**

Expected: fuel-state, correction math, writer-isolation and prior witness tests all pass.

---

### Task 4: Release convergence and evidence

**Files:**
- Modify: `STATUS.md`
- Optionally update: `docs/superpowers/specs/2026-09-05-blue-obd-witness-design.md` with a historical pointer to the superseding finalization design.

**Interfaces:**
- Produces exact-SHA release evidence only.

- [ ] **Step 1: Run canonical GitHub Actions on final code SHA**

Required order: `FAST -> FULL JVM/unit -> lint -> APK`.

- [ ] **Step 2: Read full job results and artifact metadata**

No completion claim from queued/running jobs.

- [ ] **Step 3: Update STATUS.md**

Record branch, exact final SHA, CI run id, FAST result, JVM result, lint result, APK result/artifact, and explicit limit: no physical fuel-economy/driveability proof yet.

- [ ] **Step 4: Verify STATUS-only commit does not invalidate code evidence**

If STATUS commit is docs-only and CI skips Android, record both code SHA and evidence SHA explicitly. If workflow reruns, wait for terminal result.

- [ ] **Step 5: Final remote audit**

Verify no temporary applicator/workflow remains, no automatic writer path was introduced, Curve K selection is batch-capable, and no shipped CSS text is below 10px.
