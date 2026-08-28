# Phase 06 Typed Scientific Authority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Physics/K* accept only typed `OEM_NATIVE | CLASSIC_ASSISTED | ADAPTIVE_SHADOW` scientific evidence, preserve provenance, reject Prediction-as-Observation, and remove the public raw-double K* bypass without changing the K* equation.

**Architecture:** Reuse the existing `ScientificAuthority` vocabulary and move it into a focused `ScientificEvidence.kt` contract with an orthogonal `ScientificEvidenceRole`. Resolve duplicate physical observations conservatively, then make `KStarEstimator` accept only `KStarScientificInput`, preserve a compact trace, and migrate the Fast Physics consumer to the typed entry. No second runtime, Store, polling loop, scheduler, serial authority, Draft, Writer, persistence path, or Calibration Identity system is introduced.

**Tech Stack:** Kotlin, JUnit4, Android Gradle unit tests, GitHub remote source authority, read-only ephemeral runtime for test execution.

**Spec:** `docs/superpowers/specs/2026-08-23-phase06-typed-scientific-authority-design.md`

## Global Constraints

- Reuse `ScientificAuthority`; do not create a second producer taxonomy.
- `ScientificEvidenceRole` is exactly `OBSERVATION | PREDICTION` and is never silently defaulted.
- One physical observation counts once even when several producers expose it.
- Producer disagreement is explicit; no priority, max, min, average, or vote may silently resolve disagreement.
- Prediction is never eligible as a physical K* observation.
- Producer identity must not alter K* mathematics or inflate `MagnitudeAuthority`.
- Do not duplicate Calibration Identity/fingerprint validity logic inside K*.
- Remove the public raw-double `KStarEstimator.estimate(...)` production bypass.
- Do not change the log-domain K* equation, plant-gain model, StepPolicy, actuator allocation, writer safety, MP48 acquisition, persistence, or UI/Draft contracts.
- No new polling, scheduler, thread, Store, queue, serial path, Draft, Writer, JSON hot-path work, or historical scan.
- Source writes occur only through GitHub remote APIs. Test runtime is read-only and must execute the exact remote SHA.
- GitHub Actions remain denied by default; use the ephemeral runtime first.

---

