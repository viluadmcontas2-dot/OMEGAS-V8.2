# OMEGAS VERDE Scientific Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the already-approved scientific behavior inside the existing OmegasVerde runtime, eliminate pathological learning-map volatility, and publish a new APK only after every behavior-critical discovery is implemented and tested.

**Architecture:** Reuse the existing Verde/V7 seams instead of adding a parallel engine. `AdaptivePetrolReference` remains the gasoline-reference math seam, `LearningStabilityV7` becomes the authoritative robust cell-state seam, `CalibrationStateV7`/`V7SessionRuntime` own confirmed calibration state and post-readback evidence, and `AssistedCalibrationAdvisor` + `AdvisorSuggestionAdapterV7` remain the suggestion pipeline.

**Tech Stack:** Kotlin/JVM Android app, JUnit, JavaScript UI assets, GitHub Actions/Gradle.

**Spec:** `docs/superpowers/specs/2026-09-15-omegas-verde-scientific-consolidation-design.md`

## Global Constraints

- Repository: `viluadmcontas2-dot/OMEGAS-V8.2`.
- Branch only: `OmegasVerde`.
- Remote GitHub source is authoritative; no new branch/worktree/clone.
- No automatic ECU writes and no claim of physical vehicle validation.
- F2 stays frozen; no refit.
- Prediction/interpolation never becomes evidence.
- Gasoline collection must remain valid at any point in the workflow.
- Curve K global trend must be removed before Mapa K residual.
- Do not resurrect the failed TRUST confidence gate as automatic authorization.
- No opaque ML and no hardcoded MAP≈0.775 special rule.
- APK is generated only after Tasks 1–6 are green and the matrix has no unresolved behavior-critical gap.

---

### Task 1: Scientific coverage matrix and volatility reproduction

**Files:**
- Create: `docs/research/omegas-verde/scientific-consolidation/MATRIX.md`
- Create: `app/src/test/java/com/omegas/v7/runtime/LearningStabilityVolatilityTest.kt`
- Modify only if necessary for test access: `app/src/main/java/com/omegas/v7/runtime/LearningStabilityV7.kt`

**Interfaces:**
- Consumes: `LearningStabilityV7.mapCell(...)`, current `FuelComparisonV7`.
- Produces: a deterministic volatility regression test and the baseline scientific status matrix.

- [ ] **Step 1: Write `MATRIX.md` with every accepted/rejected scientific finding and current status.**

Required rows: frozen F2; carry-forward S; cautious S update; anisotropic local quadratic residual; adaptive continuous reference; prediction!=evidence; later physical petrol supersedes prediction; RPM×MAP gasoline geometry; RPM×Petrol Mapa K geometry; Curve-before-Map decomposition; calibration material-state identity; post-readback causal response; 0.75 first step; conditional ~0.90 second step; no visit-count escalation; failed TRUST not authority; no auto-write; gasoline-any-time; no MAP.775 hardcode; no opaque ML; robust map-cell presentation.

- [ ] **Step 2: Write a focused volatility test with a chronological cell sequence.**

Use a same physical map cell and visits with error sequence around `+1.7, +2.0, +1.5, +1.9, +2.2, +1.6`, followed by isolated `+15` and `-10` outliers. Assert that `LearningStabilityV7` exposes a robust recent/consolidated center and that one isolated outlier does not replace a consolidated primary value.

- [ ] **Step 3: Add a second sequence proving responsiveness.**

After a stable positive baseline, feed at least six coherent negative visits meeting the existing promotion criteria. Assert that the state progresses through `REVALIDATING` and eventually promotes a new generation rather than freezing forever.

- [ ] **Step 4: Run `./gradlew testDebugUnitTest --tests '*LearningStabilityVolatilityTest'`.**

Expected baseline: test should expose the exact existing gap if the UI/runtime projection cannot provide a robust primary value in `LEARNING`; `LearningStabilityV7` itself may already pass the robust-statistics part.

- [ ] **Step 5: Record baseline metrics in `MATRIX.md`: raw max jump, robust max jump, sign flips, state transitions, effective/unique visits.**

---

### Task 2: Make robust stability the authoritative learning-map value

