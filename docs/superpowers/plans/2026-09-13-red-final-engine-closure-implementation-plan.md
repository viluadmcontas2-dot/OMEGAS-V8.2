# OMEGAS RED Final Engine Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar a engine existente do OMEGAS RED com gasolina como superfície permanente, interpolação espacial contínua suportada, Predictor convergente, sugestões manuais e UI operacional simples, sem criar engine paralela.

**Architecture:** A memória física de gasolina permanece a autoridade de referência `(RPM, MAP) -> Petrol Inj.`. A superfície contínua é construída a partir de evidência observada e pode produzir estados `OBSERVED`, `INTERPOLATED`, `PREDICTED` ou `UNSUPPORTED`; o Predictor usa a mesma autoridade e perde peso conforme a evidência observada cresce. O erro GNV usa a referência de gasolina por RPM×MAP, enquanto o endereço do Mapa K continua usando RPM_GNV×PetrolInj_GNV. Toda computação pesada fica fora do hot path.

**Tech Stack:** Android/Kotlin, JSON persistence/projection, WebView JavaScript UI, Python/Node contract tests, Gradle/JUnit, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-13-red-final-engine-closure-design.md`

## Global Constraints

- Repository authority: `viluadmcontas2-dot/OMEGAS-V8.2`.
- Canonical branch: `work/omegas-blue-causal-engine`.
- Do not create a second learning/calibration engine.
- Gasoline evidence never expires by time, GNV epoch, Curve K revision, or Map K revision.
- Short temporal windows may exist only for causal before/after K intervention analysis, never for gasoline-reference existence.
- Scientific equivalence: `(RPM, MAP) -> Petrol Inj. expected on gasoline`.
- GNV error: `PetrolInj_GNV / PetrolInj_GasolineExpected - 1`.
- Map K address: current `RPM_GNV × PetrolInj_GNV` only.
- Observed, interpolated, predicted and unsupported states must remain distinguishable.
- No aggressive extrapolation outside supported geometry.
- No automatic ECU write; prepare/review/confirm/ACK/readback remains manual.
- Interpolation/prediction must not run as heavy work per telemetry frame.
- TDD required: RED proof -> minimal implementation -> focused GREEN -> broad regression.
- Final software gate: FAST -> JVM/unit -> lint on exact SHA; APK remains a separate owner-authorized gate.

---

### Task 1: Remove the 30-second gasoline-reference regression

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt`
- Modify/Test: `app/src/test/java/com/omegas/prohub/blue/BlueTemporalPairingTest.kt`
- Test: `app/src/test/java/com/omegas/prohub/blue/BluePairedEvidenceAuthorityTest.kt`

**Interfaces:**
- Consumes: `FuelEvidence`, `BluePolicy`, existing `normalizedDistance()` geometry.
- Produces: `BlueCausalEngine.petrolReference(target, petrolEvidence): BluePetrolReference?` whose validity depends on physical support/quality, never age.

- [ ] **Step 1: Write RED tests for non-expiring gasoline reference.**
  Add explicit cases with equivalent gasoline evidence 31 s, 30 min, 24 h and several days before current GNV evidence; all must resolve a reference when physical support/quality are valid.
- [ ] **Step 2: Write a RED test proving future gasoline is not required and age is not part of equivalence.**
  Historical valid gasoline must work; a timestamp alone must never return `null`.
- [ ] **Step 3: Run focused JUnit tests and capture expected failure at the current `if (temporalPairs.isEmpty()) return null` behavior.**
- [ ] **Step 4: Remove temporal gating from `petrolReference()`.**
  Candidate ranking remains physical-distance/quality based. If temporal proximity is retained at all, it may be metadata/tie-break information only and must not invalidate old gasoline.
- [ ] **Step 5: Keep intervention timing separate.**
  Do not move the 30 s window elsewhere unless an existing causal before/after K path already owns it.