### Task 1: Canonical scientific-evidence contract and conservative resolution

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/physics/ScientificEvidence.kt`
- Modify: `app/src/main/java/com/omegas/prohub/physics/Phase6OwnerBindings.kt`
- Create: `app/src/test/java/com/omegas/prohub/physics/ScientificEvidenceContractTest.kt`
- Modify: `app/src/test/java/com/omegas/prohub/physics/Phase6BindingIntegrationTest.kt`

**Interfaces:**
- Consumes: existing `ScientificAuthority` and `PhysicsScientificInput` semantics from `Phase6OwnerBindings.kt`.
- Produces: `ScientificAuthority`, `ScientificEvidenceRole`, `PhysicsScientificInput`, `ResolvedScientificEvidence`, `ScientificEvidenceConflict`, `ScientificEvidenceResolution`, `ScientificMeasurement`, and `KStarScientificInput` in `ScientificEvidence.kt`.

- [ ] **Step 1: Write the RED tests for duplicate resolution and Prediction lineage**

Create `ScientificEvidenceContractTest.kt` with these behaviors:

```kotlin
package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificEvidenceContractTest {
    @Test fun `same physical observation counts once and preserves every producer`() {
        val resolution = PhysicsScientificInput.resolve(
            listOf(
                PhysicsScientificInput(
                    ScientificAuthority.OEM_NATIVE,
                    ScientificEvidenceRole.OBSERVATION,
                    "oem-frame-42",
                    "frame-42",
                    0.8,
                    "mp48-native",
                ),
                PhysicsScientificInput(
                    ScientificAuthority.CLASSIC_ASSISTED,
                    ScientificEvidenceRole.OBSERVATION,
                    "classic-frame-42",
                    "frame-42",
                    0.8,
                    "classic-forward",
                ),
            ),
        )
        assertTrue(resolution.conflicts.isEmpty())
        assertEquals(1, resolution.accepted.size)
        val evidence = resolution.accepted.single()
        assertEquals(setOf(ScientificAuthority.OEM_NATIVE, ScientificAuthority.CLASSIC_ASSISTED), evidence.authorities)
        assertEquals(setOf("oem-frame-42", "classic-frame-42"), evidence.evidenceIds)
        assertEquals(0.8, evidence.effectiveWeight, 1e-12)
    }

    @Test fun `conflicting physical weights stay explicit`() {
        val resolution = PhysicsScientificInput.resolve(
            listOf(
                PhysicsScientificInput(ScientificAuthority.OEM_NATIVE, ScientificEvidenceRole.OBSERVATION, "a", "frame-42", 1.0, "oem"),
                PhysicsScientificInput(ScientificAuthority.CLASSIC_ASSISTED, ScientificEvidenceRole.OBSERVATION, "b", "frame-42", 0.7, "classic"),
            ),
        )
        assertTrue(resolution.accepted.isEmpty())
        assertEquals("SCIENTIFIC_WEIGHT_CONFLICT", resolution.conflicts.single().reason)
    }

    @Test fun `observation prediction collision stays explicit`() {
        val resolution = PhysicsScientificInput.resolve(
            listOf(
                PhysicsScientificInput(ScientificAuthority.OEM_NATIVE, ScientificEvidenceRole.OBSERVATION, "a", "frame-42", 1.0, "oem"),
                PhysicsScientificInput(ScientificAuthority.ADAPTIVE_SHADOW, ScientificEvidenceRole.PREDICTION, "pred-7", "frame-42", 1.0, "adaptive-model"),
            ),
        )
        assertTrue(resolution.accepted.isEmpty())
        assertEquals("SCIENTIFIC_ROLE_CONFLICT", resolution.conflicts.single().reason)
    }

    @Test fun `prediction does not invent singular physical lineage`() {
        val prediction = PhysicsScientificInput(
            ScientificAuthority.ADAPTIVE_SHADOW,
            ScientificEvidenceRole.PREDICTION,
            "pred-aggregate-7",
            null,
            0.6,
            "adaptive-model",
        )
        assertNull(prediction.physicalEvidenceId)
        val resolution = PhysicsScientificInput.resolve(listOf(prediction))
        assertEquals(1, resolution.accepted.size)
        assertEquals(ScientificEvidenceRole.PREDICTION, resolution.accepted.single().role)
    }
}
```

Replace the old priority/deduplication assertion in `Phase6BindingIntegrationTest.kt` with a typed-resolution assertion using equal weights and expecting both producer authorities to remain visible.

- [ ] **Step 2: Run the focused RED test and confirm failure is for the missing contract**

Use a fresh read-only ephemeral checkout of the remote branch and run:

```bash
rm -rf /tmp/vit143-red-1
git clone --depth 1 --branch feature/phase06-pragmatic-equivalence-20260821 https://github.com/viluadmcontas2-dot/OMEGAS-V8.2.git /tmp/vit143-red-1
cd /tmp/vit143-red-1
git rev-parse HEAD
./gradlew :app:testDebugUnitTest --tests "com.omegas.prohub.physics.ScientificEvidenceContractTest" --no-daemon
```

Expected: FAIL at test compilation because `ScientificEvidenceRole`, the new `PhysicsScientificInput` constructor, and `resolve(...)` do not exist yet. A Gradle/environment failure is not an acceptable RED.

- [ ] **Step 3: Implement the minimal scientific-evidence contract**

Create `ScientificEvidence.kt` with this public surface and deterministic resolution rules:

```kotlin
package com.omegas.prohub.physics

enum class ScientificAuthority {
    OEM_NATIVE,
    CLASSIC_ASSISTED,
    ADAPTIVE_SHADOW,
}

enum class ScientificEvidenceRole {
    OBSERVATION,
    PREDICTION,
}

