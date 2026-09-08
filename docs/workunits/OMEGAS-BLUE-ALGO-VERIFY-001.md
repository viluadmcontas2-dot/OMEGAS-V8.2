# OMEGAS-BLUE-ALGO-VERIFY-001 — Algorithm chain verification + didactic refinement

- Lineage: closed convergence issue `#16` + closed recovery epic `#18`
- Branch authority: `work/omegas-blue-causal-engine`
- Starting SHA: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Status: ACTIVE
- Artifact gate: BLOCKED; no APK/assemble/release/upload authorized

## Purpose
Verify the real runtime chain `MP48 evidence -> learning -> petrol/CNG equivalence -> measured deviation -> causal actuator gain -> Blue proposal -> Learning/Curve K presentation -> manual review` and reduce operator confusion without creating a second scientific engine.

## Historical evidence that remains valid
Canonical `OMEGAS Blue CI` run `34172162423` on the starting SHA completed/success: FAST success, JVM/unit+lint success, APK artifact skipped, no run artifacts and no releases. This proves only those executed gates; it does not prove end-to-end algorithm integration.

## New findings at the starting SHA
1. `BlueCalibrationCoordinator.proposalJson()` and its nested proposal path call `BlueAutoCalAdapter.proposalJson(..., gain = null)`.
2. `BlueAutoCalAdapter` only produces `correctionMultiplier` when a valid `BlueActuatorGain` is supplied; otherwise state is `MEASURE_ACTUATOR_GAIN`.
3. `BlueCausalEngine` and `BlueAutoCalAdapter.learnGain()` contain isolated causal-gain math, but acquisition/storage/use of gain by the runtime coordinator is not yet demonstrated.
4. `learning.js` requires `proposal.available === true` plus numeric `correctionMultiplier`; nested calibration-state proposal currently needs behavioral verification because the coordinator nested payload does not add `available` itself.
5. UI numeric helper must be tested for `null`/missing inputs so absence never renders as valid zero.
6. `mapKCell` is emitted by Blue coordinator, while Learning cell indexing reads row/column variants; the actual normalization path must be traced and behaviorally proven using gasoline and GNV with different Petrol Inj.
7. Several existing contracts are static source checks; grep green is not end-to-end proof.

## Invariants
- `BlueCausalEngine` is the only correction-math authority.
- MP48 is physical/calibration truth.
- OBD is read-only GNV STFT witness; LTFT does not vote; conflict cannot raise confidence.
- Map K address uses current GNV RPM x current GNV Petrol Inj.
- No automatic ECU writer. Manual mutation remains review -> confirm -> ACK -> readback.
- RPM is not an arbitrary write-authorization gate.
- Do not change MP48 protocol for convenience.

## Required behavior tests
- Valid physical petrol/CNG pair shows the measured deviation.
- Missing equivalent pair explains what is missing.
- Missing causal gain never fabricates a K target.
- A valid observed before/write-readback/after sequence can produce causal gain and then a Blue proposal when attribution requirements are satisfied.
- Curve/Map revision mismatch and incompatible physical region cannot contaminate causal evidence.
- Agreeing OBD witness may raise confidence; conflict may not; LTFT remains absent.
- Map K location is the current GNV region, even when gasoline-reference Petrol Inj. differs.
- Selection/preview never writes ECU; relative delta and absolute assignment remain distinct.
- Missing values render as unknown, never as valid zero.
- The operator can see in one place: measured result, what is missing, whether a proposal exists, and the next manual action.

## Causal-attribution guardrail
Do not substitute a constant gain, `1.0`, guessed K percentage, or automatic calibration. Before implementing gain persistence/use, trace every confirmed-write caller and define which before/after comparison belongs to the same physical region and which calibration revision transition makes that intervention attributable. If that policy is not already specified by code/spec evidence, surface the exact policy decision required instead of inventing it.

## Exit gate
Focused RED -> minimal fix -> focused GREEN for each confirmed defect; targeted negative scenarios; FAST/JVM/lint on final exact SHA only after production changes; remote read-back of code/docs/status. Physical vehicle validation remains explicitly NOT VALIDATED by this work unit.
