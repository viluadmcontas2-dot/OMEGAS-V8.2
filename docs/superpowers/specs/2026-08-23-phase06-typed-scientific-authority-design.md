# Phase 06 — 123B Typed Scientific Authority for Physics/K* Design

Status: DRAFT_FOR_OWNER_REVIEW

Owner: VIT-143 / Step 123B

This design implements the owner-approved direction: choose the valid architecture that minimizes future coupling, migration debt, and rework. It extends the existing Phase 06 scientific authority vocabulary instead of creating a parallel runtime, a duplicate authority taxonomy, or a second Physics engine.

## 1. Goal

Bind the real Physics/K* entry to typed scientific evidence from the three existing scientific producers:

- `OEM_NATIVE`
- `CLASSIC_ASSISTED`
- `ADAPTIVE_SHADOW`

All three remain producers over the same acquisition/runtime backbone. Producer origin must remain auditable, but it must never silently change the K* mathematics, multiply one physical observation into several votes, or turn a model prediction into a physical observation.

The result must be a durable boundary that later Arbitration/Draft/UI work can consume without replacing the Physics estimator again.

## 2. Binding authorities

This design follows the current OMEGAS contracts:

- `OME-ADP-001`: one physical/runtime backbone; three typed scientific authorities; no second polling loop, Store, serial authority, Draft, or Writer; conflict remains typed and observable; Prediction is never Observation.
- `AL-004 / Fast-to-Zero`: K* remains a physics target estimator; arbitrary visit/sample constants do not become scientific authority; false precision is a failure; abstention is valid when evidence is ineligible.
- Step 123 pragmatic equivalence: RPM+MAP locates comparable state and petrol Tinj is the primary comparison signal; downstream consumers may reduce upstream authority, never inflate it.
- Existing single-writer/manual-write safety remains unchanged.

## 3. Current source state and the actual gap

The canonical source already contains the right producer enum in `Phase6OwnerBindings.kt`:

```kotlin
enum class ScientificAuthority {
    OEM_NATIVE,
    CLASSIC_ASSISTED,
    ADAPTIVE_SHADOW,
}
```

It also contains `PhysicsScientificInput(authority, physicalEvidenceId, weight)`. However that type is not the real K* entry contract yet.

Three material gaps remain:

1. `KStarEstimator.estimate(...)` accepts untyped raw doubles and `PlantGain`, so producer origin and Observation-vs-Prediction semantics can be bypassed.
2. `PhysicsScientificInput.deduplicateByPhysicalEvidence(...)` silently ranks producers `OEM_NATIVE > CLASSIC_ASSISTED > ADAPTIVE_SHADOW`. That prevents duplicate counting but violates the current ADP-001 rule that no scientific authority silently dominates another.
3. `KStarEstimate` does not preserve producer/evidence provenance, so downstream code cannot prove where the accepted science came from.

Step 123B fixes those gaps without changing the K* equation.

## 4. Non-goals

Step 123B does **not**:

- create Proposal Arbitration; that belongs to later `147B–164B` work;
- decide that OEM, Classic, or Adaptive is globally superior;
- average conflicting producer predictions;
- promote an `ADAPTIVE_SHADOW` prediction into an observation;
- add a second Physics engine, Learning Store, acquisition path, scheduler, serial loop, recorder, Draft, or Writer;
- change the K* log-domain equation or invent a new plant-gain prior;
- change StepPolicy damping, MAP/Curve allocation policy, or writer safety;
- make an Adaptive physical-accuracy claim;
- add JSON/persistence work to the telemetry hot path;
- duplicate Calibration Identity or epoch-validity logic inside K*;
- propagate producer-origin metadata into final Draft/UI yet.

## 5. Architectural decision

Promote the existing `ScientificAuthority` enum into the canonical **producer-origin** type and add one orthogonal semantic axis: `ScientificEvidenceRole`.

Do not create another enum for the same three producers.

The authority vocabulary stays deliberately multi-axis:

| Type | Question it answers | Examples |
| --- | --- | --- |
| `ScientificAuthority` | Which scientific producer exposed this claim? | `OEM_NATIVE`, `CLASSIC_ASSISTED`, `ADAPTIVE_SHADOW` |
| `ScientificEvidenceRole` | Is the numeric claim observed or predicted? | `OBSERVATION`, `PREDICTION` |
| `MagnitudeAuthority` | How strongly is a target/gain magnitude anchored? | `PHYSICALLY_ANCHORED`, `EMPIRICALLY_BOUNDED`, `POLICY_ONLY`, `UNKNOWN` |
| `PhysicsEvidenceAuthority` | What is the knownness/provenance class of a physical factor? | `LIVE_VALIDATED`, `STATIC_ORACLE_CANDIDATE`, `OBSERVED_CONTEXT`, ... |
| `ScientificDecisionAuthority` | Where did a comparison/decision rule come from? | native anchor vs OMEGAS comparability policy |

