# Phase 06 Pragmatic Equivalence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ordinary stability-based evidence rejection with bounded continuous RPM+MAP→petrol-Tinj equivalence learning while preserving V8.2 latency, memory and persistence optimizations.

**Architecture:** Reuse `MotorSampleAnalyzer` for robust centers/diagnostics, convert those diagnostics into a continuous stability weight, combine it with existing `ContinuousWindowNovelty`, and update one bounded primitive-array `EquivalenceSurface` with separate gasoline and CNG petrol-Tinj lanes. Publish only material equivalence revisions to Advisor; keep Predictor downstream and unchanged except for contract tests proving it does not become the primary matcher.

**Tech Stack:** Kotlin/JVM, Android unit tests via Gradle, existing OMEGAS Learning persistence/revision infrastructure, host-side Python only for offline replay/bootstrap/Monte Carlo oracle.

**Spec:** `docs/superpowers/specs/2026-08-21-phase06-pragmatic-equivalence-design.md`

**Approval receipt:** `docs/superpowers/specs/2026-08-21-phase06-pragmatic-equivalence-approval.md`

## Global Constraints

- Primary equivalence is RPM+MAP→gasoline petrol Tinj, compared with petrol Tinj observed on CNG.
- Pressure, coolant temperature, gas temperature, K2/K3/K4, deadtime and A/C state cannot gate or alter the primary equivalence result.
- Hard-zero remains only for physically meaningless comparison states: invalid/implausible telemetry, engine off, cutoff, unresolved fuel transition, severe continuity loss, or invalid RPM/MAP/petrol Tinj.
- CNG weight is `w_stability * w_novelty`; gasoline weight is `w_stability^2 * w_novelty` unless the offline oracle falsifies this before release.
- Surface update is O(1), touches at most four lattice points, uses bounded numeric state, and must not scan historical evidence on the telemetry hot path.
- Primary query inspects a fixed small neighborhood; target bound is no more than roughly 16 compact support points unless the benchmark proves another bound is better.
- JSON stays at status/export/persistence boundaries; no per-frame JSON construction in scientific arithmetic.
- Advisor refresh remains revision-driven and off the telemetry/UI thread; persistence remains material-change-driven, async and coalesced; restore remains deferred.
- No GitHub Actions. Verification is local/ephemeral-first with fresh output before any PASS claim.
- Predictor closure is a separate workstream; this plan only protects the boundary so Predictor cannot become a competing equivalence engine.

---

### Task 1: Continuous evidence weight and analyzer admission

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/learning/EquivalenceEvidenceWeight.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/MotorSampleAnalyzer.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/EquivalenceEvidenceWeightTest.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/MotorSampleAnalyzerTest.kt`

**Interfaces:**
- Consumes: existing `SampleDiagnostics` ratios and `MotorSample` centers.
- Produces: `data class EquivalenceEvidenceWeight(val stability: Double, val limitingSignal: String)` and `EquivalenceEvidenceWeight.from(diagnostics: SampleDiagnostics): EquivalenceEvidenceWeight`.

- [ ] **Step 1: Write the failing weight tests**

```kotlin
@Test fun threshold_is_continuous_not_binary() {
    val below = EquivalenceEvidenceWeight.from(diagnostics(rpmCenterShift = 99.0, rpmCenterLimit = 100.0)).stability
    val above = EquivalenceEvidenceWeight.from(diagnostics(rpmCenterShift = 101.0, rpmCenterLimit = 100.0)).stability
    assertTrue(kotlin.math.abs(below - above) < 0.02)
    assertTrue(below > 0.0 && above > 0.0)
}

