# OMEGAS-BLUE-ALGO-VERIFY-001 — Algorithm chain verification + didactic refinement

- Lineage: closed convergence issue `#16` + closed recovery epic `#18`
- Branch authority: `work/omegas-blue-causal-engine`
- Starting SHA: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Latest verified software SHA: `ff8dc6f6b1d2d2f236e746565432cebcca327488`
- Status: SOFTWARE_GATE_VERIFIED / OWNER_AUTHORIZED_APK_GATE_PENDING
- Artifact gate: owner-authorized on 2026-09-08 for manual APK artifact generation only

## Purpose
Verify the real runtime chain `MP48 evidence -> learning -> petrol/CNG equivalence -> measured deviation -> causal actuator gain -> Blue proposal -> Learning/Curve K presentation -> manual review` and reduce operator confusion without creating a second scientific engine.

## Historical evidence that remains valid
Canonical `OMEGAS Blue CI` run `34172162423` on the starting SHA completed/success: FAST success, JVM/unit+lint success, APK artifact skipped, no run artifacts and no releases. This proves only those executed gates; it does not prove end-to-end algorithm integration.

## Current exact-SHA evidence
Canonical `OMEGAS Blue CI` run `34249955077` on `ff8dc6f6b1d2d2f236e746565432cebcca327488` completed/success after a focused test-defect fix aligned the OBD/GNV-only integration contract with the current production path: `BlueCalibrationCoordinator` calls `BlueMapKAddressing.presentationFields(comparison)`, and `presentationFields` calls `cell(comparison)` using current GNV `petrolOnCngMs`.

Remote evidence from run `34249955077`:
- `FAST contracts`: success.
- FAST log: `SOURCE_SHA=ff8dc6f6b1d2d2f236e746565432cebcca327488` and `QUALITY_GATE_FAST=PASS python=24 node=24`.
- `FULL JVM lint`: success.
- FULL log: `./gradlew testDebugUnitTest lintDebug -PomegasAbis=armeabi-v7a --no-daemon --stacktrace`, `BUILD SUCCESSFUL in 1m 48s`, and `READY_FOR_APK_GENERATION=true`.
- Push-CI APK job: `Owner-authorized APK artifact` skipped.
- Push-CI artifacts: empty.
- Push-CI APK evidence: `APK_GENERATED=false`.

Focused local reproduction before publication used an exact ZIP snapshot of `011e6e685bf8e63deff351e34a918013eaf13813`: the original contract failed on the stale assertion `BlueMapKAddressing.cell(comparison)`, then passed after replacing it with `BlueMapKAddressing.presentationFields(comparison)`. The adjusted FAST suite passed with `QUALITY_GATE_FAST=PASS python=24 node=24`.

## New findings at the starting SHA
1. `BlueCalibrationCoordinator.proposalJson()` and its nested proposal path call `BlueAutoCalAdapter.proposalJson(..., gain = null)`.
2. `BlueAutoCalAdapter` only produces `correctionMultiplier` when a valid `BlueActuatorGain` is supplied; otherwise state is `MEASURE_ACTUATOR_GAIN`.
3. `BlueCausalEngine` and `BlueAutoCalAdapter.learnGain()` contain isolated causal-gain math, but acquisition/storage/use of gain by the runtime coordinator is not yet demonstrated.
4. Learning UI tests now cover that missing `correctionMultiplier` is never rendered as valid zero, an explicit current-GNV `mapKCell` is rendered, and a nested proposal with explicit availability plus numeric target is visible.
5. `mapKCell`, `row`, `column`, and `cellKey` are emitted through `BlueMapKAddressing.presentationFields(comparison)`, and the production cell function uses current GNV `petrolOnCngMs` instead of gasoline-reference `petrolTargetMs`.
6. Several existing contracts remain static source checks; green evidence proves those checks and the exercised UI behavior, not physical vehicle performance.

## Invariants
- `BlueCausalEngine` is the only correction-math authority.
- MP48 is physical/calibration truth.
- OBD is read-only GNV STFT witness; LTFT does not vote; conflict cannot raise confidence.
- Map K address uses current GNV RPM x current GNV Petrol Inj.
- No automatic ECU writer. Manual mutation remains review -> confirm -> ACK -> readback.
- RPM is not an arbitrary write-authorization gate.
- Do not change MP48 protocol for convenience.

## Required behavior tests
- Valid physical petrol/CNG pair shows the measured deviation: covered by current Learning tests.
- Missing equivalent pair explains what is missing: covered by current Learning tests.
- Missing causal gain never fabricates a K target: covered by current Learning tests.
- A valid observed before/write-readback/after sequence can produce causal gain and then a Blue proposal when attribution requirements are satisfied: pending runtime attribution policy.
- Curve/Map revision mismatch and incompatible physical region cannot contaminate causal evidence: pending full causal gain policy.
- Agreeing OBD witness may raise confidence; conflict may not; LTFT remains absent: covered by current contracts.
- Map K location is the current GNV region, even when gasoline-reference Petrol Inj. differs: covered by current contracts and UI test.
- Selection/preview never writes ECU; relative delta and absolute assignment remain distinct: covered by current Curve K contracts.
- Missing values render as unknown, never as valid zero: covered by current Learning UI test.
- The operator can see in one place: measured result, what is missing, whether a proposal exists, and the next manual action: covered by current didactic UI contracts.

## Causal-attribution guardrail
Do not substitute a constant gain, `1.0`, guessed K percentage, or automatic calibration. Full runtime integration of causal gain remains `PENDING` until the policy specifies how to attribute `K_effective` to a confirmed manual intervention across before/after evidence, especially when the intervention changes multiple curve points or multiple Map K cells. Without that specified attribution rule, production code must continue to surface measured deviation and missing-gain state instead of inventing a proposal target.

## Exit gate
Focused RED -> minimal fix -> focused GREEN for each confirmed defect; targeted negative scenarios; FAST/JVM/lint on final exact SHA after production or test changes; remote read-back of code/docs/status; owner-authorized manual APK artifact gate when explicitly requested. Physical vehicle validation remains explicitly NOT VALIDATED by this work unit.
