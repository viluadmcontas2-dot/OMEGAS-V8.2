# RED Blend — MAP_K Sensitivity Gate

Date: 2026-08-30
WorkUnit: `RED-BLEND-001`
Issue: `#11`
Branch: `work/red-v82-science-blend`

## Scope

This checkpoint proves the sensitivity layer fails closed when governed real causal outcomes are unavailable. It does not estimate physical MAP_K sensitivity and does not produce P(improve) or actionability.

## TDD evidence

### RED

- workflow SHA: `fde02c8a5d96989138840918decd4b31838184fa`
- Actions run: `33329391641`
- all prior RED/science/causal steps: PASS
- sensitivity step failed exactly with `ModuleNotFoundError: No module named 'lab.red_blend.sensitivity_science'`

### GREEN

- implementation SHA: `96532e6c53d289007b570c9a2e2f014f8a090311`
- Actions run: `33329495482`
- all workflow steps: PASS
- inherited RED fast contracts: PASS
- causal proof and real causal abstention: PASS
- sensitivity fail-closed tests: PASS

## Scientific result

The governed intervention fixture contains 133 confirmed cell events in 11 manual adjustments, but the real causal audit has zero comparable intervention outcomes because no governed common-timebase bridge is currently proven.

Therefore:

- `independent_effect_count=0`
- `sensitivity=null`
- `p_improve=null`
- `actionable=false`
- status: `BLOCKED_BY_INSUFFICIENT_CAUSAL_OUTCOME_SUPPORT`

Neither 133 cell writes nor 11 adjustment identities are allowed to substitute for independent outcome effects. This prevents pseudo-replication from manufacturing a sensitivity estimate.

## Gate state

`G9_SENSITIVITY=FAIL_CLOSED_REAL_CAUSAL_SUPPORT_INSUFFICIENT`

`SENSITIVITY_PROVEN=false`

`P_IMPROVE_PROVEN=false`

`PRODUCTION_RUNTIME_CHANGED=false`

`ECU_AUTO_WRITE=false`

`MANUAL_CONFIRM_ACK_READBACK_INVARIANT_PRESERVED=true`

## Next

Predictive risk/coverage calibration, OOD/falsification and performance regression may continue independently. P(improve) remains blocked until causal outcome support exists.
