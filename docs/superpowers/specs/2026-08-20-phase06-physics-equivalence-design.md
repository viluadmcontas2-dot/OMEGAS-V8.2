# Phase 06 Physics & Equivalence Design

## Goal
Close OMEGAS V8.2 Phase 06 (123–146 plus A/B extensions) by making calibration physics explicit, uncertainty-aware, evidence-bound, and safe for downstream Predictor/Suggestion consumers.

## Constraints
- Preserve one ECU/serial authority and manual-only write authority.
- UNKNOWN is first-class and never coerced into zero or fabricated precision.
- Configuration values, static oracle candidates, and live-effective factors are distinct types/provenance.
- Bilinear/trilinear projection is a LOCAL_MODEL, never claimed as ECU interpolation.
- TargetEstimator, ActuatorAllocator, and StepPolicy are separate authorities.
- Advisor legacy 45–90% is STEP_POLICY_BASELINE / POLICY_ONLY and never the physical target label.
- Hardware-only claims remain unasserted; this phase closes source/scientific contracts only.
- Verification is local/ephemeral-first. GitHub Actions are not used.

## Architecture
1. `CalibrationPhysicsFoundation` owns evidence matrix, factor knownness/provenance, deadtime semantics, effective actuation state, `MagnitudeAuthority`, `ExpectedEffect`, mechanism types, and K*/gain primitives.
2. `Phase6OwnerBindings` maps current OMEGAS producers/advisors into the physics types without introducing another scientific producer.
3. A dedicated classification layer converts residual evidence + context into `MAP_LOCAL`, `CURVE_MUL_ACT`, `ENVIRONMENTAL_DIAGNOSTIC`, `NO_ACTION`, or `UNKNOWN` with reason codes and explicit abstention.
4. An uncertainty/oracle layer performs deterministic bootstrap/Monte-Carlo style offline checks and exposes compact runtime summaries; runtime logic may be analytic only where it reproduces the oracle sufficiently.
5. Downstream Suggestion/Draft-facing payloads serialize `MagnitudeAuthority`, assumptions, bounds, mechanism, and falsifier so policy and physics cannot be silently conflated.
6. Evidence invalidation is dependency-scoped: new reverse-engineering evidence stales only dependent physics rules.

## Data flow
`Learning residual/context -> CalibrationPhysicsContext -> TargetEstimator -> ActuatorAllocator -> StepPolicy -> ExpectedEffect -> Suggestion projection`.

No stage is allowed to manufacture a numeric target if its required gain/deadtime/context is UNKNOWN. Environmental confounding increases uncertainty or returns a diagnostic mechanism rather than defaulting to K correction.

## Error handling
- Missing/stale deadtime: no active-pulse arithmetic; magnitude requiring it becomes UNKNOWN or abstains.
- Static K2/K4 candidate: may decode fixture values but cannot be promoted to LIVE_VALIDATED.
- K3: remains UNKNOWN until independent evidence exists.
- Broad/local residual without compatible mechanism support: INCONCLUSIVE/NO_ACTION, never automatic Map/Curve allocation.
- Contradictory evidence: uncertainty widens and action may abstain.

## Testing
TDD per coherent unit. Focused JVM tests cover: evidence matrix completeness, known/unknown deadtime, K1/MUL_ACT scaling/direction, static-candidate vs live authority, environmental context distinction, policy-vs-target separation, localized/broad/environmental/inconclusive classification, K* sign/gain/interval behavior, uncertainty coverage properties, evidence invalidation, and Suggestion serialization. Final verification uses an ephemeral/local checkout of the exact remote SHA when a capable runtime is available; otherwise the phase remains `IMPLEMENTED_AWAITING_AUDIT` until equivalent fresh verification can be produced without GitHub Actions.
