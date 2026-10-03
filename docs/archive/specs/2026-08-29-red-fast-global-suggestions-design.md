# OMEGAS V8.0 RED — Fast Global Suggestions Design

## Goal

Accelerate useful gasoline↔CNG calibration guidance while preserving the RED runtime budget and manual ECU-write contract.

## Scope

- Fix AutoCal/Curve tab controller interoperability.
- Keep the existing RPM×MAP comparison and downstream RPM×Petrol Inj. cell mapping.
- Separate the estimated ideal correction from the bounded step proposed for the current review.
- Size the step using independent visit support, uncertainty and deadband.
- Improve global suggestion explanation without adding polling, stores, writers or automatic ECU actions.

## Design

The advisor continues to estimate the relative error between petrol reference and petrol-on-CNG. Each global point publishes:

- `idealDeltaPercent`: full estimated correction direction/magnitude;
- `suggestedDeltaPercent`: bounded manual step for the current review;
- `stepPolicy`: `INDEPENDENCE_BOUNDED`;
- `estimatedResidualAfterPercent`: expected remaining error after that bounded step;
- independent visit count, uncertainty and confidence already present.

A single independent visit can surface a useful proposal but its correction fraction is capped at 55%. Two or three coherent visits may reach 75%; four or more may reach the existing 90% ceiling. Contradictory evidence and errors covered by uncertainty remain observational.

The adapter continues to persist only `suggestedDeltaPercent` into a reviewable Curva K change. Its rationale explains ideal error, bounded step and expected residual. No value is written until the existing manual review, confirmation, ACK and readback path completes.

## Performance and safety

The change adds only constant-time arithmetic to the existing per-point publication. It creates no new thread, polling loop, serialization path, historical replay or scientific store. UI rendering remains revision-driven.

## Verification

- Runtime regression for AutoCal tab ownership.
- Advisor unit tests for ideal-versus-step semantics and independent-visit caps.
- Adapter unit test proving the bounded step, not the ideal target, reaches the manual proposal.
- Focused UI and JVM tests, then the existing fast quality gate.
- Android build only after affected and broad checks are green.