**Files:**
- Modify: `app/src/main/assets/ui/screens/learning.js`
- Test: `app/src/test/java/com/omegas/v7/runtime/LearningStabilityVolatilityTest.kt`
- Test/update contract if present: `tests/test_learning_consolidation_contract.py`

**Interfaces:**
- Consumes: `learningStability.map[*].consolidatedErrorPercent`, `recentErrorPercent`, `state`.
- Produces: primary map-cell display semantics: LEARNING→recent robust center; CONSOLIDATED→consolidated; REVALIDATING→consolidated primary + recent trend metadata.

- [ ] **Step 1: Add a failing contract assertion for display precedence.**

Required precedence:
`CONSOLIDATED/REVALIDATING consolidatedErrorPercent` > `LEARNING recentErrorPercent` > raw comparison only when no stability summary exists.

- [ ] **Step 2: Change only comparison-layer value selection in `learning.js`.**

Do not add EMA or arbitrary presentation filtering. Preserve the existing raw comparison for detail/diagnostics, but stop using a single raw comparison as primary cell value when robust stability is available.

- [ ] **Step 3: In `REVALIDATING`, keep consolidated primary and expose recent trend in subtext/detail rather than replacing the main value.**

- [ ] **Step 4: Run focused runtime tests and any JS/Python contract tests available.**

- [ ] **Step 5: Update volatility metrics in `MATRIX.md`; expected pathological sequence max jump must fall materially while the coherent multi-visit shift still promotes.**

---

### Task 3: Carry-forward gasoline scale S with cautious evidence updates

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/AdaptivePetrolReference.kt`
- Modify the smallest existing persisted learning-state seam that already survives sessions; prefer an existing snapshot/session metadata file rather than a new subsystem.
- Test: create `app/src/test/java/com/omegas/prohub/learning/AdaptivePetrolScaleStateTest.kt`

**Interfaces:**
- Consumes: prior accepted scale, real gasoline regions only.
- Produces: accepted scale + candidate scale + support metadata for `AdaptivePetrolReference.estimate`.

- [ ] **Step 1: Characterize current reset behavior in a failing test.**

Construct a prior accepted scale near `1.0656` and an early small gasoline cluster whose instantaneous median ratio is ~3% away. Assert the accepted runtime scale must remain close to the carried prior after that small cluster.

- [ ] **Step 2: Add a convergence test.**

Feed sustained coherent gasoline evidence at a genuinely shifted scale and assert repeated evidence moves the accepted scale gradually toward the candidate.

- [ ] **Step 3: Implement the smallest persisted scale state.**

State must contain at least: `acceptedScale`, `candidateScale`, `realSupport`, `independentVisits`, `updatedAt/provenance`. GNV must have no mutation path.

- [ ] **Step 4: Bound each update by evidence maturity.**

Use a transparent evidence-dependent blend/bounded step, not a hyperparameter search. An early cluster cannot reset S; sustained support can move it.

- [ ] **Step 5: Wire `AdaptivePetrolReference` to use carried accepted S before fitting local residual.**

- [ ] **Step 6: Run focused tests plus existing adaptive-reference/reconciler tests.**

- [ ] **Step 7: Update MATRIX rows for S, F2, residual, prediction/evidence provenance.**

---

### Task 4: Strengthen material calibration-state identity and post-readback causal evidence

**Files:**
- Modify: `app/src/main/java/com/omegas/v7/runtime/V7SessionRuntime.kt`
- Modify: `app/src/main/java/com/omegas/v7/runtime/V7SessionSnapshotCodec.kt`
- Test: create `app/src/test/java/com/omegas/v7/runtime/CalibrationMaterialStateIdentityTest.kt`
- Test: extend `V7SessionRuntimeTest.kt`

**Interfaces:**
- Produces: deterministic material state ID from complete Curve K + Mapa K confirmed values; post-readback transition metadata from state A→B.

- [ ] **Step 1: Add deterministic material-state identity tests.**

Same material Curve+Map values must yield the same ID independent of session label/revision counters; changing one Curve or Map value must change the ID.

- [ ] **Step 2: Add snapshot compatibility test.**

Old accepted schemas continue to decode; new schema persists state identity/transition metadata deterministically.

- [ ] **Step 3: Implement material state ID as a derived property/helper on `CalibrationStateV7`, preserving revision counters for compatibility.**

Hash canonical Curve values + full editable/storage Map values; no session ID in hash.

- [ ] **Step 4: After successful writer readback, persist a transition record containing before-state ID, after-state ID, suggestion ID/target and timestamp.**

No transition is created on failed write or missing readback.

- [ ] **Step 5: Ensure new GNV evidence is attributable to the confirmed after-state, without mixing earlier-state comparisons.**

- [ ] **Step 6: Run focused runtime/snapshot tests.**

---

### Task 5: Causal 0.75 → 0.90 suggestion policy

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt`
- Modify only if needed for metadata propagation: `app/src/main/java/com/omegas/prohub/calibration/AdvisorSuggestionAdapterV7.kt`
- Modify: `app/src/main/java/com/omegas/v7/runtime/V7SessionRuntime.kt`
- Test: create `app/src/test/java/com/omegas/v7/runtime/CausalSuggestionFractionTest.kt`