These axes must never be collapsed or silently mapped to each other. `OEM_NATIVE` does not automatically mean `PHYSICALLY_ANCHORED`, and `ADAPTIVE_SHADOW` does not automatically mean low or high magnitude authority.

## 6. File boundaries

Create:

`app/src/main/java/com/omegas/prohub/physics/ScientificEvidence.kt`

Responsibilities:

- own `ScientificAuthority` (moved, not duplicated);
- define `ScientificEvidenceRole`;
- own `PhysicsScientificInput`;
- consolidate duplicate exposure of the same physical observation without producer ranking;
- represent explicit consolidation conflicts;
- define the resolved evidence object used by K*.

Modify:

`app/src/main/java/com/omegas/prohub/physics/Phase6OwnerBindings.kt`

After the split this file keeps legacy Advisor/Phase 06 bridge helpers only; it no longer owns the core producer taxonomy.

Modify:

`app/src/main/java/com/omegas/prohub/physics/CalibrationPhysicsFoundation.kt`

Responsibilities:

- typed K* public entry;
- unchanged numerical estimator core;
- abstention on scientifically ineligible evidence;
- output scientific trace.

Modify:

`app/src/main/java/com/omegas/prohub/physics/FastPhysicsGateEvaluator.kt`

It must exercise the same typed K* public entry using explicit synthetic observations rather than keep a raw-double bypass.

Add focused tests under the existing Phase 06 physics test package.

## 7. Canonical raw evidence claim

### 7.1 Evidence role

```kotlin
enum class ScientificEvidenceRole {
    OBSERVATION,
    PREDICTION,
}
```

`OBSERVATION` means the numeric claim is grounded in physical evidence from the shared acquisition/runtime backbone.

`PREDICTION` means a model produced or transformed the value. Predictions are valid scientific objects for later arbitration/validation, but they are not physical observations.

### 7.2 Producer claim

`PhysicsScientificInput` becomes:

```kotlin
data class PhysicsScientificInput(
    val authority: ScientificAuthority,
    val role: ScientificEvidenceRole,
    val evidenceId: String,
    val physicalEvidenceId: String?,
    val weight: Double,
    val provenance: String,
)
```

Invariants:

- `evidenceId` and `provenance` are non-blank;
- `weight` is finite and in `0.0..1.0`;
- `OBSERVATION` requires a non-blank `physicalEvidenceId` because it claims physical lineage;
- `PREDICTION` may have `physicalEvidenceId=null` because a model result may aggregate many observations and must not invent one physical source id;
- no constructor defaults role to `OBSERVATION`;
- no constructor defaults producer to `CLASSIC_ASSISTED`.

This intentionally does **not** duplicate `CalibrationIdentity` fields. Epoch/reference validity remains the responsibility of the upstream evidence/reference layer that already owns it.

## 8. Why K* does not compare calibration fingerprints

The gasoline reference and the current CNG observation do not necessarily belong to the same CNG calibration fingerprint. Step 123 intentionally allows useful gasoline reference science to survive later CNG calibration changes while stale CNG comparison state is controlled separately.

Therefore Step 123B must **not** require “reference fingerprint == CNG fingerprint” inside K*. Doing that would duplicate identity policy and reject valid reference reuse.

If later Adaptive model lineage requires a calibration/model binding token, that token belongs to the producer/proposal contract that owns the model. K* consumes already-eligible scientific claims and preserves their provenance; it does not become a second Calibration Identity authority.

## 9. Duplicate physical evidence: one vote, all origins visible

The current priority-based `deduplicateByPhysicalEvidence(...)` is removed.

Raw `OBSERVATION` claims are consolidated by `physicalEvidenceId` into a resolved object conceptually shaped as:

```kotlin
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
```

Rules:

- the same physical observation exposed by several producers becomes **one** resolved observation;
- all contributing `authorities`, `evidenceIds`, and provenance remain visible;
- the physical scientific weight is not summed, averaged, or selected by producer priority;
- duplicate exposures of the same physical observation are accepted only when they report the same physical weight; that shared value becomes `effectiveWeight`;
- conflicting weights for the same physical observation produce `SCIENTIFIC_WEIGHT_CONFLICT` instead of a hidden max/min choice;
- mixed Observation/Prediction semantics for the same claimed physical source produce `SCIENTIFIC_ROLE_CONFLICT`;
- Prediction claims without a physical id remain separate model claims and are not “deduplicated as physical observations.”

