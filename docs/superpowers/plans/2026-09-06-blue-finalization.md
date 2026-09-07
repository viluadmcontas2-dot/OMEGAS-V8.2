# OMEGAS Blue Finalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the current Blue release by clearing runtime/legibility regressions, making Curve K batch-editable without jank, and integrating same-region GNV STFT as a witness to the primary MP48 gasoline?GNV equivalence while preserving Blue/write authority.

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
- Final software proof before artifact authorization is remote `FAST -> FULL JVM/unit -> lint -> READY FOR APK GENERATION` on one exact SHA. APK is a separate owner-gated step.

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

### Task 3: GNV-only OBD witness + current-GNV Map K addressing

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/obd/ObdWitnessEngine.kt`
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueWitnessConfidence.kt`
- Create: `app/src/main/java/com/omegas/prohub/blue/BlueMapKAddressing.kt`
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt`
- Modify: `app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt`
- Modify: `app/src/main/assets/ui/screens/obd.js`
- Test: focused OBD/Blue unit tests plus multimedia witness contract.

**Scientific contract:**
- MP48 compares gasoline and GNV in equivalent `RPM x MAP`; recent gasoline microbursts are preferred when available.
- OBD scientific input is STFT Bank 1 on GNV only. Gasoline OBD STFT is not required.
- LTFT does not participate in correction math.
- Raw GNV STFT cannot manufacture a standalone correction ratio or K target.
- Blue may boost confidence only when the STFT witness belongs to the same calibration state and same current GNV RPM/MAP/Petrol-Inj. region and agrees in direction with the MP48 error.
- Map K evidence/correction address uses `comparison.rpm x comparison.petrolOnCngMs`; `petrolTargetMs` is never the correction address.

- [ ] **Step 1: Prove RED** for GNV STFT without gasoline OBD, region mismatch rejection, temporal gasoline preference, and GNV Map K addressing.
- [ ] **Step 2: Implement minimum production changes** while keeping OBD writer-isolated.
- [ ] **Step 3: Update OBD/MDT UI** to show GNV STFT + MP48 pairing without a fake gasoline-OBD residual.
- [ ] **Step 4: Run focused JVM/Node tests** and prove writer isolation.
- [ ] **Step 5: Run FAST inventory**; unrelated recovery REDs remain owned by their issues, not hidden or weakened.

---

### Task 4: Release convergence and evidence

**Files:**
- Modify: `STATUS.md`
- Optionally update: `docs/superpowers/specs/2026-09-05-blue-obd-witness-design.md` with a historical pointer to the superseding finalization design.

**Interfaces:**
- Produces exact-SHA release evidence only.

- [ ] **Step 1: Run canonical GitHub Actions on final code SHA**

Required order before owner artifact authorization: `FAST -> FULL JVM/unit -> lint -> READY FOR APK GENERATION`. Do not run the APK job in this plan unless the owner explicitly authorizes it in a later step.

- [ ] **Step 2: Read full job results and artifact metadata**

No completion claim from queued/running jobs.

- [ ] **Step 3: Update STATUS.md**

Record branch, exact final SHA, CI run id, FAST result, JVM result, lint result, `READY FOR APK GENERATION`, and explicit limits: APK not generated and no physical fuel-economy/driveability proof yet.

- [ ] **Step 4: Verify STATUS-only commit does not invalidate code evidence**

If STATUS commit is docs-only and CI skips Android, record both code SHA and evidence SHA explicitly. If workflow reruns, wait for terminal result.

- [ ] **Step 5: Final remote audit**

Verify no temporary applicator/workflow remains, no automatic writer path was introduced, Curve K selection is batch-capable, and no shipped CSS text is below 10px.
