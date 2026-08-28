# Phase 06 Physics & Equivalence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close OMEGAS V8.2 Phase 06 by completing evidence-bound calibration physics, causal mechanism classification, uncertainty/oracle validation, downstream authority propagation, and gate receipts.

**Architecture:** Extend the existing typed `com.omegas.prohub.physics` foundation rather than create a competing engine. Keep estimation, allocation and operational stepping separate; propagate explicit knownness/provenance/authority through current Advisor/Suggestion paths; classify residuals with abstention; verify focused properties before the final phase gate.

**Tech Stack:** Kotlin/JVM, org.json, JUnit, existing OMEGAS V7 runtime/advisor model.

**Spec:** `docs/superpowers/specs/2026-08-20-phase06-physics-equivalence-design.md`

## Global Constraints
- UNKNOWN never becomes zero or fabricated precision.
- K2/K4 static candidates never become live authority without independent evidence.
- K3 remains UNKNOWN.
- Bilinear/trilinear projection is LOCAL_MODEL only.
- TargetEstimator, ActuatorAllocator and StepPolicy remain separate.
- Legacy 45–90% is POLICY_ONLY StepPolicy baseline, never a physical target.
- No new serial/writer/scientific producer is introduced.
- Verification is local/ephemeral-first; do not use GitHub Actions.

---

### Task 1: Foundation contract completion
**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/physics/CalibrationPhysicsFoundation.kt`
- Modify: `app/src/main/java/com/omegas/prohub/physics/Phase6OwnerBindings.kt`
- Modify/Test: `app/src/test/java/com/omegas/prohub/physics/CalibrationPhysicsFoundationTest.kt`

**Interfaces:**
- Produces: evidence matrix, factor knownness/provenance, effective actuation, MagnitudeAuthority, ExpectedEffect, correction mechanisms, target/allocation/step interfaces.

- [ ] Add failing tests for matrix completeness, deadtime known/unknown/freshness, K1 neutrality/direction, MUL_ACT Q14 neutrality/direction, K2/K4 static-candidate non-promotion, K3 UNKNOWN, config-vs-live separation and local-model metadata.
- [ ] Run focused JVM test in ephemeral runtime if available; otherwise record `NOT_CAPABLE_WITH_REASON` and continue source implementation without claiming PASS.
- [ ] Implement minimal missing contracts and bindings.
- [ ] Re-run focused verification when runtime is available.

### Task 2: Authority propagation into Advisor/Suggestion
**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt`
- Modify: `app/src/main/java/com/omegas/prohub/calibration/AdvisorSuggestionAdapterV7.kt`
- Test: `app/src/test/java/com/omegas/prohub/learning/AssistedCalibrationAdvisorTest.kt`
- Test/Create: `app/src/test/java/com/omegas/prohub/calibration/AdvisorSuggestionPhysicsAuthorityTest.kt`

**Interfaces:**
- Consumes: `MagnitudeAuthority`, `ExpectedEffect`, mechanism and policy metadata.
- Produces: Advisor JSON and V7 suggestion projection carrying authority without gaining write authority.

- [ ] Add failing tests requiring Advisor 45–90 metadata to serialize as `POLICY_ONLY/STEP_POLICY_BASELINE` and requiring suggestion projection to preserve authority/mechanism/assumptions.
- [ ] Implement serialization and V7 projection with backward-compatible defaults for older persisted payloads.
- [ ] Verify no change in manual-only lifecycle/write authority.

### Task 3: Residual mechanism classifier
**Files:**
- Create: `app/src/main/java/com/omegas/prohub/physics/ResidualMechanismClassifier.kt`
- Create/Test: `app/src/test/java/com/omegas/prohub/physics/ResidualMechanismClassifierTest.kt`

**Interfaces:**
- Consumes: residual samples, `CalibrationPhysicsContext`, mechanism support flags.
- Produces: typed classification + reason code + `ExpectedEffect`/abstention.

- [ ] Add failing localized, broad-supported, broad-unsupported, environmental-confounded and insufficient-evidence fixtures.
- [ ] Implement classifier: localized repeatable -> MAP_LOCAL candidate; broad coherent + compatible mechanism -> CURVE_MUL_ACT; environmental correlation -> ENVIRONMENTAL_DIAGNOSTIC; insufficient/contradictory -> NO_ACTION/UNKNOWN.
- [ ] Ensure no default-to-K path exists.

### Task 4: K* and uncertainty oracle
**Files:**
- Create: `app/src/main/java/com/omegas/prohub/physics/PhysicsUncertaintyOracle.kt`
- Create/Test: `app/src/test/java/com/omegas/prohub/physics/PhysicsUncertaintyOracleTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/physics/CalibrationPhysicsFoundation.kt`

**Interfaces:**
- Consumes: residual/error ratio, effective actuation, gain prior/posterior, noise assumptions.
- Produces: K*/F* point/range, interval coverage summary, abstention when gain unsupported.

- [ ] Add failing tests for log-domain target sign, gain sensitivity, UNKNOWN gain abstention, deterministic seeded bootstrap/Monte-Carlo coverage and drift/noise perturbation.
- [ ] Implement bounded deterministic oracle for offline tests and compact analytic runtime result.
- [ ] Prove runtime approximation matches oracle within test tolerance on fixtures.

### Task 5: Evidence dependency invalidation
**Files:**
- Create: `app/src/main/java/com/omegas/prohub/physics/PhysicsEvidenceDependencies.kt`
- Create/Test: `app/src/test/java/com/omegas/prohub/physics/PhysicsEvidenceDependenciesTest.kt`

**Interfaces:**
- Produces: rule -> checkpoint dependencies and selective `STALE_BY_EVIDENCE` invalidation.

- [ ] Add failing test showing a K2 evidence change stales K2-dependent rules but not independent K1/deadtime rules.
- [ ] Implement dependency graph and selective invalidation.

### Task 6: Phase 06 focused suite and gate
**Files:**
- Modify tests under `app/src/test/java/com/omegas/prohub/physics/`
- No persistent CI/workflow files.

**Interfaces:**
- Produces: G4A/G4A-Fast evidence receipt.

- [ ] Run focused Physics JVM suite on the exact remote SHA in an ephemeral/local runtime when technically available.
- [ ] Run broader JVM/lint/contract verification that is available locally/ephemerally.
- [ ] Audit for forbidden claims: total `Tgas=Tpet*K1`, K3 fabricated value, static candidate promoted to live, policy fraction used as target, Map+Curve double application.
- [ ] Record exact evidence and SHA in Linear.
- [ ] Independent read-only audit + distinct meta-audit before final PASS.
- [ ] Close VIT-85 through VIT-120/VIT-131/VIT-143/VIT-144 according to actual evidence and set Phase 06 milestone to 100% only after gates satisfy governance.