data class PhysicsScientificInput(
    val authority: ScientificAuthority,
    val role: ScientificEvidenceRole,
    val evidenceId: String,
    val physicalEvidenceId: String?,
    val weight: Double,
    val provenance: String,
) {
    init {
        require(evidenceId.isNotBlank())
        require(provenance.isNotBlank())
        require(weight.isFinite() && weight in 0.0..1.0)
        if (role == ScientificEvidenceRole.OBSERVATION) {
            require(!physicalEvidenceId.isNullOrBlank())
        }
    }

    companion object {
        fun resolve(inputs: List<PhysicsScientificInput>): ScientificEvidenceResolution {
            val accepted = mutableListOf<ResolvedScientificEvidence>()
            val conflicts = mutableListOf<ScientificEvidenceConflict>()
            val byPhysical = inputs.filter { it.physicalEvidenceId != null }.groupBy { it.physicalEvidenceId!! }
            val consumed = mutableSetOf<PhysicsScientificInput>()

            byPhysical.toSortedMap().values.forEach { group ->
                val roles = group.map { it.role }.toSet()
                if (roles.size > 1) {
                    conflicts += group.conflict("SCIENTIFIC_ROLE_CONFLICT")
                    consumed += group
                    return@forEach
                }
                if (roles.single() == ScientificEvidenceRole.OBSERVATION) {
                    val weights = group.map { it.weight }.toSet()
                    if (weights.size > 1) {
                        conflicts += group.conflict("SCIENTIFIC_WEIGHT_CONFLICT")
                    } else {
                        accepted += group.resolved(weights.single())
                    }
                    consumed += group
                }
            }

            inputs.filterNot { it in consumed }.forEach { input ->
                accepted += ResolvedScientificEvidence(
                    authorities = setOf(input.authority),
                    role = input.role,
                    evidenceIds = setOf(input.evidenceId),
                    physicalEvidenceId = input.physicalEvidenceId,
                    effectiveWeight = input.weight,
                    provenance = setOf(input.provenance),
                )
            }
            return ScientificEvidenceResolution(accepted, conflicts)
        }
    }
}

data class ResolvedScientificEvidence(
    val authorities: Set<ScientificAuthority>,
    val role: ScientificEvidenceRole,
    val evidenceIds: Set<String>,
    val physicalEvidenceId: String?,
    val effectiveWeight: Double,
    val provenance: Set<String>,
)

data class ScientificEvidenceConflict(
    val evidenceIds: Set<String>,
    val physicalEvidenceId: String?,
    val authorities: Set<ScientificAuthority>,
    val reason: String,
)

data class ScientificEvidenceResolution(
    val accepted: List<ResolvedScientificEvidence>,
    val conflicts: List<ScientificEvidenceConflict>,
)

data class ScientificMeasurement(
    val valueMs: Double,
    val evidence: ResolvedScientificEvidence,
) {
    init { require(valueMs.isFinite() && valueMs > 0.0) }
}

data class KStarScientificInput(
    val petrolOnGas: ScientificMeasurement,
    val petrolReference: ScientificMeasurement,
    val currentFactor: Double,
    val gain: PlantGain,
) {
    init { require(currentFactor.isFinite() && currentFactor > 0.0) }
}

private fun List<PhysicsScientificInput>.resolved(weight: Double): ResolvedScientificEvidence =
    ResolvedScientificEvidence(
        authorities = map { it.authority }.toSet(),
        role = ScientificEvidenceRole.OBSERVATION,
        evidenceIds = map { it.evidenceId }.toSet(),
        physicalEvidenceId = first().physicalEvidenceId,
        effectiveWeight = weight,
        provenance = map { it.provenance }.toSet(),
    )

private fun List<PhysicsScientificInput>.conflict(reason: String): ScientificEvidenceConflict =
    ScientificEvidenceConflict(
        evidenceIds = map { it.evidenceId }.toSet(),
        physicalEvidenceId = first().physicalEvidenceId,
        authorities = map { it.authority }.toSet(),
        reason = reason,
    )
