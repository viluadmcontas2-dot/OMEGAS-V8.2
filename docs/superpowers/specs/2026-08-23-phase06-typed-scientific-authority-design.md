# Phase 06 — 123B Typed Scientific Authority for Physics/K* Design

Status: DRAFT_FOR_OWNER_REVIEW

Owner: VIT-143 / Step 123B

This design implements the owner-approved direction: choose the valid architecture that minimizes future coupling, migration debt, and rework. It extends the existing Phase 06 scientific authority vocabulary instead of creating a parallel runtime, a duplicate authority taxonomy, or a second Physics engine.

## 1. Goal

Bind the real Physics/K* entry to typed scientific evidence from the three existing scientific producers:

- `OEM_NATIVE`
- `CLASSIC_ASSISTED`
- `ADAPTIVE_SHADOW`

All three remain producers over the same acquisition/runtime backbone. The producer origin must be preserved and auditable, but it must not silently alter the K* mathematics, multiply one physical observation into several votes, or turn a model prediction into a physical observation.

The result must be a durable contract that later Arbitration/Draft/UI work can consume without migrating the Physics core again.

## 2. Binding authorities

This design follows the current OMEGAS governance and scientific contracts:

- `OME-ADP-001`: one physical/runtime backbone; three typed scientific authorities; no second polling loop, Store, serial authority, Draft, or Writer; conflict remains typed and observable; Prediction is never Observation.
- `AL-004 / Fast-to-Zero`: K* remains a physics target estimator; arbitrary visit/sample constants do not become scientific authority; false precision is a failure; abstention is valid when evidence is not eligible.
- Step 123 pragmatic equivalence contract: RPM+MAP locates comparable state and petrol Tinj is the primary comparison signal; downstream consumers may reduce upstream authority, never inflate it.
- Existing single-writer/manual-write safety contracts remain unchanged.

## 3. Current source state and the gap

The canonical source already contains the right producer enum in `Phase6OwnerBindings.kt`:

```kotlin
enum class ScientificAuthority {
    OEM_NATIVE,
    CLASSIC_ASSISTED,
    ADAPTIVE_SHADOW,
}
```

It also contains `PhysicsScientificInput(authority, physicalEvidenceId, weight)`. However that type is not yet the real K* input contract.

The current implementation has three material gaps:

1. `KStarEstimator.estimate(...)` accepts untyped raw doubles and `PlantGain`, so producer origin and Observation-vs-Prediction semantics can be bypassed.
2. `PhysicsScientificInput.deduplicateByPhysicalEvidence(...)` silently ranks producers `OEM_NATIVE > CLASSIC_ASSISTED > ADAPTIVE_SHADOW`. That prevents double counting, but it violates the current ADP-001 rule that no authority silently dominates another. The physical observation should count once while all contributing origins remain visible.
3. `KStarEstimate` does not preserve scientific producer provenance, so a downstream consumer cannot prove which scientific authority supplied the accepted evidence.

This step fixes those gaps without changing the K* equation itself.

## 4. Explicit non-goals

Step 123B does **not**:

- create Proposal Arbitration; that belongs to the later `147B–164B` scope;
- decide that OEM, Classic, or Adaptive is globally “better”;
- average conflicting producer predictions;
- promote an `ADAPTIVE_SHADOW` prediction into an observation;
- add a second Physics engine, Learning Store, acquisition path, scheduler, serial loop, recorder, Draft, or Writer;
- change the existing K* log-domain equation or invent a new plant-gain prior;
- change StepPolicy damping, MAP/Curve allocation policy, or writer safety;
- make any physical-accuracy claim for Adaptive Shadow;
- add new JSON/persistence work to the telemetry hot path;
- propagate producer-origin metadata into final UI/Draft contracts yet; later Adaptive arbitration owners will extend that boundary using the trace introduced here.

## 5. Architectural decision