**Interfaces:**
- Consumes: confirmed calibration transition + pre/post evidence for same physical/global target.
- Produces: correction fraction `0.75` by default, `0.90` only after causal response confirmation.

- [ ] **Step 1: Add RED test: repeated visits without a confirmed intervention never increase 0.75.**

- [ ] **Step 2: Add RED test: successful readback alone is insufficient for 0.90 without post-change evidence.**

- [ ] **Step 3: Add RED test: post-change evidence with expected response direction and plausible gain can mark the same target causally confirmed and allow 0.90.**

- [ ] **Step 4: Add RED test: wrong direction/conflicted revalidation keeps 0.75 or abstains.**

- [ ] **Step 5: Implement a small causal-response summary on existing transition/runtime state; do not create a generic ML learner.**

- [ ] **Step 6: Make advisor fraction selection consume that explicit confirmation.**

Curve global removal remains before Map residual; total error must not be assigned directly to Map.

- [ ] **Step 7: Run advisor/adapter/runtime focused tests.**

---

### Task 6: Cross-cutting scientific regression audit

**Files:**
- Create/update tests only as required; prefer existing suites.
- Update: `docs/research/omegas-verde/scientific-consolidation/MATRIX.md`

**Interfaces:** final requirement→test→evidence mapping.

- [ ] **Step 1: Verify gasoline collection before GNV, after GNV, while suggestions exist, and after readback/state transition.**

- [ ] **Step 2: Verify prediction does not increment real sample/visit/session evidence.**

- [ ] **Step 3: Verify later physical gasoline can supersede adaptive prediction without duplicate GNV vote.**

- [ ] **Step 4: Verify RPM×MAP reference geometry and RPM×Petrol Map K geometry remain intact.**

- [ ] **Step 5: Verify manual-only write semantics and mandatory readback remain intact.**

- [ ] **Step 6: Search final diff for forbidden behavior: automatic write, hardcoded MAP≈0.775 exception, opaque ML, Confidence TRUST authorization, total-error-to-Map shortcut.**

- [ ] **Step 7: Mark every MATRIX row final as `IMPLEMENTED+TESTED`, `ALREADY_IMPLEMENTED+VERIFIED`, or `REJECTED/N/A` with evidence path.**

---

### Task 7: Final verification and APK publication

**Files:**
- No production changes unless a verification defect is found.
- Update issue #50 with final receipts.

**Interfaces:** final remote SHA, CI run, APK artifact, SHA-256.

- [ ] **Step 1: Re-read remote branch HEAD and inspect full diff from baseline `d51b26d...`.**

- [ ] **Step 2: Run/confirm `./gradlew testDebugUnitTest` on final SHA.**

Expected: all tests pass.

- [ ] **Step 3: Run/confirm `./gradlew assembleDebug`.**

Expected: build success.

- [ ] **Step 4: Confirm CI hash step and artifact publication.**

- [ ] **Step 5: Download artifact and independently verify APK SHA-256 matches published `.sha256`.**

- [ ] **Step 6: Update issue #50 with matrix summary, before/after volatility metrics, final SHA, CI run ID, artifact name/hash, limitations, and no-physical-validation statement.**

- [ ] **Step 7: Close issue #50 only if no behavior-critical MATRIX row remains unresolved.**
