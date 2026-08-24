# Actuator Identification Step 153 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep `F*=M*×C*` as the primary identifiable physical target and expose actuator-specific Map/Curve targets only when the complementary actuator is conditioned/frozen or an explicit later allocator supplies decomposition authority.

**Architecture:** Reuse `KStarEstimate`, `CalibrationPhysicsContext`, and `ConditionalActuatorTargets` as the only owners of physical K*/F* and conditioned actuator equations. Add a pure identification layer that expresses Map/Curve freedom and returns at most one conditioned actuator target. Add calibration-bound relative-prior metadata with explicit MAP/CURVE dependency sets so source calibration changes invalidate only dependent artifacts.

**Tech Stack:** Kotlin/JVM; existing Phase06 Physics contracts; focused exact-blob Kotlin probes and property tests.

**Spec:** Notion `FASE 07 — 147–164 — Predictor e Suggestions`, Step 153.

## Constraints
- `F_current=M_eff×C_eff` and `F*` remain primary identifiable quantities.
- Never apply one residual simultaneously to Map and Curve.
- Curve conditioned/frozen + Map free => Map target delegates to `ConditionalActuatorTargets.mapLocal`.
- Map conditioned/frozen + Curve free => Curve target delegates to `ConditionalActuatorTargets.curveGlobal` and still requires `localResidualRemoved=true`.
- Both free => publish F* only; no Map/Curve target without explicit allocator/identification policy.
- Both frozen => no actuator target.
- No Curve-axis fallback for Map geometry.
- Historical absolute values are converted to relative correction against the source calibration before reuse.
- MAP/CURVE dependency identities remain separate.

### Task 1 — Freeze identification-state contract
**Files:**
- Create `app/src/test/java/com/omegas/prohub/physics/ActuatorTargetIdentificationTest.kt`
- Create `app/src/main/java/com/omegas/prohub/physics/ActuatorTargetIdentification.kt`

Tests:
- Curve frozen + Map free yields exactly Map target and recomposes `Map* × C_eff == F*`.
- Map frozen + Curve free + local residual removed yields exactly Curve target and recomposes `M_eff × Curve* == F*`.
- Map frozen + Curve free without local residual removal yields no Curve target.
- Both free yields `FSTAR_PRIMARY_BOTH_ACTUATORS_FREE`, with F* present and Map/Curve targets null.
- Both frozen yields no actuator target.
- Unknown/abstained K* yields no actuator target.
- No state may contain both non-null Map and Curve targets.

Implementation:
- `enum class ActuatorFreedom { FROZEN, FREE }`
- `data class ActuatorIdentificationInput(val kStar: KStarEstimate, val context: CalibrationPhysicsContext, val mapFreedom: ActuatorFreedom, val curveFreedom: ActuatorFreedom, val localResidualRemoved: Boolean)`
- `data class IdentifiedActuatorTarget(val fStar: Double?, val mapTarget: ConditionalActuatorTarget?, val curveTarget: ConditionalActuatorTarget?, val reason: String, val authority: MagnitudeAuthority)`
- `object ActuatorTargetIdentification.resolve(input)` delegates conditioned target computation to existing `ConditionalActuatorTargets`.

### Task 2 — Freeze calibration-bound relative prior/dependency contract
**Files:**
- Create `app/src/test/java/com/omegas/prohub/physics/CalibrationBoundRelativePriorTest.kt`
- Extend `ActuatorTargetIdentification.kt` or create focused `CalibrationBoundRelativePrior.kt` if separation improves readability.

Tests:
- source absolute F and target F are stored/recovered as `deltaStar=ln(F*/F_source)` rather than as a free absolute target;
- rebasing applies `F_current×exp(deltaStar)` only when required dependencies are compatible;
- a MAP-only artifact remains valid when only Curve changes;
- a CURVE-only artifact remains valid when only Map changes;
- an artifact depending on both invalidates on either change;
- unknown/blank calibration identity fails closed.

Implementation types:
- `enum class CalibrationDependency { MAP, CURVE }`
- `data class CalibrationDependencyIdentity(val mapHash: String, val curveFingerprint: String)`
- `data class CalibrationBoundRelativePrior(val sourceIdentity: CalibrationDependencyIdentity, val dependencies: Set<CalibrationDependency>, val sourceFactor: Double, val deltaStar: Double, val provenance: String)`
- factory converts `(sourceFactor,targetFactor)` to log-relative correction;
- `rebase(currentFactor,currentIdentity)` returns typed available/unavailable result and checks only declared dependencies.

### Task 3 — Exact verification and closure
- Reconstruct final remote production blobs; `git hash-object` must match GitHub.
- Focused property/fuzz: 1,000 random conditioned decompositions recompose F*; 1,000 dependency mutation matrices validate selective invalidation; both-free never emits two targets.
- Static scan: no writer/USB/serial/Router/Scheduler/Store/Android/JSON in new identification layer.
- Independent audit + distinct meta-audit in VIT-309.
- Full Android/device proof remains later PREAPK work and must not be inferred.