Promote the existing `ScientificAuthority` enum into the canonical producer-origin type and add one orthogonal semantic axis: `ScientificEvidenceRole`.

Do not create another enum that means the same three producers.

The authority model becomes deliberately multi-axis:

| Type | Question it answers | Examples |
| --- | --- | --- |
| `ScientificAuthority` | Which scientific producer exposed this evidence? | `OEM_NATIVE`, `CLASSIC_ASSISTED`, `ADAPTIVE_SHADOW` |
| `ScientificEvidenceRole` | Is this physical evidence or a model result? | `OBSERVATION`, `PREDICTION` |
| `MagnitudeAuthority` | How strongly is a numeric target/gain anchored? | `PHYSICALLY_ANCHORED`, `EMPIRICALLY_BOUNDED`, `POLICY_ONLY`, `UNKNOWN` |
| `PhysicsEvidenceAuthority` | What is the knownness/provenance class of a physical factor? | `LIVE_VALIDATED`, `STATIC_ORACLE_CANDIDATE`, `OBSERVED_CONTEXT`, ... |
| `ScientificDecisionAuthority` | Where did a comparison/decision rule come from? | native anchor vs OMEGAS comparability policy |

These axes must not be collapsed or silently mapped to one another. In particular, `OEM_NATIVE` does not imply `PHYSICALLY_ANCHORED`, and `ADAPTIVE_SHADOW` does not imply low or high `MagnitudeAuthority` by itself.

## 6. File boundaries

The producer/evidence types become a core Physics contract rather than remaining mixed with Advisor JSON decoration.

Create:

`app/src/main/java/com/omegas/prohub/physics/ScientificEvidence.kt`

Responsibilities:

- own `ScientificAuthority` (moved, not duplicated);
- define `ScientificEvidenceRole`;
- own `PhysicsScientificInput`;
- consolidate duplicate exposure of the same physical evidence without silent producer ranking;
- represent explicit consolidation conflicts;
- define the typed measurement and K* evidence pair used by the estimator.

Modify:

`app/src/main/java/com/omegas/prohub/physics/Phase6OwnerBindings.kt`

Responsibilities after the split:

- legacy Advisor/Phase 06 bridge helpers only;
- no core scientific producer taxonomy.

Modify:

`app/src/main/java/com/omegas/prohub/physics/CalibrationPhysicsFoundation.kt`

Responsibilities:

- typed K* entry;
- unchanged numerical estimator core;
- abstention on scientifically ineligible evidence;
- output scientific trace.

Modify:

`app/src/main/java/com/omegas/prohub/physics/FastPhysicsGateEvaluator.kt`

Responsibilities:

- exercise the same typed K* API using explicit synthetic `CLASSIC_ASSISTED` observations;
- never use an untyped bypass just because the data are synthetic.

Tests live in the existing Phase 06 physics test package and add a focused scientific-authority contract test file rather than growing one giant test class.

## 7. Canonical evidence types

### 7.1 Evidence role

```kotlin
enum class ScientificEvidenceRole {
    OBSERVATION,
    PREDICTION,
}
```

`OBSERVATION` means the numeric value is grounded in physical evidence on the shared acquisition/runtime backbone.

`PREDICTION` means a model produced or transformed the numeric value. A prediction may be useful later for arbitration and validation, but it is not eligible to occupy a physical-observation slot in K*.

### 7.2 Raw producer exposure

`PhysicsScientificInput` remains the raw “producer X exposes physical evidence Y” object, but it is enriched so the evidence can survive future consumers without another schema migration:

```kotlin
data class PhysicsScientificInput(
    val authority: ScientificAuthority,
    val role: ScientificEvidenceRole,
    val physicalEvidenceId: String,
    val calibrationFingerprint: String,
    val weight: Double,
    val provenance: String,
    val modelVersion: String? = null,
    val predictionId: String? = null,
)
```

Required invariants:

- `physicalEvidenceId`, `calibrationFingerprint`, and `provenance` are non-blank;
- `weight` is finite and in `0.0..1.0`;
- `ADAPTIVE_SHADOW + PREDICTION` requires non-blank `modelVersion` and `predictionId`;
- no constructor silently defaults the role to `OBSERVATION`;
- no constructor silently defaults the producer to `CLASSIC_ASSISTED`.

`modelVersion`/`predictionId` are lineage only. Their presence never upgrades a prediction into an observation.

## 8. Duplicate physical evidence: count once, preserve every origin

The current priority-based `deduplicateByPhysicalEvidence(...)` is replaced by explicit consolidation.

The new result shape is conceptually:

```kotlin
data class ConsolidatedScientificEvidence(
    val authorities: Set<ScientificAuthority>,
    val role: ScientificEvidenceRole,
    val physicalEvidenceId: String,
    val calibrationFingerprint: String,
    val effectiveWeight: Double,
    val provenance: Set<String>,
    val modelVersions: Set<String>,
    val predictionIds: Set<String>,
)

data class ScientificEvidenceConflict(
    val physicalEvidenceId: String,
    val authorities: Set<ScientificAuthority>,
    val reason: String,
)

data class ScientificEvidenceConsolidation(
    val accepted: List<ConsolidatedScientificEvidence>,
    val conflicts: List<ScientificEvidenceConflict>,
)
```

For several producers exposing the same physical evidence:

- the physical vote appears once;
- `effectiveWeight = max(weight)` rather than sum, so duplicate exposure cannot manufacture authority;
- `authorities` is the union of all producers that exposed it;
- provenance/model/prediction ids are retained as bounded sets for the small consolidation input;
- no producer priority is applied.

A group is a conflict instead of an accepted consolidated item when the same `physicalEvidenceId` is presented with incompatible scientific meaning, including:

- different `calibrationFingerprint` values;
- mixed `OBSERVATION` and `PREDICTION` roles.

A conflict is not silently resolved. Later Arbitration may decide how to interpret producer disagreement, but Step 123B only guarantees that the Physics entry does not hide it.

## 9. Typed K* measurement boundary

K* receives two scientifically typed measurements instead of naked Tinj doubles:

```kotlin
data class ScientificMeasurement(
    val valueMs: Double,
    val evidence: ConsolidatedScientificEvidence,
)

data class KStarScientificInput(
    val petrolOnGas: ScientificMeasurement,
    val petrolReference: ScientificMeasurement,
    val currentFactor: Double,
    val gain: PlantGain,
)
```

The field names keep the Step 123 physical objective explicit. A generic “value A/value B” API would be easier to misuse later.

## 10. K* eligibility and abstention

`KStarEstimator` accepts `KStarScientificInput` as its public entry.

The numerical equation remains:

```text
e = ln(Tpet_GNV / Tpet_ref)
theta = ln(F_current)
theta* = theta + e / g
F* = exp(theta*)
```

Before applying that equation, the estimator validates scientific eligibility.

It must abstain with `targetFactor=null`, `MagnitudeAuthority.UNKNOWN`, and a stable reason code when:

- either measurement role is `PREDICTION` → `PREDICTION_IS_NOT_OBSERVATION`;
- the two measurements have different calibration fingerprints → `CALIBRATION_FINGERPRINT_MISMATCH`;
- either consolidated evidence has zero effective weight → `NO_SCIENTIFIC_WEIGHT`;
- the exact same physical evidence id is used as both the CNG-side observation and the gasoline reference → `SELF_COMPARISON_EVIDENCE`;
- plant gain remains unknown → existing `PLANT_GAIN_UNKNOWN` behavior.

Existing positive/finite numeric preconditions remain explicit developer invariants. Step 123B does not broaden the estimator into a telemetry sanitizer because that is a different responsibility.

