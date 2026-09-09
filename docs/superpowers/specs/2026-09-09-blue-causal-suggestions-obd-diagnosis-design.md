# OMEGAS Blue causal suggestions and OBD diagnosis design

Issue: #28  
Approved by owner: 2026-09-09  
Baseline: `70c1a701818b212bf5f89743ab7241dbd8e7219e`

## Goal

Convert measured petrol/CNG evidence into exact, manually reviewable Curve K or Map K changes only after the vehicle itself has demonstrated an identifiable actuator response. Make an OBD connection failure observable without guessing its physical cause.

## Non-negotiable invariants

- `BlueCausalEngine` remains the only correction-math authority.
- MP48 telemetry and confirmed readback remain physical truth.
- No automatic ECU write.
- No constant gain, gain fallback `1.0`, invented `K_effective`, or multi-actuator attribution.
- Manual mutation remains prepare -> review -> confirm -> ACK -> readback.
- OBD is optional and read-only. GNV STFT may change confidence only. LTFT has no correction vote.
- Physical success and fuel economy remain unproven without vehicle validation.

## Confirmed baseline defects

1. `BlueCalibrationCoordinator` calls `proposalJson(comparison, gain = null)`; runtime cannot consume `actuatorGain`.
2. Curve K renders measured comparisons but receives no exact `curveChanges`.
3. Region visits are cloned into multiple identical `FuelEvidence` values, allowing audit count to change scientific weight.
4. Reconciliation replaces evidence time with wall-clock time, so an older comparison can appear latest.
5. OBD native status contains stage/code/detail, while the setup UI does not render the complete failure.

## Architecture

### 1. Evidence identity and time

One Learning region produces one scientific `FuelEvidence`. Visit IDs remain audit metadata and never duplicate the regional estimate. `FuelComparison.createdAtMs` equals the CNG evidence timestamp. Reference provenance carries the selected petrol evidence IDs, support count, distance, and spread.

### 2. Identifiable intervention ledger

A `BlueCausalIntervention` is prepared before a manual write and confirmed only after ACK plus full readback.

Supported attribution:
- `CURVE_POINT`: exactly one Curve K point changed.
- `MAP_CELL`: exactly one writable Map K cell changed.

Any zero-change, multi-point, multi-cell, mixed, partial, failed, session-mismatched, or missing-readback operation is `NOT_IDENTIFIABLE` and cannot produce gain.

The ledger records intervention ID, type/address, before/after K, old/new calibration state, before comparison, confirmation time, and readback proof. It never stores a synthetic combined K.

### 3. Before/after matching and gain

After confirmation, only CNG evidence from the new calibration state can close the intervention. The after comparison must match the before comparison within the existing RPM/MAP physical windows and the same addressed actuator region.

The engine calculates:
`gain = -(afterErrorLog - beforeErrorLog) / ln(afterK / beforeK)`.

A gain is accepted only when finite, positive, inside the existing policy bounds, and based on a non-trivial K step. Invalid direction, unrelated region, stale state, or insufficient evidence yields an explicit abstention reason.

The first implementation learns one traceable gain from one isolated confirmed intervention. It does not pool unrelated interventions. Later robust pooling requires at least three compatible gains and a separate policy change.

### 4. Exact proposal

A proposal is emitted only when an active comparison has an applicable accepted gain.

- Curve K: choose the nearest confirmed physical Curve K axis point to `petrolOnCngMs`; target factor is current factor multiplied by the bounded correction multiplier and normalized through `KFactorProtocol`.
- Map K: use `BlueMapKAddressing` from current GNV RPM and current GNV Petrol Inj.; target integer is current cell multiplied by the bounded correction multiplier and clamped to `100..180`.
- The first release never emits both actuators for one comparison.
- The proposal includes one exact `curveChanges` or `mapChanges` item, rationale, evidence IDs, gain provenance, confidence, and `automaticWrite=false`.

Global/local decomposition remains conservative: an accepted Curve gain produces Curve proposals; an accepted Map-cell gain produces only that Map cell proposal. Automatic promotion from local evidence to a global curve is not introduced without cross-region empirical evidence.

### 5. Consumption

Learning displays measurement separately from action. Curve K consumes only `curveChanges`; Map K consumes only `mapChanges`. Navigation may prepare a preview, but never starts a writer. Preview is re-normalized by Kotlin against current readback before human review.

### 6. OBD diagnosis

The setup UI renders `connectionStage`, `connectionErrorCode`, `connectionDetail`, `retryable`, last command, and last error. Retry invokes the existing bounded connection path.

Transport behavior is not changed until real evidence identifies PERMISSION, RFCOMM, ELM_INIT, PROTOCOL, or STFT failure. This separates “diagnosis available” from “adapter physically fixed.”

## Acceptance examples

1. Petrol 4.00 ms and CNG 4.40 ms produces +10% measured deviation and no target before gain.
2. One Curve point changes 1.00 -> 1.05 with confirmed readback; equivalent after evidence moves error from +10% to +5%; accepted gain becomes available and a later same-region comparison emits one exact Curve change.
3. The same data with two changed Curve points abstains with `MULTI_ACTUATOR_NOT_IDENTIFIABLE`.
4. A confirmed intervention followed by evidence in another RPM/MAP region does not learn gain.
5. Re-ingesting a region with 20 visit IDs still creates one evidence item and one comparison.
6. Two comparisons preserve their physical timestamps and latest means newest CNG observation.
7. OBD SUPPORTS may increase confidence; CONFLICTS, LTFT, or no OBD cannot change target math.
8. An RFCOMM/ELM/protocol/STFT error is visible with code, detail, and retry action.
9. No test or UI path writes ECU without the existing manual confirmation, ACK, and readback chain.

## Validation boundary

JVM and browser tests can prove math, state transitions, payloads, UI consumption, writer isolation and build integrity. Only the actual multimedia, ELM adapter and vehicle can prove Bluetooth/ELM connectivity, live STFT behavior, drivability or economy.