- [ ] **Step 6: Run focused tests GREEN, then the Blue causal regression set.**
- [ ] **Step 7: Commit:** `fix(red): restore permanent gasoline reference surface`.

### Task 2: Make the gasoline surface continuous across 2D/diagonal supported space

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/ContinuousLearningMath.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt`
- Create/Test: `app/src/test/java/com/omegas/prohub/learning/GasolineSurfaceInterpolationTest.kt`

**Interfaces:**
- Consumes: observed gasoline support points `(rpm, mapBar, petrolMs, quality)`.
- Produces: one surface query contract conceptually equivalent to `estimateGasoline(rpm, mapBar) -> {valueMs, supportState, confidence, provenance}`.
- Support states: `OBSERVED`, `INTERPOLATED`, `UNSUPPORTED` at this layer.

- [ ] **Step 1: RED — vertical/horizontal hole.**
  Two or more coherent observed points bracketing an internal query must produce an interpolated value rather than an empty cell.
- [ ] **Step 2: RED — diagonal support.**
  Four surrounding/diagonal observations must interpolate an internal point independent of whether any frame landed in the exact target cell.
- [ ] **Step 3: RED — irregular 2D neighborhood.**
  Use non-grid-aligned RPM/MAP points and require a finite weighted estimate inside supported geometry.
- [ ] **Step 4: RED — unsupported outside domain.**
  A point outside acceptable support/hull must return `UNSUPPORTED`, not nearest-neighbor truth.
- [ ] **Step 5: Implement the smallest continuous-surface query using existing math.**
  Reuse `ContinuousLearningMath`; do not create a competing engine. Weight by physical proximity and evidence quality. Preserve provenance IDs/support count.
- [ ] **Step 6: Keep grid projection downstream.**
  `LearningGridProjection` may render/interpolate into cells, but scientific support must derive from continuous RPM×MAP geometry, not from cell occupancy.
- [ ] **Step 7: Run focused JUnit tests GREEN and existing learning projection tests.**
- [ ] **Step 8: Commit:** `feat(red): close continuous gasoline surface interpolation`.

### Task 3: Restore Predictor as an early estimated surface that converges to evidence

**Files:**
- Modify only the existing Predictor/advisor implementation discovered on branch before execution; do not add a new engine.
- Test: `tests/test_predictor_map_residual_contract.py`
- Test: `tests/ui/predictor-consumer.test.cjs`
- Test: `tests/ui/predictor-live-cell.test.cjs`
- Test: `tests/ui/predictor-route.test.cjs`
- Test: `tests/test_runtime_predictor_router_soak.py`
- Reference test: `tools/phase06/test_equivalence_oracle.py`

**Interfaces:**
- Consumes: gasoline surface estimate, observed GNV comparisons, global Curve K trend, supported local RPM×MAP residuals.
- Produces: predicted surface values with explicit state `PREDICTED`/`GLOBAL_ONLY`/`UNSUPPORTED`, confidence and uncertainty.

- [ ] **Step 1: Before editing, identify the single current Predictor runtime owner and record the exact source path in the task checkpoint.**
  If more than one production owner is reachable, stop and remove/disable the duplicate rather than layering another implementation.
- [ ] **Step 2: RED — sparse but supported region.**
  With limited evidence inside supported geometry, Predictor must emit a useful estimate with explicit uncertainty.
- [ ] **Step 3: RED — convergence.**
  Add real evidence near a predicted point and require the estimate to move toward observed truth while uncertainty decreases.
- [ ] **Step 4: RED — observation dominance.**
  Where direct observed evidence is strong, observed truth must win over previous Predictor output.
- [ ] **Step 5: RED — outside support.**
  Predictor may return `GLOBAL_ONLY` or `UNSUPPORTED`, never fabricated local evidence.
- [ ] **Step 6: Implement with the existing model:** `error_ratio = global_curve(petrol_target_ms) + local_residual(rpm,map_bar) + noise`.
  Keep local residual support-bounded; do not copy a local residual across distant RPM/MAP.
- [ ] **Step 7: Ensure output provenance distinguishes observed/interpolated/predicted.**
- [ ] **Step 8: Run Predictor Python/Node/runtime tests GREEN.**
- [ ] **Step 9: Commit:** `feat(red): restore evidence-convergent predictor surface`.

### Task 4: Preserve GNV epoch isolation and Map K physical addressing

**Files:**
- Verify/modify: `app/src/main/java/com/omegas/prohub/blue/BlueMapKAddressing.kt`
- Verify/modify: `app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt`
- Test: existing `ObdMapKSuggestionTest.kt`
- Test: existing Blue calibration/causal coordinator tests.

**Interfaces:**
- Consumes: `FuelComparison` with `rpm`, `mapBar`, `petrolOnCngMs`, calibration revision.
- Produces: Map K cell address from current GNV coordinates and GNV epoch transitions only.

- [ ] **Step 1: RED — gasoline survives calibration change.**
  Confirmed Curve K/Map K mutation must not clear, age, or epoch-shift gasoline evidence.
- [ ] **Step 2: RED — GNV epoch changes.**
  New confirmed/readback calibration revision starts a new GNV state while retaining old GNV only as history.
- [ ] **Step 3: RED — Map K address.**
  Assert row/column is derived from current `RPM_GNV × PetrolInj_GNV`, never gasoline target Petrol Inj.
- [ ] **Step 4: Implement only if current runtime violates a test.**
  No refactor for style.
- [ ] **Step 5: Run calibration/addressing regressions GREEN.**
- [ ] **Step 6: Commit:** `fix(red): preserve gasoline authority across gnv calibration epochs`.

### Task 5: Simplify Learning UI around operator decisions

**Files:**
- Modify: existing Learning screen implementation that renders `learningCellDetail` / `LearningScreen` on the branch.
- Test: existing Learning UI tests plus a new/updated contract test under `tests/ui/`.

**Interfaces:**
- Consumes: projected cell state, gasoline expected, GNV observed, deviation, confidence, support origin, suggestion.
- Produces: operational cell panel with only the fields defined by the spec.

- [ ] **Step 1: RED — forbid engineering jargon in the primary cell pane.**
  Primary UI must not render `BlueCausalEngine`, calibration-state internals, epoch history, ACK/readback contract text or internal IDs.
- [ ] **Step 2: RED — require operational fields.**
  Require RPM/MAP context, gasoline expected, GNV observed, deviation, confidence, origin (`medido`, `interpolado`, `predito`) and suggested adjustment when available.
- [ ] **Step 3: RED — compact grid cells.**
  Grid cells show the principal value/state without repeated long labels such as `ms gasolina`, `ms no GNV`, or engineering explanations in every cell.
- [ ] **Step 4: Implement minimal UI simplification without deleting diagnostic data from backend snapshots.**
- [ ] **Step 5: Run all Learning UI tests GREEN.**
- [ ] **Step 6: Commit:** `refactor(red-ui): simplify learning surface for calibration`.

### Task 6: Keep surface/predictor work out of the telemetry hot path

**Files:**
- Verify affected telemetry/persistence scheduler files on branch before edit.
- Test: `tests/test_runtime_predictor_router_soak.py`
- Test: existing self-paced scheduler contract under `tests/ui/telemetry-self-paced-scheduler.test.cjs`.

**Interfaces:**
- Consumes: semantic evidence revision/update events.
- Produces: cached surface/predictor snapshot refreshed only when evidence/calibration meaningfully changes.

- [ ] **Step 1: RED — no heavy surface rebuild per frame/render tick.**
  Contract test must detect direct full-history/surface recomputation from telemetry tick or UI render loop.
- [ ] **Step 2: RED — latest revision wins.**
  Rapid evidence changes must coalesce; stale computations must not queue an unbounded backlog.
- [ ] **Step 3: Implement revision-driven cached recomputation only if needed.**
- [ ] **Step 4: Run soak/scheduler tests GREEN.**
- [ ] **Step 5: Commit:** `perf(red): keep learning surface off telemetry hot path`.

### Task 7: Adversarial integration matrix

**Files:**
- Extend existing Blue/RED causal, learning, Predictor and UI tests; prefer existing suites over adding redundant harnesses.
- Contract tests may live under `tests/` when cross-layer behavior cannot be expressed cleanly in JUnit.

**Interfaces:**
- Consumes: all outputs from Tasks 1-6.
- Produces: end-to-end evidence that one authority survives across learning, prediction, suggestion and UI.

- [ ] **Step 1: Add exact regression scenario from the vehicle screenshots.**
  Large gasoline history + large GNV history + nearly identical RPM/MAP must yield a valid comparison even without a recent fuel switch.
- [ ] **Step 2: Add 2D/diagonal interpolation matrix.**
  Exercise center holes, diagonal holes, irregular neighborhoods and edge-of-support behavior.
- [ ] **Step 3: Add Predictor convergence matrix.**
  Sparse -> predicted; more evidence -> lower uncertainty; direct observation -> observation dominance.
- [ ] **Step 4: Add calibration epoch matrix.**
  Gasoline persists, GNV epoch changes, Map K address remains current-GNV based.
- [ ] **Step 5: Add no-auto-write assertion across Predictor and Learning suggestions.**
- [ ] **Step 6: Run focused integration suite GREEN.**
- [ ] **Step 7: Commit:** `test(red): lock final engine closure invariants`.

### Task 8: Final verification, review and artifact gate

**Files:**
- Update only after all code/tests are green: `STATUS.md` and any existing canonical spec/task status required by repo governance.
- Do not modify product behavior in this task.

**Interfaces:**
- Consumes: final candidate SHA from Tasks 1-7.
- Produces: exact-SHA proof and owner-authorized APK candidate.

- [ ] **Step 1: Re-read canonical remote HEAD and confirm no divergence before final verification.**
- [ ] **Step 2: Run FAST:** `python3 -B tools/run_checks.py`.
- [ ] **Step 3: Run FULL:** `./gradlew testDebugUnitTest lintDebug -PomegasAbis=armeabi-v7a --no-daemon --stacktrace`.
- [ ] **Step 4: Perform an adversarial code review focused on permanent gasoline, support-bounded interpolation, Predictor convergence, Map K address, GNV epochs, no writer and hot-path performance.**
- [ ] **Step 5: If review changes code, rerun Steps 2-4 on the new exact SHA.**
- [ ] **Step 6: Update `STATUS.md` with exact SHA/tree/run and known limits, then rerun FAST/FULL if STATUS is part of final-SHA governance.**
- [ ] **Step 7: Stop at `READY FOR APK GENERATION` until the already-authorized artifact gate is explicitly invoked by the existing workflow contract.**
- [ ] **Step 8: Generate APK only through the canonical owner-authorized workflow; verify receipt, ZIP integrity, APK SHA-256/bytes, `apksigner verify --verbose --print-certs`, and `aapt dump badging` against the exact final SHA.**
- [ ] **Step 9: Keep physical vehicle validation as a separate human gate; do not claim fuel economy, stability or real-device behavior from CI alone.**

## Self-review checklist

- Spec coverage: permanent gasoline, continuous/diagonal interpolation, supported-domain abstention, Predictor convergence, state provenance, 30 s regression removal, GNV-only epochs, correct Map K address, simplified UI, performance, manual write, CI/APK gates are each mapped to tasks.
- Placeholder scan: no TODO/TBD implementation placeholders. The only execution-time discovery requirement is identifying the already-existing single Predictor owner before editing; creation of a parallel owner is explicitly forbidden.
- Type/semantic consistency: gasoline surface is queried by RPM×MAP; Map K address remains RPM_GNV×PetrolInj_GNV; Predictor consumes the same authority and does not become a calibration writer.