The producer identity itself never changes the formula. If identical eligible observations are labeled `OEM_NATIVE`, `CLASSIC_ASSISTED`, or `ADAPTIVE_SHADOW` with role `OBSERVATION`, the computed numeric K* is identical.

## 11. Adaptive Shadow rule

`ADAPTIVE_SHADOW` may participate in two distinct ways:

1. It may forward or classify an actual shared-backbone observation. In that case the role is `OBSERVATION`, the physical evidence id and calibration fingerprint must point back to the physical lineage, and it is numerically treated like any other eligible observation.
2. It may emit a model-derived value. In that case the role is `PREDICTION`, `modelVersion` and `predictionId` are mandatory, and that value is ineligible to occupy either physical measurement slot in K*.

This preserves the architectural value of Adaptive Shadow without allowing prediction leakage into observation authority.

## 12. K* output trace

`KStarEstimate` gains a compact trace object:

```kotlin
data class KStarScientificTrace(
    val authorities: Set<ScientificAuthority>,
    val petrolOnGasEvidenceId: String,
    val petrolReferenceEvidenceId: String,
    val calibrationFingerprint: String,
    val provenance: Set<String>,
)
```

Every estimate produced from scientifically eligible evidence carries this trace, including an estimate that later abstains because plant gain is unknown.

For a scientific-eligibility abstention, the result still carries enough trace to explain why it was rejected when possible.

This trace is the durable handoff for later `ClassicProposal` / `AdaptiveProposal` / `ProposalArbitration` work. Step 123B does not yet project it into Draft/UI.

## 13. Untyped bypass policy

The existing public overload:

```kotlin
KStarEstimator.estimate(
    petrolOnGasMs,
    petrolReferenceMs,
    currentFactor,
    gain,
)
```

must not remain as a public production bypass after 123B.

The implementation may keep the pure numeric calculation as a private/internal helper after scientific eligibility has passed, but callers must enter through `KStarScientificInput`.

This is deliberate. Keeping a convenient public raw-double overload would make the new type contract optional and guarantee future migration debt.

## 14. Existing consumers

`FastPhysicsGateEvaluator` is migrated to the typed API. It creates explicit synthetic observation provenance such as:

- authority: `CLASSIC_ASSISTED`;
- role: `OBSERVATION`;
- calibration fingerprint: `SYNTHETIC_FAST_PHYSICS_GATE`;
- distinct physical ids for the synthetic gasoline reference and CNG-side measurement.

This makes the deterministic gate a real consumer of the new public entry instead of preserving an untyped test-only escape hatch.

`ConditionalActuatorTargets` remains downstream of `KStarEstimate`. Its arithmetic does not change. It automatically receives a K* object that now carries traceable scientific origin.

`PhysicsOracleValidator` remains an independent numeric oracle. It is not a production K* entry and may continue to operate on raw numeric scenarios because its job is to independently validate the mathematics, not to establish live scientific provenance.

## 15. Error and conflict semantics

Expected scientific state mismatch is represented as data, not as silent coercion:

- duplicate exposure with consistent lineage → consolidate;
- duplicate exposure with incompatible lineage/role → explicit conflict;
- prediction in observation slot → K* abstention;
- calibration mismatch → K* abstention;
- unknown plant gain → K* abstention.

Programmer-contract violations such as blank required ids or non-finite construction weights fail fast at construction time.

No automatic producer fallback is allowed. For example, if an Adaptive prediction is rejected, the estimator does not silently relabel it Classic or substitute an OEM value.

## 16. Performance and RK3326 boundary

The change must not affect MP48 acquisition cadence or create per-frame heavy work.

Constraints:

- no new polling, scheduler, thread, Store, queue, or persistence path;
- no JSON in the scientific arithmetic;
- no historical scan;
- consolidation operates only on the small set of producer exposures for one scientific evidence item/decision;
- K* remains O(1);
- trace collections are bounded by the fixed producer count (three authorities) and the small input set, not drive duration;
- no claim of physical RK3326 performance is made by this step.