```

Remove the old `ScientificAuthority` and old `PhysicsScientificInput` definitions from `Phase6OwnerBindings.kt`; do not leave aliases or a defaulting compatibility constructor.

- [ ] **Step 4: Run the focused GREEN tests**

```bash
rm -rf /tmp/vit143-green-1
git clone --depth 1 --branch feature/phase06-pragmatic-equivalence-20260821 https://github.com/viluadmcontas2-dot/OMEGAS-V8.2.git /tmp/vit143-green-1
cd /tmp/vit143-green-1
git rev-parse HEAD
./gradlew :app:testDebugUnitTest --tests "com.omegas.prohub.physics.ScientificEvidenceContractTest" --tests "com.omegas.prohub.physics.Phase6BindingIntegrationTest" --no-daemon
```

Expected: BUILD SUCCESSFUL and both test classes green.

- [ ] **Step 5: Inspect the remote diff for scope creep**

Confirm only the four Task 1 paths changed and no runtime/store/writer surface was added.

---

### Task 2: Typed-only K* public entry and provenance trace

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/physics/CalibrationPhysicsFoundation.kt`
- Modify: `app/src/main/java/com/omegas/prohub/physics/FastPhysicsGateEvaluator.kt`
- Modify: `app/src/test/java/com/omegas/prohub/physics/CalibrationPhysicsFoundationTest.kt`
- Test: `app/src/test/java/com/omegas/prohub/physics/FastPhysicsGateEvaluatorTest.kt`

**Interfaces:**
- Consumes: `KStarScientificInput`, `ScientificMeasurement`, `ResolvedScientificEvidence` from Task 1 and existing `PlantGain`.
- Produces: typed-only `KStarEstimator.estimate(input: KStarScientificInput)` and `KStarScientificTrace` on every `KStarEstimate`.

- [ ] **Step 1: Write RED tests for typed K*, producer neutrality, abstention, trace, and public API shape**

Update `CalibrationPhysicsFoundationTest.kt` with a private test helper that creates resolved evidence explicitly, then replace the old raw-double K* calls and add these assertions:

```kotlin
private fun evidence(
    authority: ScientificAuthority,
    role: ScientificEvidenceRole,
    evidenceId: String,
    physicalId: String?,
    weight: Double = 1.0,
): ResolvedScientificEvidence = ResolvedScientificEvidence(
    authorities = setOf(authority),
    role = role,
    evidenceIds = setOf(evidenceId),
    physicalEvidenceId = physicalId,
    effectiveWeight = weight,
    provenance = setOf("test-provenance"),
)

private fun kStarInput(
    authority: ScientificAuthority = ScientificAuthority.CLASSIC_ASSISTED,
    role: ScientificEvidenceRole = ScientificEvidenceRole.OBSERVATION,
    petrolOnGasPhysicalId: String? = "cng-frame",
    petrolReferencePhysicalId: String? = "gas-frame",
    weight: Double = 1.0,
    gain: PlantGain = PlantGain.empiricallyBounded(1.0, 0.8, 1.2),
): KStarScientificInput = KStarScientificInput(
    petrolOnGas = ScientificMeasurement(4.4, evidence(authority, role, "cng-evidence", petrolOnGasPhysicalId, weight)),
    petrolReference = ScientificMeasurement(4.0, evidence(authority, role, "gas-evidence", petrolReferencePhysicalId, weight)),
    currentFactor = 1.0,
    gain = gain,
)

@Test fun `typed kstar reproduces current numeric target`() {
    val estimate = KStarEstimator.estimate(kStarInput())
    assertEquals(1.1, estimate.targetFactor!!, 1e-12)
    assertFalse(estimate.abstained)
}

@Test fun `producer label never changes kstar mathematics`() {
    val targets = ScientificAuthority.values().map { authority ->
        KStarEstimator.estimate(kStarInput(authority = authority)).targetFactor
    }
    assertEquals(1, targets.toSet().size)
}

@Test fun `adaptive prediction cannot masquerade as observation`() {
    val estimate = KStarEstimator.estimate(
        kStarInput(
            authority = ScientificAuthority.ADAPTIVE_SHADOW,
            role = ScientificEvidenceRole.PREDICTION,
            petrolOnGasPhysicalId = null,
            petrolReferencePhysicalId = null,
        ),
    )
    assertTrue(estimate.abstained)
    assertNull(estimate.targetFactor)
    assertEquals(MagnitudeAuthority.UNKNOWN, estimate.authority)
    assertEquals("PREDICTION_IS_NOT_OBSERVATION", estimate.reason)
}

@Test fun `self comparison cannot create a target`() {
    val estimate = KStarEstimator.estimate(
        kStarInput(petrolOnGasPhysicalId = "same-frame", petrolReferencePhysicalId = "same-frame"),
    )
    assertTrue(estimate.abstained)
    assertEquals("SELF_COMPARISON_EVIDENCE", estimate.reason)
}

@Test fun `zero scientific weight cannot create a target`() {
    val estimate = KStarEstimator.estimate(kStarInput(weight = 0.0))
    assertTrue(estimate.abstained)
    assertEquals("NO_SCIENTIFIC_WEIGHT", estimate.reason)
}

@Test fun `unknown gain still abstains and keeps scientific trace`() {
    val estimate = KStarEstimator.estimate(kStarInput(gain = PlantGain.unknown()))
    assertTrue(estimate.abstained)
    assertEquals("PLANT_GAIN_UNKNOWN", estimate.reason)
    assertEquals(setOf(ScientificAuthority.CLASSIC_ASSISTED), estimate.scientificTrace.authorities)
    assertEquals("cng-frame", estimate.scientificTrace.petrolOnGasPhysicalEvidenceId)
    assertEquals("gas-frame", estimate.scientificTrace.petrolReferencePhysicalEvidenceId)
}

@Test fun `public kstar API has no raw double bypass`() {
    val estimates = KStarEstimator::class.java.methods.filter { it.name == "estimate" }
    assertEquals(1, estimates.size)
    assertEquals(listOf(KStarScientificInput::class.java), estimates.single().parameterTypes.toList())
}
```