@Test fun pressure_does_not_change_primary_weight() {
    val a = EquivalenceEvidenceWeight.from(diagnostics(pressureCenterShift = 0.0)).stability
    val b = EquivalenceEvidenceWeight.from(diagnostics(pressureCenterShift = 99.0)).stability
    assertEquals(a, b, 1e-12)
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*EquivalenceEvidenceWeightTest'`
Expected: FAIL because `EquivalenceEvidenceWeight` does not exist.

- [ ] **Step 3: Implement the minimal pure weighting function**

```kotlin
internal object EquivalenceEvidenceWeight {
    data class Result(val stability: Double, val limitingSignal: String)

    fun from(d: SampleDiagnostics): Result {
        val ratios = listOf(
            "rpm_shift" to ratio(d.rpmCenterShift, d.rpmCenterLimit),
            "rpm_osc" to ratio(d.rpmOscillation, d.rpmOscillationLimit),
            "map_shift" to ratio(d.mapCenterShift, d.mapCenterLimit),
            "map_osc" to ratio(d.mapOscillation, d.mapOscillationLimit),
            "tinj_shift" to ratio(d.petrolCenterShift, d.petrolCenterLimit),
            "tinj_osc" to ratio(d.petrolOscillationRatio, d.petrolOscillationLimit),
        )
        val limiting = ratios.maxByOrNull { it.second } ?: ("none" to 0.0)
        val r = limiting.second.coerceAtLeast(0.0)
        return Result(1.0 / (1.0 + r * r), limiting.first)
    }

    private fun ratio(value: Double, limit: Double): Double =
        if (!value.isFinite() || !limit.isFinite() || limit <= 0.0) 0.0 else kotlin.math.abs(value) / limit
}
```

- [ ] **Step 4: Change `MotorSampleAnalyzer.evaluate()` so ordinary RPM/MAP/Tinj instability and water/pressure checks no longer null the sample**

Return a `MotorSample` for every physically valid same-fuel window. Keep invalid telemetry, cutoff, unresolved fuel transition and severe continuity loss as hard-zero. Preserve diagnostics and reason text; set `learningEligible=true` for valid weighted windows and expose the weight result through the sample/decision contract rather than `SAMPLE_REJECTED`.

- [ ] **Step 5: Add analyzer contract tests**

```kotlin
@Test fun cng_pressure_instability_still_produces_learning_sample() { /* fixture with pressure beyond legacy limit; assert sample != null and learningEligible */ }
@Test fun water_below_legacy_minimum_does_not_gate_primary_equivalence() { /* assert sample != null when RPM/MAP/Tinj are valid */ }
@Test fun cutoff_remains_hard_zero() { /* assert learningEligible == false */ }
```

- [ ] **Step 6: Run focused analyzer + weight tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*EquivalenceEvidenceWeightTest' --tests '*MotorSampleAnalyzerTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/omegas/prohub/learning/EquivalenceEvidenceWeight.kt app/src/main/java/com/omegas/prohub/learning/MotorSampleAnalyzer.kt app/src/test/java/com/omegas/prohub/learning/EquivalenceEvidenceWeightTest.kt app/src/test/java/com/omegas/prohub/learning/MotorSampleAnalyzerTest.kt
git commit -m "feat(learning): weight valid evidence continuously"
```

---

### Task 2: Bounded RPM×MAP equivalence surface

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/learning/EquivalenceSurface.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/EquivalenceSurfaceTest.kt`

**Interfaces:**
- Consumes: `(fuel, rpm, mapBar, petrolTinjMs, weight, materialRevision)`.
- Produces: `EquivalenceSurface.observe(...)`, `EquivalenceSurface.query(rpm,mapBar): QueryResult`, `FuelLaneMoment`, and fixed-size primitive-array storage.

- [ ] **Step 1: Write RED tests for bilinear mass conservation, two independent fuel lanes, weighted mean/variance/ESS, and fixed-state size**

```kotlin
@Test fun bilinear_distribution_conserves_weight() {
    val s = EquivalenceSurface.testSurface()
    s.observe(FuelLane.PETROL_REFERENCE, 2480.0, 0.51, 3.0, 0.8, 1L)
    assertEquals(0.8, s.debugTotalWeight(FuelLane.PETROL_REFERENCE), 1e-12)
}

@Test fun cng_can_exist_before_petrol_reference() {
    val s = EquivalenceSurface.testSurface()
    s.observe(FuelLane.CNG_PETROL_OBSERVED, 2500.0, 0.50, 3.3, 0.4, 1L)
    assertTrue(s.query(2500.0, 0.50).cng != null)
    assertNull(s.query(2500.0, 0.50).petrol)
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*EquivalenceSurfaceTest'`
Expected: FAIL because `EquivalenceSurface` does not exist.

- [ ] **Step 3: Implement primitive-array moments and bilinear update touching at most four points**

Use one indexer and per-lane `DoubleArray` fields for `sumW`, `sumW2`, `sumWTinj`, `sumWTinj2`, plus `LongArray` material revision metadata. Reuse `ContinuousLearningMath.bilinearWeights` where its axis contract matches; otherwise implement the same four-corner conservation locally without allocations.

- [ ] **Step 4: Implement fixed-neighborhood query without global sort/KNN**

Query at most the local 4×4 lattice neighborhood around `(rpm,map)`, combine supported nodes with distance weights, and return lane estimate, spread, ESS, nearest support distance and material revision. Do not allocate a historical candidate list.

- [ ] **Step 5: Run surface tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*EquivalenceSurfaceTest' --tests '*ContinuousLearningMathTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/omegas/prohub/learning/EquivalenceSurface.kt app/src/test/java/com/omegas/prohub/learning/EquivalenceSurfaceTest.kt
git commit -m "feat(learning): add bounded rpm map equivalence surface"
```

---

### Task 3: Compose stability with existing novelty in `SignalLearningStore`

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/ContinuousWindowNovelty.kt` only if a zero-allocation accessor is required; do not change its semantics.
- Test: `app/src/test/java/com/omegas/prohub/learning/SignalLearningStoreEquivalenceTest.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/ContinuousWindowNoveltyTest.kt`

**Interfaces:**
- Consumes: analyzer sample + `EquivalenceEvidenceWeight.Result` + `ContinuousWindowNovelty.Result`.
- Produces: final per-observation scientific weight and exactly one surface update per eligible window.

- [ ] **Step 1: Write RED integration tests**

```kotlin
@Test fun cng_weight_is_stability_times_novelty() { /* inject 0.5 stability and 0.4 novelty; assert surface receives 0.2 */ }
@Test fun petrol_weight_squares_stability_before_novelty() { /* 0.5 * 0.5 * 0.4 = 0.1 */ }
@Test fun duplicate_window_has_zero_new_scientific_weight() { /* novelty duplicate; surface weight unchanged */ }
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*SignalLearningStoreEquivalenceTest' --tests '*ContinuousWindowNoveltyTest'`
Expected: FAIL on missing surface integration.

- [ ] **Step 3: Integrate one `EquivalenceSurface` instance into `SignalLearningStore`**

Preserve existing novelty counters, evidence budgets, material persistence gate and advisor executor. Do not serialize/deserialize JSON inside `ingest()` merely to update the surface.

- [ ] **Step 4: Map fuel lanes without environmental gates**

`PETROL -> PETROL_REFERENCE`; `CNG -> CNG_PETROL_OBSERVED`. Pressure/temp/K/deadtime must not participate in lane admission or final weight.

- [ ] **Step 5: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*SignalLearningStoreEquivalenceTest' --tests '*ContinuousWindowNoveltyTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt app/src/main/java/com/omegas/prohub/learning/ContinuousWindowNovelty.kt app/src/test/java/com/omegas/prohub/learning/SignalLearningStoreEquivalenceTest.kt app/src/test/java/com/omegas/prohub/learning/ContinuousWindowNoveltyTest.kt
git commit -m "feat(learning): absorb weighted petrol and cng evidence"
```

---

### Task 4: Analytic uncertainty and primary equivalence snapshot

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/learning/EquivalenceUncertainty.kt`
- Create: `app/src/main/java/com/omegas/prohub/learning/EquivalenceEstimate.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/EquivalenceUncertaintyTest.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/EquivalenceEstimateTest.kt`

**Interfaces:**
- Produces: `EquivalenceEstimate(referenceMs, cngMs, deltaMs, errorFraction, uncertaintyFraction, usefulMarginFraction, actionable, revision)`.
- `EquivalenceUncertainty.combine(ref,cng,match)` returns root-sum-square relative uncertainty.

- [ ] **Step 1: Write RED arithmetic tests**

```kotlin
@Test fun error_is_cng_minus_reference_over_reference() {
    val e = EquivalenceEstimate.create(3.0, 3.3, 0.01, 0.01, 0.0, 0.02, 1L)
    assertEquals(0.10, e.errorFraction, 1e-12)
    assertEquals(0.30, e.deltaMs, 1e-12)
}

@Test fun useful_margin_subtracts_uncertainty_and_deadband() {
    val e = EquivalenceEstimate.create(3.0, 3.3, 0.014, 0.0, 0.0, 0.02, 1L)
    assertTrue(e.usefulMarginFraction > 0.0)
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*EquivalenceUncertaintyTest' --tests '*EquivalenceEstimateTest'`
Expected: FAIL because the types do not exist.

- [ ] **Step 3: Implement analytic uncertainty with no invented sensor floor**

Compute relative spread/ESS contributions from lane query results, add only empirically justified spatial interpolation term, and combine via `sqrt(uRef*uRef + uCng*uCng + uMatch*uMatch)`. Keep the deadband as an injected policy value; do not hard-code it as statistical truth.

- [ ] **Step 4: Publish comparison only when both lanes have support**

If only one lane exists, publish an explicit support state without manufacturing a target. When both exist, publish `deltaMs`, `errorFraction`, uncertainty and useful margin.

- [ ] **Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*EquivalenceUncertaintyTest' --tests '*EquivalenceEstimateTest' --tests '*SignalLearningStoreEquivalenceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/omegas/prohub/learning/EquivalenceUncertainty.kt app/src/main/java/com/omegas/prohub/learning/EquivalenceEstimate.kt app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt app/src/test/java/com/omegas/prohub/learning/EquivalenceUncertaintyTest.kt app/src/test/java/com/omegas/prohub/learning/EquivalenceEstimateTest.kt
git commit -m "feat(learning): publish tinj equivalence with uncertainty"
```

---

### Task 5: Advisor/revision integration without hot-path recomputation

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/AdvisorRevisionGate.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/AdvisorRevisionGateTest.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/AssistedCalibrationAdvisorTest.kt`

**Interfaces:**
- Consumes: published `EquivalenceEstimate` snapshots, not raw history.
- Produces: revision token based on material equivalence state; Advisor suggestions continue downstream to Map/Curve projection.

- [ ] **Step 1: Add RED tests proving tiny repeated updates do not wake Advisor while material error/uncertainty changes do**

Use quantized semantic token components: local support key, Tinj estimate band, uncertainty band, useful-margin sign/band and ESS milestone.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*AdvisorRevisionGateTest' --tests '*AssistedCalibrationAdvisorTest'`
Expected: FAIL on new token contract.

- [ ] **Step 3: Change Advisor input to consume equivalence snapshots without rescanning raw historical regions**

Preserve its existing separation of target estimation from step policy and keep Map/Curve projection after the error is formed.

- [ ] **Step 4: Keep Advisor executor, duplicate-payload exit and material persistence gate unchanged in responsibility**

Do not move advisor computation, snapshot building or file writes into telemetry/UI thread.

- [ ] **Step 5: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*AdvisorRevisionGateTest' --tests '*AssistedCalibrationAdvisorTest' --tests '*SignalLearningStoreEquivalenceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/omegas/prohub/learning/AdvisorRevisionGate.kt app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt app/src/test/java/com/omegas/prohub/learning/AdvisorRevisionGateTest.kt app/src/test/java/com/omegas/prohub/learning/AssistedCalibrationAdvisorTest.kt
git commit -m "feat(learning): drive advisor from material equivalence revisions"
```

---

### Task 6: Versioned persistence and conservative gasoline migration

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/LiveOnlyLearningStore.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/LearningTelemetrySchemaMigration.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/LearningTelemetrySchemaMigrationTest.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/CalibrationBoundLearningEvidenceTest.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/LiveOnlyLearningStoreTest.kt`

**Interfaces:**
- Produces: new versioned equivalence-surface persistence payload plus one-time `LEGACY_ACCEPTED_PETROL_REGION` seeding path.

- [ ] **Step 1: Write RED migration tests**

```kotlin
@Test fun accepted_legacy_petrol_regions_seed_reference_lane_once() { /* restore twice; assert no double-count */ }
@Test fun stale_cng_epoch_is_not_seeded() { /* old calibration binding; assert CNG lane empty */ }
@Test fun migration_never_fabricates_raw_samples() { /* exported provenance says LEGACY_ACCEPTED_PETROL_REGION and no synthetic frame IDs */ }
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*LearningTelemetrySchemaMigrationTest' --tests '*CalibrationBoundLearningEvidenceTest' --tests '*LiveOnlyLearningStoreTest'`
Expected: FAIL on missing new revision/migration behavior.

- [ ] **Step 3: Add versioned surface persistence through existing coalesced writer path**

Serialize only bounded arrays/compact metadata at persistence boundary. Preserve `MaterialPersistenceGate` and `CoalescedSnapshotWriter` behavior.

- [ ] **Step 4: Implement conservative monotone legacy gasoline seed mapping behind one named policy function**

The function consumes only persisted support/quality already present in legacy regions, caps seed authority, tags provenance explicitly, and is easy for Task 7 oracle to replace if falsified. Do not resurrect old CNG comparisons.

- [ ] **Step 5: Run migration tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*LearningTelemetrySchemaMigrationTest' --tests '*CalibrationBoundLearningEvidenceTest' --tests '*LiveOnlyLearningStoreTest' --tests '*CoalescedSnapshotWriterTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt app/src/main/java/com/omegas/prohub/learning/LiveOnlyLearningStore.kt app/src/main/java/com/omegas/prohub/learning/LearningTelemetrySchemaMigration.kt app/src/test/java/com/omegas/prohub/learning/LearningTelemetrySchemaMigrationTest.kt app/src/test/java/com/omegas/prohub/learning/CalibrationBoundLearningEvidenceTest.kt app/src/test/java/com/omegas/prohub/learning/LiveOnlyLearningStoreTest.kt
git commit -m "feat(learning): persist and migrate bounded equivalence surface"
```

---

### Task 7: Real-log replay, bootstrap and Monte Carlo oracle

**Files:**
- Create: `tools/phase06/equivalence_oracle.py`
- Create: `tools/phase06/equivalence_corpus_manifest.json`
- Create: `tools/phase06/README.md`
- Test: `tools/phase06/test_equivalence_oracle.py`

**Interfaces:**
- Consumes: only the explicitly inventoried OMEGAS semantic session logs; Portmon remains protocol evidence unless decoded.
- Produces: reproducible JSON report with hard-gate baseline vs continuous weighting, session/trajectory holdouts, bootstrap intervals and noise-stress Monte Carlo.

- [ ] **Step 1: Write RED host-side tests for deterministic manifest loading, session-level holdout separation, bootstrap seed reproducibility and metric schema**

```python
def test_holdout_never_trains_on_same_session():
    train, test = split_by_session(fixtures())
    assert {x.session_id for x in train}.isdisjoint({x.session_id for x in test})
```

- [ ] **Step 2: Run and verify RED**

Run: `python -m pytest tools/phase06/test_equivalence_oracle.py -q`
Expected: FAIL because oracle functions do not exist.

- [ ] **Step 3: Implement streaming log parser and candidate models**

Compute current hard-gate baseline, continuous CNG weighting, gasoline-squared weighting and bounded local-neighborhood alternatives. Never load all raw JSONL rows into an unbounded in-memory object graph.

- [ ] **Step 4: Emit required metrics**

Report median/P90/P95 gasoline-reference error, supported coverage, time-to-first-useful-reference, time-to-first-actionable CNG residual, false-action rate inside deadband, interval coverage, ESS inflation under repeated frames and runtime-state memory estimate.

- [ ] **Step 5: Run session-level bootstrap and empirically bounded Monte Carlo**

Bootstrap resamples real sessions/trajectories. Monte Carlo perturbs RPM/MAP/Tinj only from noise distributions estimated from the corpus; it cannot replace held-out real-session metrics.

- [ ] **Step 6: Freeze or revise engineering constants only from the oracle report**

Promote lattice spacing, local-query radius/bound, deadband policy and legacy-seed cap only if held-out/error/coverage results support them. If any approved architectural bullet would need to change, stop this dependent branch for owner authorization rather than silently changing the design.

- [ ] **Step 7: Commit**

```bash
git add tools/phase06/equivalence_oracle.py tools/phase06/equivalence_corpus_manifest.json tools/phase06/README.md tools/phase06/test_equivalence_oracle.py
git commit -m "test(learning): add real-log equivalence oracle"
```

---

### Task 8: Performance regression gate and Predictor boundary contract

**Files:**
- Create: `app/src/test/java/com/omegas/prohub/learning/EquivalencePerformanceContractTest.kt`
- Modify: `app/src/test/java/com/omegas/prohub/learning/PredictorSurfaceTest.kt`
- Modify: `app/src/test/java/com/omegas/prohub/learning/PredictorSpatialConfidenceTest.kt`
- Modify: `app/src/test/java/com/omegas/prohub/learning/SignalLearningStoreEquivalenceTest.kt`

**Interfaces:**
- Produces: executable proof that hot-path cost is bounded and Predictor stays downstream.

- [ ] **Step 1: Add RED performance-contract tests**

Assert that one `observe()` touches no more than four lattice points, query inspects no more than the fixed local bound, state size is independent of sample count, and repeated identical windows do not create growing provenance or advisor refreshes.

- [ ] **Step 2: Add Predictor boundary tests**

```kotlin
@Test fun predictor_does_not_require_environment_for_primary_equivalence() { /* feed published equivalence with only RPM/MAP/Tinj science; assert projection remains possible when its own geometry supports it */ }
@Test fun predictor_confidence_never_upgrades_upstream_uncertainty() { /* projection confidence <= upstream support envelope */ }
@Test fun predictor_revision_token_is_stable_for_duplicate_upstream_payload() { /* same revision + same map hash => same token */ }
```

- [ ] **Step 3: Run focused contracts**

Run: `./gradlew :app:testDebugUnitTest --tests '*EquivalencePerformanceContractTest' --tests '*PredictorSurfaceTest' --tests '*PredictorSpatialConfidenceTest' --tests '*SignalLearningStoreEquivalenceTest'`
Expected: PASS after the bounded implementation is complete.

- [ ] **Step 4: Run fresh full unit suite locally/ephemerally**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. Record wall-clock, failures=0, and exact source SHA. Do not use GitHub Actions.

- [ ] **Step 5: Run the offline oracle on the frozen corpus manifest**

Run: `python tools/phase06/equivalence_oracle.py --manifest tools/phase06/equivalence_corpus_manifest.json --output build/phase06-equivalence-oracle.json`
Expected: report contains all required metrics and no training/test session overlap.

- [ ] **Step 6: Compare performance with the optimized V8.2 baseline**

Use the same local benchmark harness/source fixture for baseline and candidate. Treat repeated >10% p95/p99/allocation regression as an alarm; any unbounded memory slope, historical hot-path scan or UI-thread blocking is an unconditional FAIL.

- [ ] **Step 7: Commit**

```bash
git add app/src/test/java/com/omegas/prohub/learning/EquivalencePerformanceContractTest.kt app/src/test/java/com/omegas/prohub/learning/PredictorSurfaceTest.kt app/src/test/java/com/omegas/prohub/learning/PredictorSpatialConfidenceTest.kt app/src/test/java/com/omegas/prohub/learning/SignalLearningStoreEquivalenceTest.kt
git commit -m "test(learning): gate equivalence performance and predictor boundary"
```

---

### Task 9: Governance reconciliation and fresh closure receipts

**Files:**
- Modify: `docs/superpowers/specs/2026-08-21-phase06-pragmatic-equivalence-design.md` status from draft to approved/implemented only after exact-source verification.
- Create: `docs/superpowers/receipts/2026-08-21-phase06-pragmatic-equivalence-verification.md`
- Update: Notion Phase 06 owner decision and Linear VIT-85/relevant children only after fresh source/test receipts exist.

**Interfaces:**
- Produces: exact-SHA verification receipt, normative audit input, meta-audit input and reconciled governance state.

- [ ] **Step 1: Record exact-source verification receipt**

Include candidate SHA, focused test commands/results, full unit suite result, oracle report hash/metrics, performance comparison, migration proof and any hardware-only claims left unverified.

- [ ] **Step 2: Run independent normative audit against the approved spec**

The audit must prove only RPM/MAP/Tinj are required for primary equivalence; valid evidence is weighted rather than ordinary stability-rejected; no environmental/K/deadtime blocker remains; no arbitrary sample-count blocker exists; correlated evidence is not overcounted; bounded/incremental hot-path behavior holds; Map/Curve projection remains downstream.

- [ ] **Step 3: Run distinct meta-audit with a separate run ID**

Verify the normative audit used the correct SHA, complete acceptance criteria and fresh receipts rather than self-asserted completion.

- [ ] **Step 4: Reconcile Notion and Linear**

Replace obsolete Phase 06 operational wording only now that the owner-approved design has executable proof. Do not mutate the separate `OMEGAS_GNV_EQ` campaign.

- [ ] **Step 5: Commit governance receipts**

```bash
git add docs/superpowers/specs/2026-08-21-phase06-pragmatic-equivalence-design.md docs/superpowers/receipts/2026-08-21-phase06-pragmatic-equivalence-verification.md
git commit -m "docs: close verified Phase 06 pragmatic equivalence contract"
```

## Self-review

- Spec coverage: Tasks 1–9 cover continuous admission, novelty, bounded dual-lane surface, uncertainty/actionability, Advisor revisions, migration, real-log oracle, performance/Predictor boundary and governance closure.
- Placeholder scan: no `TBD`, `TODO`, generic “add tests” or unnamed interface remains in the implementation path.
- Type consistency: `EquivalenceEvidenceWeight`, `EquivalenceSurface`, `FuelLane`, `EquivalenceEstimate` and `EquivalenceUncertainty` are introduced before downstream tasks consume them.