A source review must confirm that the diff introduces no second acquisition/runtime/writer surface.

## 17. TDD falsifiers

Implementation starts with failing tests that prove the current source violates the new contract.

Required falsifiers:

1. Same physical evidence exposed by OEM + Classic consolidates to one vote while preserving both authorities; neither authority silently wins.
2. Same physical evidence exposed as both Observation and Prediction yields an explicit consolidation conflict.
3. Same physical evidence exposed under two calibration fingerprints yields an explicit conflict.
4. K* accepts typed Classic observations and reproduces the current numeric target exactly.
5. Re-labeling identical eligible observations among OEM/Classic/Adaptive Observation leaves numeric K* unchanged.
6. `ADAPTIVE_SHADOW + PREDICTION` in either K* measurement slot causes abstention with `PREDICTION_IS_NOT_OBSERVATION`.
7. Different calibration fingerprints cause abstention with `CALIBRATION_FINGERPRINT_MISMATCH`.
8. Reusing the same physical evidence as reference and CNG observation causes `SELF_COMPARISON_EVIDENCE` abstention.
9. Zero scientific weight cannot create a target.
10. Unknown plant gain still abstains exactly as before.
11. `KStarEstimate` preserves producer/evidence/fingerprint trace.
12. `FastPhysicsGateEvaluator` compiles and passes using only the typed public K* entry.
13. No public raw-double K* entry remains after migration.
14. Existing Phase 06 physics tests remain green.

## 18. Regression surface

At minimum, verification covers:

- `ScientificEvidence` focused tests;
- `CalibrationPhysicsFoundationTest`;
- `Phase6BindingIntegrationTest`;
- `ConditionalActuatorTargetTest`;
- `FastPhysicsGateEvaluatorTest`;
- `PhysicsOracleValidationTest`;
- full relevant Physics test package when the execution environment can run it.

If a new source SHA is produced, all prior Step 123B source-audit receipts are invalid until a new exact-SHA audit run.

## 19. Audit contract

The implementer may close only at `IMPLEMENTED_AWAITING_AUDIT`.

PASS requires a fresh read-only normative audit on the frozen candidate SHA with a new `AUDIT_EPOCH_ID` and `AUDIT_RUN_ID`, followed by a distinct read-only meta-audit.

The audit must attempt to falsify at least:

- producer origin cannot alter K* math;
- Prediction cannot become Observation;
- duplicate physical evidence cannot multiply authority;
- no producer silently dominates duplicate evidence;
- no second runtime/Store/polling/writer was created;
- no untyped production K* bypass remains;
- downstream `MagnitudeAuthority` is not inflated by producer origin;
- source branch/canonical topology is fresh.

No physical RK3326 claim is required for Step 123B. Any hardware-dependent performance or Adaptive physical-accuracy claim remains deferred to the appropriate later gate.

## 20. Invalidation events

This design or a resulting PASS becomes stale when any of the following changes materially:

- `ScientificAuthority` producer semantics;
- `ScientificEvidenceRole` semantics;
- ADP-001 Observation/Prediction or single-backbone contract;
- K* equation or plant-gain authority model;
- Calibration Identity/fingerprint semantics used by the evidence boundary;
- new producer authority is added;
- Proposal Arbitration begins consuming the trace and discovers a missing lineage field;
- source changes in the typed evidence/K* call path after audit.

## 21. Decision summary

The future-proof choice is to **make typed evidence mandatory at the K* boundary now**, using the producer enum that already exists, rather than adding metadata after the fact or keeping an untyped compatibility escape hatch.

One physical observation counts once. Every producer origin remains visible. Observation and Prediction are different types of scientific claims. K* mathematics remains producer-neutral. The single runtime and single writer remain untouched.

That gives later Adaptive Arbitration a stable provenance-bearing Physics result without forcing Phase 06 to replace the estimator again.