This makes duplicate handling conservative and future-proof: one physical event cannot gain authority merely because more producers looked at it, and disagreement is not hidden.

## 10. Typed K* measurement boundary

K* receives two typed measurements:

```kotlin
data class ScientificMeasurement(
    val valueMs: Double,
    val evidence: ResolvedScientificEvidence,
)

data class KStarScientificInput(
    val petrolOnGas: ScientificMeasurement,
    val petrolReference: ScientificMeasurement,
    val currentFactor: Double,
    val gain: PlantGain,
)
```

The field names deliberately encode the Step 123 physical objective. A generic “value A/value B” API would be easier to misuse.

## 11. K* eligibility and abstention

`KStarEstimator` accepts `KStarScientificInput` as its public production entry.

The numerical equation remains unchanged:

```text
e = ln(Tpet_GNV / Tpet_ref)
theta = ln(F_current)
theta* = theta + e / g
F* = exp(theta*)
```

Before running that equation, the estimator validates scientific eligibility.

It abstains with `targetFactor=null`, `MagnitudeAuthority.UNKNOWN`, and a stable reason when:

- either measurement role is `PREDICTION` → `PREDICTION_IS_NOT_OBSERVATION`;
- either resolved observation has zero effective weight → `NO_SCIENTIFIC_WEIGHT`;
- the exact same physical evidence id is used as both the CNG-side observation and gasoline reference → `SELF_COMPARISON_EVIDENCE`;
- plant gain is unknown → existing `PLANT_GAIN_UNKNOWN` behavior.

Existing positive/finite numeric preconditions remain explicit developer invariants. Step 123B does not turn K* into a telemetry sanitizer.

Producer identity does not alter the equation. With identical eligible numeric measurements, OEM, Classic, and Adaptive Observation labels must produce the same numeric K* result.

## 12. Adaptive Shadow rule

`ADAPTIVE_SHADOW` may expose two kinds of claims:

1. A physical observation forwarded/classified from the shared backbone: role `OBSERVATION`, with real `physicalEvidenceId`.
2. A model-derived value: role `PREDICTION`, with its own `evidenceId`; a singular physical id is optional because the model may aggregate many observations.

The second form is explicitly ineligible to occupy either physical K* measurement slot.

This preserves Adaptive Shadow as a scientific producer without allowing prediction leakage into observation authority.

## 13. K* output trace

`KStarEstimate` gains a compact trace:

```kotlin
data class KStarScientificTrace(
    val authorities: Set<ScientificAuthority>,
    val evidenceIds: Set<String>,
    val petrolOnGasPhysicalEvidenceId: String?,
    val petrolReferencePhysicalEvidenceId: String?,
    val provenance: Set<String>,
)
```

Every estimate produced from scientifically eligible evidence carries this trace, including an estimate that later abstains because plant gain is unknown.

For scientific-eligibility abstention, the result carries enough resolved trace to explain the rejection when available.

This is the stable handoff for later `ClassicProposal`, `AdaptiveProposal`, and `ProposalArbitration` work. Step 123B does not yet project it into Draft/UI.

## 14. No public untyped bypass

The current public overload:

```kotlin
KStarEstimator.estimate(
    petrolOnGasMs,
    petrolReferenceMs,
    currentFactor,
    gain,
)
```

must not remain as a public production bypass after 123B.

The implementation may retain the pure numeric calculation as a private/internal helper after scientific eligibility passes, but callers enter through `KStarScientificInput`.

Keeping a public raw-double escape hatch would make the new type contract optional and recreate the exact migration debt this step exists to remove.

## 15. Existing consumers

`FastPhysicsGateEvaluator` migrates to the typed K* API. Its synthetic scenarios create explicit `CLASSIC_ASSISTED + OBSERVATION` evidence ids and physical ids.

`ConditionalActuatorTargets` remains downstream of `KStarEstimate`; its arithmetic does not change. It automatically receives an estimate that now has producer/evidence trace.

`PhysicsOracleValidator` remains an independent numeric oracle. It is not a production K* entry and may continue to operate on raw numeric scenarios because its purpose is to independently validate the mathematics, not establish live provenance.

## 16. Error/conflict semantics

Expected scientific mismatch is data, not silent coercion:

- consistent duplicate physical exposure → consolidate once;
- conflicting weights → explicit `SCIENTIFIC_WEIGHT_CONFLICT`;
- incompatible roles on the same claimed physical source → explicit `SCIENTIFIC_ROLE_CONFLICT`;
- Prediction in a K* observation slot → abstain;
- same physical evidence on both sides of the comparison → abstain;
- unknown plant gain → abstain.