The existing unknown-gain test must be migrated to typed input rather than duplicated with the old API.

- [ ] **Step 2: Run the focused RED test and confirm the typed entry/trace are missing**

```bash
rm -rf /tmp/vit143-red-2
git clone --depth 1 --branch feature/phase06-pragmatic-equivalence-20260821 https://github.com/viluadmcontas2-dot/OMEGAS-V8.2.git /tmp/vit143-red-2
cd /tmp/vit143-red-2
git rev-parse HEAD
./gradlew :app:testDebugUnitTest --tests "com.omegas.prohub.physics.CalibrationPhysicsFoundationTest" --no-daemon
```

Expected: FAIL at compile/API assertions because `KStarEstimator` does not yet accept `KStarScientificInput`, `KStarEstimate` has no trace, and the raw-double public method still exists.

- [ ] **Step 3: Implement typed K* with stable abstention order and trace**

Add:

```kotlin
data class KStarScientificTrace(
    val authorities: Set<ScientificAuthority>,
    val evidenceIds: Set<String>,
    val petrolOnGasPhysicalEvidenceId: String?,
    val petrolReferencePhysicalEvidenceId: String?,
    val provenance: Set<String>,
)
```

Add `scientificTrace: KStarScientificTrace` to `KStarEstimate`.

Replace the public raw-double estimator with:

```kotlin
object KStarEstimator : TargetEstimator {
    fun estimate(input: KStarScientificInput): KStarEstimate {
        val petrolOnGas = input.petrolOnGas
        val petrolReference = input.petrolReference
        val error = ln(petrolOnGas.valueMs / petrolReference.valueMs)
        val theta = ln(input.currentFactor)
        val trace = KStarScientificTrace(
            authorities = petrolOnGas.evidence.authorities + petrolReference.evidence.authorities,
            evidenceIds = petrolOnGas.evidence.evidenceIds + petrolReference.evidence.evidenceIds,
            petrolOnGasPhysicalEvidenceId = petrolOnGas.evidence.physicalEvidenceId,
            petrolReferencePhysicalEvidenceId = petrolReference.evidence.physicalEvidenceId,
            provenance = petrolOnGas.evidence.provenance + petrolReference.evidence.provenance,
        )

        fun abstain(reason: String): KStarEstimate = KStarEstimate(
            logError = error,
            currentTheta = theta,
            targetTheta = null,
            targetFactor = null,
            gain = input.gain,
            authority = MagnitudeAuthority.UNKNOWN,
            abstained = true,
            reason = reason,
            scientificTrace = trace,
        )

        if (petrolOnGas.evidence.role == ScientificEvidenceRole.PREDICTION ||
            petrolReference.evidence.role == ScientificEvidenceRole.PREDICTION
        ) return abstain("PREDICTION_IS_NOT_OBSERVATION")

        if (petrolOnGas.evidence.effectiveWeight <= 0.0 ||
            petrolReference.evidence.effectiveWeight <= 0.0
        ) return abstain("NO_SCIENTIFIC_WEIGHT")

        val onGasPhysicalId = petrolOnGas.evidence.physicalEvidenceId
        val referencePhysicalId = petrolReference.evidence.physicalEvidenceId
        if (onGasPhysicalId != null && onGasPhysicalId == referencePhysicalId) {
            return abstain("SELF_COMPARISON_EVIDENCE")
        }

        val g = input.gain.mean
        if (g == null || !g.isFinite() || g <= 0.0) return abstain("PLANT_GAIN_UNKNOWN")

        val targetTheta = theta + error / g
        return KStarEstimate(
            logError = error,
            currentTheta = theta,
            targetTheta = targetTheta,
            targetFactor = exp(targetTheta),
            gain = input.gain,
            authority = input.gain.authority,
            abstained = false,
            reason = "GAIN_SUPPORTED",
            scientificTrace = trace,
        )
    }
}
```

