# OMEGAS Blue Constitution

## 1. Repo-first authority
The Git repository is the only mutable technical authority. Specs, plans, tasks, code, tests and exact-SHA evidence live here. Chat, Drive and historical sessions are evidence/input, never runtime authority.

## 2. One causal engine
`BlueCausalEngine` is the only runtime authority for petrol reference, CNG equivalence error, causal actuator gain, global Curve K correction intent and local Map K residual intent. No legacy predictor, advisor, AutoMatch, V7 equivalence runtime, visit-count confidence engine or independent Auto-Cal math may remain reachable.

## 3. Physical truth
Raw MP48 telemetry and confirmed ECU readback are physical truth. Petrol reference is learned from short stable microbursts. RPM×MAP identifies comparable operating condition; RPM+petrol-ms locates Map K geometry. Evidence from different calibration states is never pooled as one state.

## 4. Calibration write safety
No automatic ECU write. Every mutation is human-reviewed and follows prepare → confirm → write → ACK → readback. Service, USB, ECU readiness and fresh telemetry are legitimate write gates. **RPM value is never a write-authorization gate** for Curve K, Map K or Auto-Cal proposals: writes must not require idle or RPM below 1200.

## 5. Durable sessions
A logical driving session may contain multiple USB connection segments. A transient disconnect/reconnect must not create a new retained session. Sessions are classified `PROBE`, `VALID` or `PROTECTED`; tiny probes never evict useful sessions. Default retained VALID/PROTECTED sessions is 30 and configurable minimum is 20. Sessions containing confirmed calibration/readback or explicit protection are never auto-pruned.

Live recording uses a fast private spool. Qualified closed sessions are promoted to a user-controlled OMEGAS session vault, preferably a persisted Storage Access Framework tree chosen once by the operator (for example `Documents/OMEGAS/Sessions`). Loss of vault permission must never lose the private spool.

## 6. Didactic UI
Learning separates measurement from action. Primary evidence surfaces are Gasolina, GNV and Desvio. A correction proposal is a separate Blue output; if causal gain is unavailable, the UI says why instead of fabricating a target. Cell detail prioritizes location, petrol reference, CNG observed, measured deviation, meaning, and correction status; audit counts stay secondary.

## 7. TDD and convergence
Every production behavior change starts with a failing test. A cheap legacy/drift gate runs before Android build work. Software-complete requires FAST → JVM/unit → lint → APK on the exact final SHA. Vehicle economy/stability claims require physical validation.
