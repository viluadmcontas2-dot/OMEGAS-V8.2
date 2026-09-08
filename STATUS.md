# Status — OMEGAS V8.2 Blue

- Branch: `work/omegas-blue-causal-engine`
- Current state: `ALGORITHM_INTEGRATION_VERIFICATION_ACTIVE`
- Historical recovery lineage: `#16` + `#18` (closed)
- Current Work Unit: `OMEGAS-BLUE-ALGO-VERIFY-001`
- Primary spec lineage: `specs/001-blue-runtime-convergence/` + `specs/003-blue-system-recovery/`
- Starting SHA for this work: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Mathematical authority: `BlueCausalEngine`
- Automatic ECU write: `FALSE`
- APK/assemble/release/upload: `BLOCKED`

## Historical evidence that remains valid
Canonical `OMEGAS Blue CI` run `34172162423` on `022945165a590781f4245c5ca0f9a8b51cbbc9f3` completed/success. FAST succeeded (`QUALITY_GATE_FAST=PASS python=24 node=23`), FULL JVM/unit + lint succeeded, owner-authorized APK artifact was skipped, workflow artifacts were empty and releases were empty.

This is evidence for the tests that ran. It is NOT evidence that the complete causal chain is functionally integrated end-to-end.

## New verified findings
1. `BlueCalibrationCoordinator.proposalJson()` calls `autoCal.proposalJson(comparison, gain = null)`.
2. `BlueCalibrationCoordinator.proposalJsonLocked()` also calls `autoCal.proposalJson(..., gain = null)`.
3. `BlueAutoCalAdapter` exposes `learnGain()` and only emits a numeric correction multiplier when a valid `BlueActuatorGain` is supplied.
4. `BlueCausalEngine` contains the before/after actuator-gain math, but the runtime acquisition/storage/use of that gain has not yet been behaviorally proven.
5. `learning.js` requires `proposal.available === true` and numeric `correctionMultiplier`; the nested `calibrationState.proposal` payload still requires behavioral verification.
6. `learning.js` numeric conversion can coerce `null` through `Number(null)`; missing-value rendering must be proven so absence never becomes valid zero.
7. Blue publishes `mapKCell`; Learning indexes row/column variants. The transformation into the displayed GNV cell is not yet proven end-to-end.

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
- Gain acquisition/persistence/attribution into runtime proposal: `PENDING`
- Nested proposal payload shown correctly in Learning: `PENDING`
- Missing numeric values never shown as valid zero: `PENDING`
- GNV Map K cell routing with distinct petrol/GNV Petrol Inj.: `PENDING`
- End-to-end behavioral chain including negative scenarios: `PENDING`
- Physical vehicle validation: `NOT VALIDATED`

## Exact next step
Create a focused integration RED test that reproduces the highest-impact gap: before evidence -> confirmed manual write/readback -> after evidence -> causal gain -> proposal. Do not implement a guessed attribution policy. If the repository does not already specify how a before/after comparison is bound to the same physical region and calibration transition, record the concrete policy decision required before production mutation.

## Authorization boundary
No APK, `assembleDebug`, release, upload, installation or distribution is authorized. GitHub remote remains technical authority; local snapshots are disposable test surfaces only.
