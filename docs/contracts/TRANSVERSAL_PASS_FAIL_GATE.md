# TRANSVERSAL PASS/FAIL GATE

This is a durable execution invariant for OMEGAS V8.2. It binds every owner, extension and phase gate.

## Mandatory sources before any owner can receive a gate verdict

The executor and the independent auditor must read and classify applicability for:

1. Programa Mestre 001→320+ (`3bd8ee52ac548148aae0f0f80132a5fa`).
2. MASTER TRACE MAP (`3bd8ee52ac54816fac65e2c5324fe56f`).
3. AL-001 Learning/Adaptive (`3bf8ee52ac548158a1ecded03b42744b`).
4. AL-002 Fast K* (`3bf8ee52ac54816581c7c76d809aa0c4`).
5. AL-003 Predictor Fast-to-Zero (`3bf8ee52ac54813abde0c72fe2b29320`).
6. AL-004 E2E Fast-to-Zero (`3bf8ee52ac54812b9a2bc293da13a446`).
7. HW-001 TayTech RK3326 (`3bf8ee52ac54816796ccc4cad509a5a8`).

The machine-readable authority is `docs/contracts/transversal-pass-fail-gate.json`.

## Required PASS CONTRACT block

Every owner must record, before its release test:

- exact remote `TARGET_SHA` and owner ID;
- `TRANSVERSE_APPLICABILITY`: every mandatory source marked `APPLIES` or `NOT_APPLICABLE_WITH_REASON`;
- `TRANSVERSE_REQUIREMENTS`: concrete obligations inherited from each applicable source;
- `TRANSVERSE_EVIDENCE`: exact code path, test, corpus, device or measurement proving each obligation;
- `CONSUMER_CALL_PATH`: mandatory for helpers/models/projections added by the owner;
- `FALSIFIERS`: negative cases capable of disproving the implementation;
- `INDEPENDENT_AUDITOR_RECEIPT`: implementer self-PASS is forbidden;
- `UNPROVEN_SURFACES` and `INVALIDATION_EVENTS`.

## Automatic non-PASS conditions

Any of the following makes the verdict non-PASS and blocks material dependents:

- mandatory transversal source not read;
- applicability not classified;
- applicable requirement without evidence;
- implementer granting PASS to its own owner;
- new helper/model without a real consumer call path;
- string/grep-only test used as proof of executable behavior;
- host benchmark claimed as TayTech/RK3326 evidence;
- Prediction reused as Observation;
- scientific constant promoted without invariant/sweep/holdout/evidence classification;
- broken/timed-out harness or partial output treated as evidence.

Use `PARTIAL`, `FAIL`, `INCONCLUSIVE`, `TEST_NOT_AVAILABLE`, `STALE_BY_EVIDENCE` or `STALE_BY_GOVERNANCE` as appropriate. Only independent, fresh `PASS` may unlock a material dependent.

## Cross-contract scientific obligations

- **MASTER TRACE MAP:** preserve authority separation and the causal route ECU → typed state → evidence → physics → predictor → draft → human review → writer → ACK/readback → reconcile/revalidation. UI/render is never a scientific heartbeat.
- **AL-001:** evidence is calibration-bound, continuous-context aware and does not turn arbitrary counts/gaps/visits into universal scientific authority. Microstate/context is preserved where material.
- **AL-002:** optimize time-to-zero; separate reference readiness, K* measurement, target estimation and StepPolicy. 4/6-frame observations are evidence from a specific log, not universal constants. Effective Map/Curve context must not be silently discarded.
- **AL-003:** IdealTarget is distinct from K_next/StepPolicy; prediction never becomes observation; sparse prediction requires uncertainty/abstention and local/contextual conflict handling.
- **AL-004:** no helper without consumer, no structural-only proof for executable behavior, no self-PASS, no arbitrary scientific constants, no broken harness as evidence; product metrics include time-to-reference/K*/post-write decision and risk/coverage.
- **HW-001:** acquisition remains dominant; queues/buffers are bounded; performance-sensitive changes require target-device evidence before claiming RK3326 PASS; ABI/ROM constraints remain explicit.

## Retroactive rule

Any earlier owner marked PASS without an explicit transversal receipt and independent auditor receipt is `STALE_BY_GOVERNANCE` until reaudited against the exact current remote SHA. This does not imply the implementation is wrong; it means the previous evidence is insufficient to release downstream work under the strengthened contract.
