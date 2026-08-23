# Phase 06 — 123B R1 Remediation Clarification

Status: BINDING_IMPLEMENTATION_CLARIFICATION_WITHIN_APPROVED_123B_SCOPE

Base design: `docs/superpowers/specs/2026-08-23-phase06-typed-scientific-authority-design.md`
Owner: VIT-143 / Step 123B
Trigger: `VIT143-AUDIT-20260823-R1 = FAIL / REAL_CONSUMER_CALL_PATH_NOT_PROVEN`

This document does **not** create a second design authority or expand Step 123B. It records the minimum correction required to satisfy the already-approved design and AL-004 real-consumer falsifier. Where the base design describes `ScientificMeasurement` as wrapping one `ResolvedScientificEvidence`, this clarification supersedes only that representation detail for aggregate K* measurements.

## Root cause

The first implementation correctly created typed scientific evidence and a typed-only public K* estimator, but the only production caller of the new K* entry was `FastPhysicsGateEvaluator`, which supplies synthetic evidence. The real bounded RPM+MAP→petrol-Tinj runtime already produced the scientific observation pair, but its Advisor projection did not consume the typed K* boundary.

A second semantic problem was exposed while tracing the real call path: `EquivalenceSurface`/Advisor values are bounded **statistical aggregates**, not one raw frame. Requiring one `physicalEvidenceId` for such an aggregate would fabricate provenance.

## Corrected representation boundary

Raw evidence remains strict:

- `PhysicsScientificInput + OBSERVATION` still requires one real `physicalEvidenceId`;
- duplicate raw physical observations are still resolved once, with no producer priority;
- raw conflicting role/weight remains an explicit conflict.

K* aggregate evidence is now a separate type:

```kotlin
data class KStarScientificEvidence(
    val authorities: Set<ScientificAuthority>,
    val role: ScientificEvidenceRole,
    val evidenceIds: Set<String>,
    val physicalEvidenceIds: Set<String>,
    val effectiveSupport: Double,
    val provenance: Set<String>,
)
```

Rules:

- `effectiveSupport` is finite and non-negative and may exceed 1 because it represents statistical support/ESS, not raw per-observation weight;
- `physicalEvidenceIds` may be empty for a bounded aggregate when raw constituent IDs are intentionally not retained in hot state;
- aggregate evidence must never invent a singular physical ID;
- raw `ResolvedScientificEvidence` converts losslessly into `KStarScientificEvidence`.

## Two-stage K* seam

`KStarEstimator` now exposes two typed stages:

1. `assess(petrolOnGas, petrolReference)` — validates Observation-vs-Prediction, positive support, self-comparison and provenance, then returns `KStarObservationAssessment` with `logError=ln(Tpet_GNV/Tpet_ref)` and side-specific scientific trace. It does **not** require or fabricate `currentFactor` or `PlantGain`.
2. `estimate(KStarScientificInput)` — reuses the assessment, then applies the unchanged K* target equation only when real `currentFactor` and a supported `PlantGain` are supplied.

This separation is required because the real equivalence runtime has enough evidence to validate the physical observation pair but does not own a trustworthy current actuator factor or plant-gain estimate. Missing values remain missing; no `currentFactor=1` or default `g=1` is invented.

## Real production consumer path

The real call path is now:

`SignalLearningStore.ingest → PersistentEquivalenceRuntime/EquivalenceSurface → SignalLearningStore advisor refresh (existing executor) → BoundedEquivalenceAdvisorSnapshot.build → KStarEstimator.assess → Advisor comparison JSON`

Properties of this path:

- it is the same bounded equivalence science used by Step 123;
- it runs at the existing Advisor boundary, not in the telemetry hot path;
- it creates no polling loop, thread, executor, Store, queue, persistence system, serial authority, Draft or Writer;
- it labels the current bounded producer as `CLASSIC_ASSISTED + OBSERVATION`;
- it preserves side-specific aggregate evidence IDs and support;
- it deliberately leaves aggregate `physicalEvidenceIds` empty rather than fabricate raw-frame identity;
- it computes observation eligibility and `logError` only; it does not create a K* target without current factor/gain.

## K* trace clarification

`KStarScientificTrace` is side-specific for future Classic/Adaptive arbitration:

- petrol-on-GNV authorities/evidence IDs/physical IDs;
- petrol-reference authorities/evidence IDs/physical IDs;
- combined provenance;
- union accessors may be exposed for existing consumers.

Producer labels remain provenance only and cannot alter the K* equation or `MagnitudeAuthority`.

## R1 remediation falsifiers

The remediation is invalid if any of these become true:

1. aggregate evidence invents a raw physical frame ID;
2. `BoundedEquivalenceAdvisorSnapshot` stops calling the typed K* assessment;
3. K* assessment runs in the telemetry ingest hot arithmetic rather than the existing Advisor boundary;
4. the bridge creates a second Store/thread/polling/writer path;
5. `CLASSIC_ASSISTED`, `OEM_NATIVE` or `ADAPTIVE_SHADOW` changes `logError` for identical numeric observations;
6. Prediction becomes eligible as Observation;
7. zero support becomes eligible;
8. overlapping evidence IDs or physical IDs can compare against themselves;
9. the bridge fabricates `currentFactor`, `PlantGain` or a target factor;
10. the later full `estimate(...)` stops using the original `theta + logError/g` equation.

## Evidence boundary

Focused RED/GREEN evidence for this remediation:

- RED: `KStarAggregateEvidenceTest` failed against the pre-remediation API because `KStarScientificEvidence` and `KStarEstimator.assess(...)` did not exist;
- GREEN: aggregate K* contract harness passed 5/5 cases;
- GREEN: bounded-equivalence→K* bridge harness produced eligible Classic observation, exact `ln(3.3/3.0)` log error, distinct reference/CNG aggregate evidence IDs, and empty physical-ID sets;
- full Android Gradle remains unavailable in the current ephemeral runtime because GitHub DNS/checkout is unavailable; remote CI is not invoked by convenience.

A new read-only normative audit must use the post-remediation SHA. R1 remains FAIL history and cannot be upgraded in place.
