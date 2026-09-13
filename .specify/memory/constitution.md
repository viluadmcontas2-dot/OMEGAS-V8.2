# OMEGAS RED Constitution

## 1. Repo-first + Spec Kit + Issues
The Git repository is the only mutable technical authority. Canonical requirements, architecture and execution structure live in the repository. Spec Kit under `specs/` is the canonical requirements/plan/task framework. GitHub Issues are the canonical executable work units. Chat, Drive and historical sessions are evidence/input, never runtime authority.

For the current closure, the canonical package is `specs/004-red-final-engine-closure/` and the parent execution Issue is #30. Every production change must belong to an open GitHub Issue with explicit scope, tests, acceptance criteria and evidence. Commits/checkpoints reference the Issue number. Do not invent a parallel task-ID system as execution authority.

## 2. One measured authority; Predictor is a projection
`BlueCausalEngine` is the runtime authority for measured gasoline reference, GNV equivalence error and causal actuator attribution. The Predictor is allowed only as a bounded estimated projection of the same learned surface/error model: it may accelerate useful guidance before coverage is complete, but it cannot become a second scientific truth, override sufficiently strong observed evidence, fabricate local support, or write to the ECU.

No legacy equivalence engine, independent AutoMatch truth, visit-count-only confidence engine or independent Auto-Cal correction math may compete with the measured authority. Existing advisor/projection code may remain reachable only when it obeys the same evidence authority and Issue #36 contract.

## 3. Permanent gasoline surface and physical truth
Raw MP48 telemetry and confirmed ECU readback are physical truth. Valid gasoline evidence builds a persistent surface `(RPM, MAP) -> expected Petrol Inj.`. Gasoline evidence does not expire because 30 seconds elapsed, because fuel changed to GNV, because a GNV epoch changed, or because Curve K/Map K changed.

RPM × MAP identifies comparable operating condition. Supported internal regions may be interpolated continuously, including horizontal, vertical, diagonal and irregular 2D neighborhoods. Observed, interpolated and predicted values remain distinguishable. Unsupported regions abstain instead of being aggressively extrapolated.

For GNV evidence/correction, **current GNV RPM + current GNV Petrol Inj.** locates Map K geometry. Gasoline target Petrol Inj. is not the current GNV Map K address. Optional OBD contributes same-region GNV STFT as a read-only witness; absence/conflict must not erase valid MP48 measurement. Evidence from different GNV calibration states is never pooled as one GNV state, while the gasoline surface remains persistent.

## 4. Causality belongs to calibration intervention, not gasoline validity
Short before/after time windows may be used to attribute the effect of an actual Curve K/Map K intervention and estimate actuator gain. A short temporal window must never decide whether a gasoline reference exists. Causal gain must be measured from a real calibration change; no fabricated gain and no fallback gain of 1.0.

## 5. Calibration write safety
No automatic ECU write. Every mutation is human-reviewed and follows prepare → review → confirm → write → ACK → readback. Service, USB, ECU readiness and fresh telemetry are legitimate write gates. RPM value is never a write-authorization gate for Curve K, Map K or Auto-Cal proposals unless a future explicit Issue/spec changes that contract with evidence.

## 6. Durable sessions and fast runtime
A logical driving session may contain multiple USB connection segments. A transient disconnect/reconnect must not create a new retained session. Sessions are classified `PROBE`, `VALID` or `PROTECTED`; tiny probes never evict useful sessions. Default retained VALID/PROTECTED sessions is 30 and configurable minimum is 20. Sessions containing confirmed calibration/readback or explicit protection are never auto-pruned.

Live recording uses a fast private spool. Qualified closed sessions are promoted to a user-controlled OMEGAS session vault. Heavy surface interpolation, Predictor rebuild and historical aggregation must stay outside the telemetry hot path and UI render tick. Revision-driven/cache/coalesced computation is preferred; stale work must not form an unbounded backlog.

## 7. Didactic UI
Learning separates measurement, interpolation/prediction and action. Primary operator information is RPM/MAP context, gasoline expected, GNV observed, measured/estimated deviation, confidence/origin and suggested adjustment when available. Internal engine names, epochs, calibration state IDs, ACK/readback explanations and forensic provenance do not belong in the primary driving pane; they may remain in diagnostics.

## 8. TDD, Issue closure and release convergence
Every production behavior change starts with a failing test attached to its GitHub Issue. A test that was never observed failing is not RED evidence. Issue closure requires exact remote SHA plus the focused/broad verification required by that Issue.

`READY FOR APK GENERATION` requires the software Issues under #30 to be reconciled, FAST → JVM/unit → lint on the exact final SHA, plus adversarial independent review in #35. APK generation is a separate owner-authorized artifact gate. #25 is a separate physical vehicle gate; CI/APK cannot prove fuel economy, real-vehicle stability or physical device compatibility.