Programmer-contract violations such as blank required ids or non-finite weights fail fast at construction time.

No producer fallback is allowed. Rejecting an Adaptive prediction never relabels it Classic or substitutes OEM evidence silently.

## 17. Performance / RK3326 boundary

The change must not affect MP48 acquisition cadence or add per-frame heavy work.

Constraints:

- no new polling, scheduler, thread, Store, queue, or persistence path;
- no JSON in K* arithmetic;
- no historical scan;
- consolidation operates only on the small producer set for one evidence item/decision;
- K* remains O(1);
- resolved authority sets are bounded by the fixed producer count, not drive duration;
- no physical RK3326 claim is made by Step 123B.

A source review must explicitly confirm that the diff introduces no second acquisition/runtime/writer surface.

## 18. TDD falsifiers

Implementation starts with failing tests that demonstrate the current source violates this contract.

Required falsifiers:

1. Same physical observation exposed by OEM + Classic resolves to one vote while preserving both authorities; neither silently wins.
2. Same physical observation with conflicting weights yields `SCIENTIFIC_WEIGHT_CONFLICT`.
3. Observation/Prediction disagreement on the same claimed physical source yields `SCIENTIFIC_ROLE_CONFLICT`.
4. A prediction may exist without inventing a singular physical id.
5. K* accepts typed Classic observations and reproduces the current numeric target exactly.
6. Re-labeling identical eligible observations among OEM/Classic/Adaptive Observation leaves numeric K* unchanged.
7. `ADAPTIVE_SHADOW + PREDICTION` in either K* measurement slot yields `PREDICTION_IS_NOT_OBSERVATION` abstention.
8. Reusing the same physical observation as both reference and CNG-side evidence yields `SELF_COMPARISON_EVIDENCE` abstention.
9. Zero scientific weight cannot create a target.
10. Unknown plant gain still abstains exactly as before.
11. `KStarEstimate` preserves producer/evidence/physical lineage trace.
12. `FastPhysicsGateEvaluator` compiles and passes using only the typed public K* entry.
13. No public raw-double K* entry remains after migration.
14. Existing Phase 06 Physics tests remain green.

## 19. Regression surface

At minimum verify:

- focused `ScientificEvidence` tests;
- `CalibrationPhysicsFoundationTest`;
- `Phase6BindingIntegrationTest`;
- `ConditionalActuatorTargetTest`;
- `FastPhysicsGateEvaluatorTest`;
- `PhysicsOracleValidationTest`;
- the full relevant Physics test package when the available execution surface can run it.

A new source SHA invalidates any prior Step 123B source-audit receipt.

## 20. Audit contract

Implementation closes at most as `IMPLEMENTED_AWAITING_AUDIT`.

PASS requires a fresh read-only normative audit on the frozen candidate SHA with a new `AUDIT_EPOCH_ID` and `AUDIT_RUN_ID`, followed by a distinct read-only meta-audit.

The audit must try to falsify at least:

- producer origin cannot alter K* math;
- Prediction cannot become Observation;
- duplicate physical evidence cannot multiply authority;
- producer disagreement cannot be hidden by priority/max/min voting;
- no second runtime/Store/polling/writer exists;
- no public untyped production K* bypass remains;
- producer origin cannot inflate `MagnitudeAuthority`;
- source branch/canonical topology is fresh.

No physical RK3326 claim is required for Step 123B. Hardware-dependent performance and Adaptive physical-accuracy claims remain for later gates.

## 21. Invalidation events

This design or a resulting PASS becomes stale on material change to:

- `ScientificAuthority` producer semantics;
- `ScientificEvidenceRole` semantics;
- ADP-001 Observation/Prediction or single-backbone contract;
- K* equation or plant-gain authority model;
- upstream evidence identity/provenance semantics;
- addition of a new scientific producer;
- later Proposal Arbitration discovering a truly required lineage field that cannot be derived from existing provenance;
- source changes in the typed evidence/K* call path after audit.

## 22. Decision summary

The future-proof choice is to make typed scientific evidence **mandatory at the K* boundary now**, using the producer enum that already exists and keeping evidence role orthogonal to magnitude/decision authority.

One physical observation counts once. Every contributing producer remains visible. Disagreement is explicit rather than silently ranked. Prediction and Observation are distinct scientific claims. K* stays producer-neutral. Calibration Identity remains owned by the layer that already governs it. The single runtime and single writer remain untouched.

That gives later Adaptive Arbitration a stable provenance-bearing Physics result without forcing another Physics-core migration.