Do not retain a public raw-double overload or a default authority adapter.

- [ ] **Step 4: Migrate Fast Physics to the typed public entry**

In `FastPhysicsGateEvaluator.kt`, create a private helper that builds explicit synthetic `CLASSIC_ASSISTED + OBSERVATION` inputs with distinct physical ids for gasoline reference and CNG-side observation, then replace both raw `KStarEstimator.estimate(...)` calls with typed calls. Do not alter gate thresholds, oracle math, scenario math, StepPolicy, or actuator allocation.

The helper must construct ids deterministically from the scenario index or semantic role, for example:

```kotlin
private fun syntheticKStarInput(
    petrolOnGasMs: Double,
    petrolReferenceMs: Double,
    currentFactor: Double,
    gain: PlantGain,
    suffix: String,
): KStarScientificInput {
    fun evidence(id: String): ResolvedScientificEvidence = ResolvedScientificEvidence(
        authorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
        role = ScientificEvidenceRole.OBSERVATION,
        evidenceIds = setOf(id),
        physicalEvidenceId = id,
        effectiveWeight = 1.0,
        provenance = setOf("SYNTHETIC_FAST_PHYSICS_GATE"),
    )
    return KStarScientificInput(
        petrolOnGas = ScientificMeasurement(petrolOnGasMs, evidence("fast-cng-$suffix")),
        petrolReference = ScientificMeasurement(petrolReferenceMs, evidence("fast-gas-$suffix")),
        currentFactor = currentFactor,
        gain = gain,
    )
}
```

- [ ] **Step 5: Run focused GREEN tests**

```bash
rm -rf /tmp/vit143-green-2
git clone --depth 1 --branch feature/phase06-pragmatic-equivalence-20260821 https://github.com/viluadmcontas2-dot/OMEGAS-V8.2.git /tmp/vit143-green-2
cd /tmp/vit143-green-2
git rev-parse HEAD
./gradlew :app:testDebugUnitTest --tests "com.omegas.prohub.physics.CalibrationPhysicsFoundationTest" --tests "com.omegas.prohub.physics.FastPhysicsGateEvaluatorTest" --no-daemon
```

Expected: BUILD SUCCESSFUL. The Fast Physics report remains numerically identical for the existing deterministic scenarios.

- [ ] **Step 6: Inspect the Task 2 diff**

Confirm the K* equation is unchanged, producer label is not used in the target formula, `MagnitudeAuthority` still comes only from plant gain on successful estimates, and no raw-double public entry remains.

---

### Task 3: Regression proof, structural falsifiers, and implementation handoff

**Files:**
- Test/read: `app/src/test/java/com/omegas/prohub/physics/ScientificEvidenceContractTest.kt`
- Test/read: `app/src/test/java/com/omegas/prohub/physics/CalibrationPhysicsFoundationTest.kt`
- Test/read: `app/src/test/java/com/omegas/prohub/physics/Phase6BindingIntegrationTest.kt`
- Test/read: `app/src/test/java/com/omegas/prohub/physics/ConditionalActuatorTargetTest.kt`
- Test/read: `app/src/test/java/com/omegas/prohub/physics/FastPhysicsGateEvaluatorTest.kt`
- Test/read: `app/src/test/java/com/omegas/prohub/physics/PhysicsOracleValidationTest.kt`
- Review: all files changed relative to canonical `rebuild/v8.2-final-implementation`.

