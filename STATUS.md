# Status — OMEGAS V8.2 Blue

- Branch: `work/omegas-blue-causal-engine`
- Current state: `READY_FOR_OWNER_AUTHORIZED_APK_GATE`
- Historical recovery lineage: `#16` + `#18` (closed)
- Current Work Unit: `OMEGAS-BLUE-ALGO-VERIFY-001`
- Primary spec lineage: `specs/001-blue-runtime-convergence/` + `specs/003-blue-system-recovery/`
- Starting SHA for this work: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Latest verified software SHA: `ff8dc6f6b1d2d2f236e746565432cebcca327488`
- Mathematical authority: `BlueCausalEngine`
- Automatic ECU write: `FALSE`
- APK/assemble/release/upload: `OWNER_AUTHORIZED_FOR_MANUAL_ARTIFACT_GATE_ON_2026-09-08`

## Historical evidence that remains valid
Canonical `OMEGAS Blue CI` run `34172162423` on `022945165a590781f4245c5ca0f9a8b51cbbc9f3` completed/success. FAST succeeded (`QUALITY_GATE_FAST=PASS python=24 node=23`), FULL JVM/unit + lint succeeded, owner-authorized APK artifact was skipped, workflow artifacts were empty and releases were empty.

This is evidence for the tests that ran. It is NOT evidence that the complete causal chain is functionally integrated end-to-end.

## Current exact-SHA evidence
Canonical `OMEGAS Blue CI` run `34249955077` on `ff8dc6f6b1d2d2f236e746565432cebcca327488` completed/success after the FAST contract was reconciled to the current `BlueMapKAddressing.presentationFields(comparison)` integration.

Remote evidence from run `34249955077`:
- `FAST contracts`: success.
- FAST log: `SOURCE_SHA=ff8dc6f6b1d2d2f236e746565432cebcca327488` and `QUALITY_GATE_FAST=PASS python=24 node=24`.
- `FULL JVM lint`: success.
- FULL log: `./gradlew testDebugUnitTest lintDebug -PomegasAbis=armeabi-v7a --no-daemon --stacktrace`, `BUILD SUCCESSFUL in 1m 48s`, and `READY_FOR_APK_GENERATION=true`.
- Push-CI APK job: `Owner-authorized APK artifact` skipped.
- Push-CI artifacts: empty.
- Push-CI APK evidence: `APK_GENERATED=false`.

A separate owner instruction on 2026-09-08 authorized continuing to the manual APK artifact gate. That gate must still preserve quality evidence, exact SHA provenance, and no physical vehicle claim.

## New verified findings
1. `BlueCalibrationCoordinator.proposalJson()` calls `autoCal.proposalJson(comparison, gain = null)`.
2. `BlueCalibrationCoordinator.proposalJsonLocked()` also calls `autoCal.proposalJson(..., gain = null)`.
3. `BlueAutoCalAdapter` exposes `learnGain()` and only emits a numeric correction multiplier when a valid `BlueActuatorGain` is supplied.
4. `BlueCausalEngine` contains the before/after actuator-gain math, but the runtime acquisition/storage/use of that gain has not yet been behaviorally proven.
5. Learning UI has tests proving missing `correctionMultiplier` is not rendered as valid zero, an explicit current-GNV `mapKCell` is rendered, and a nested proposal with explicit availability plus numeric target is visible.
6. Blue publishes `mapKCell`, `row`, `column`, and `cellKey` from `BlueMapKAddressing.presentationFields(comparison)`, whose cell calculation uses current GNV `petrolOnCngMs` rather than gasoline-reference `petrolTargetMs`.
7. Full causal gain integration remains pending until a policy specifies how to attribute `K_effective` across confirmed interventions, especially multi-point or multi-cell manual changes.

## Previously closed work that is not being reopened by default
- #17 runtime/WebView recovery
- #19 Learning evidence semantics
- #20 telemetry/background/overlay
- #21 Tools disclosure/focus behavior
- #22 Curve K selection/batch semantics
- #23 OBD x MP48 witness contract
- #16 single-authority convergence
- #18 recovery epic

Their historical GREEN evidence remains useful. A closed issue is only revisited if a new behavioral regression directly intersects this Work Unit.

## Current gates
- FAST contracts on latest verified software SHA: `PASS`
- JVM/unit on latest verified software SHA: `PASS`
- lint on latest verified software SHA: `PASS`
- Push-CI automatic APK generation: `NOT EXECUTED / SKIPPED`
- Owner-authorized manual APK artifact gate: `AUTHORIZED 2026-09-08 / PENDING EXECUTION`
- Gain acquisition/persistence/attribution into runtime proposal: `PENDING`
- Runtime causal gain policy for multi-point/multi-cell `K_effective`: `PENDING`
- Physical vehicle validation: `NOT VALIDATED`

## Exact next step
Run the owner-authorized manual artifact gate against the current branch head after read-back confirms the documented SHA remains the branch head. Do not invent gain, do not use `1.0`, do not alter MP48, and do not create an automatic writer.

## Authorization boundary
APK artifact generation is now authorized only through the manual owner-authorized artifact gate. Installation, distribution, release upload, and physical vehicle validation remain separate and must not be claimed by this work unit.
