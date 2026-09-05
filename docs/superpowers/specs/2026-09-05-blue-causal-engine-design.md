# OMEGAS Blue — Single Causal Engine Design

**Issue:** #16  
**Base:** `hotfix/v8.0-red-performance`  
**Branch:** `work/omegas-blue-causal-engine`

## Goal
Build OMEGAS Blue directly from OMEGAS V8.0 RED, replacing overlapping learning/calibration motors with one causal equivalence engine and making Auto-Cal consume only that engine.

## Authority
- Raw MP48 telemetry and confirmed ECU readback are physical authority.
- Historical sessions are evidence, never runtime authority.
- No merge of `main`, V8.2, or science branches as a whole.

## Single-engine invariant
Exactly one runtime component owns equivalence learning and correction intent: `BlueCausalEngine`.

It owns:
1. petrol-reference acquisition from short stable microbursts;
2. CNG-vs-petrol equivalence error;
3. calibration-state boundaries whenever Curve K or Map K changes;
4. actuator-response learning from before/after interventions;
5. decomposition into Curve K global component and Map K local residual;
6. confidence based on evidence quality and causal response, not visit count.

Learn, Predictor, Advisor and Auto-Cal consume the same immutable `BlueCausalSnapshot` / `BlueCorrectionProposal`. No second predictor or independent correction math may remain reachable in runtime.

## Petrol reference
A valid petrol reference is created from a short stable microburst, not repeated visits. RPM and MAP define the comparable physical operating condition. A burst is rejected across fuel transitions, telemetry gaps, cutoff, calibration writes, implausible frames or instability.

## CNG error
For comparable condition `c` in calibration state `S`:

`e(S,c) = ln(Petrol_CNG(S,c) / PetrolRef(c))`.

The error is observed directly from telemetry, never inferred from old app summaries.

## Calibration state
A calibration state is the exact Curve K + Map K state active at observation time. Any confirmed write + readback creates a new state boundary. Telemetry from different calibration states is never pooled as if one calibration.

## Actuator identification
For a state transition caused by a known K change:

`gain = -Δe / Δln(K_effective)`.

The engine learns gain from real interventions. It must not hardcode 1.0 or historical 0.7 as universal truth. Gain estimates are bounded and uncertainty-aware.

## Global/local split
- Curve K: smooth/global correction as a function of petrol injection time.
- Map K: only systematic residual after Curve K contribution, addressed by RPM + petrol-injection map geometry.
- RPM remains mandatory for physical Map K cell location even when it adds little to petrol-reference prediction.

## No-legacy rule
- No runtime fallback to visit-count confidence, legacy predictor interpolation, legacy independent advisor correction math, or legacy Auto-Cal correction math.
- Existing code may survive only as a pure utility with no independent decision authority.
- Any class computing a competing correction target/confidence is removed or disconnected in the same migration.

## Auto-Cal fix
Auto-Cal becomes a consumer/orchestrator only:
- reads the current `BlueCausalSnapshot`;
- presents the single-engine proposal;
- prepares a manual Curve K/Map K transaction;
- preserves confirm → ACK → readback;
- reports confirmed state transition back to the engine so intervention becomes causal evidence;
- never runs its own residual planner or K-target formula.

## Safety
- No automatic ECU write.
- Prepared Map K targets remain within RED policy `100..180`.
- Every write requires human confirmation, ACK and readback.
- Failed/divergent readback invalidates the causal transition.

## Verification
1. Structural test: exactly one runtime decision authority.
2. Unit tests: petrol microburst, state boundaries, CNG error, gain identification, global/local split.
3. Auto-Cal contract tests proving exclusive delegation to Blue engine.
4. Regression suite for RED safety and ECU readback.
5. Historical replay tests using causal-event/pair corpus.
6. GitHub Actions remote PASS on exact Blue SHA before software-complete claim.
7. Vehicle validation remains required before claiming real fuel economy.