**Interfaces:**
- Consumes: integrated Task 1 + Task 2 branch state.
- Produces: exact-SHA implementation evidence and `IMPLEMENTED_AWAITING_AUDIT`; does not grant PASS.

- [ ] **Step 1: Run the named regression surface on a fresh exact remote checkout**

```bash
rm -rf /tmp/vit143-regression
git clone --depth 1 --branch feature/phase06-pragmatic-equivalence-20260821 https://github.com/viluadmcontas2-dot/OMEGAS-V8.2.git /tmp/vit143-regression
cd /tmp/vit143-regression
git rev-parse HEAD
./gradlew :app:testDebugUnitTest \
  --tests "com.omegas.prohub.physics.ScientificEvidenceContractTest" \
  --tests "com.omegas.prohub.physics.CalibrationPhysicsFoundationTest" \
  --tests "com.omegas.prohub.physics.Phase6BindingIntegrationTest" \
  --tests "com.omegas.prohub.physics.ConditionalActuatorTargetTest" \
  --tests "com.omegas.prohub.physics.FastPhysicsGateEvaluatorTest" \
  --tests "com.omegas.prohub.physics.PhysicsOracleValidationTest" \
  --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the complete app unit-test task if the ephemeral environment supports it**

```bash
cd /tmp/vit143-regression
./gradlew :app:testDebugUnitTest --stacktrace --no-daemon
```

Expected: BUILD SUCCESSFUL. If the environment itself cannot execute the task, classify the result as `TEST_NOT_AVAILABLE` or `HARNESS_FAILURE`; do not replace it with GitHub Actions unless a separately justified remote-only dependency exists.

- [ ] **Step 3: Falsify duplicate runtime/write surfaces by reviewing the exact diff**

Compare `rebuild/v8.2-final-implementation` to the feature branch and confirm changed production paths are limited to the typed evidence/K* consumer boundary. Search the diff for additions of `Thread`, `Executor`, `CoroutineScope`, `launch`, `Timer`, `Scheduled`, `Store`, `Queue`, serial/USB writer calls, persistence APIs, Draft, and Writer. Any material new runtime/write surface is a failure of Step 123B.

- [ ] **Step 4: Falsify the raw K* bypass and authority inflation**

Confirm source exposes only `estimate(KStarScientificInput)`, successful `KStarEstimate.authority` is derived from `input.gain.authority`, and no branch on `ScientificAuthority` changes the K* numeric result.

- [ ] **Step 5: Revalidate branch topology and freeze candidate SHA**

Confirm feature branch is ahead of, not behind, `rebuild/v8.2-final-implementation`, with a merge base at the previously reconciled canonical lineage. Record exact candidate SHA and diff paths.

- [ ] **Step 6: Record implementation receipt in Linear**

Set `VIT-143` execution state to the project status equivalent of implementation complete / audit pending if available, and add a comment containing exact branch/SHA, changed files, RED/GREEN evidence, regression commands/results, unresolved gaps, and:

```text
IMPLEMENTATION_STATE=IMPLEMENTED_AWAITING_AUDIT
PRODUCTION_SOURCE_MUTATED=true
PHYSICAL_RK3326_CLAIM=NO
REMOTE_CI_USED=NO
```

Do not set Done and do not claim PASS.

- [ ] **Step 7: Start a fresh read-only normative audit and distinct meta-audit**

The audit must use a new `AUDIT_EPOCH_ID + AUDIT_RUN_ID + AUDIT_SCOPE_FINGERPRINT`, `AUDITOR_MODE=READ_ONLY_NORMATIVE`, `AUDITOR_NORMATIVE_WRITES=0`, re-open the exact candidate SHA and current contracts, and try to falsify every item in Spec §20. A finding closes that audit run as non-PASS and requires remediation outside the audit followed by a new epoch/run. Final closure requires a separate `META_AUDIT_RUN_ID` and `META_AUDIT=PASS`.
