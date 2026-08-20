# TRANSVERSAL PASS/FAIL GATE

This is a durable execution invariant for OMEGAS V8.2. It binds every owner, extension and phase gate.

## Authority resolution before any verdict

The live **Notion Contract Registry is canonical**. `GLOBAL-LEDGER-001` is the canonical operational definition of audit provenance, Coverage Manifest and meta-audit. This GitHub contract is an enforcement surface and minimum coverage set; it does not fork or replace those authorities.

Before any verdict, executor and provenance-independent audit run must:

1. Boot through the Governance EntryPoint.
2. Resolve the live Contract Registry and classify every ACTIVE applicable contract as `APPLIES` or `NOT_APPLICABLE_WITH_REASON`.
3. Load `GLOBAL-LEDGER-001` and build the required Coverage Manifest/receipt chain.
4. Read and classify the minimum OMEGAS sources below.
5. Resolve conditional UI/UX bindings whenever a human-facing surface is touched.

## Minimum OMEGAS sources

The executor and audit run must read and classify applicability for:

1. Programa Mestre 001→320+ (`3bd8ee52ac548148aae0f0f80132a5fa`).
2. MASTER TRACE MAP (`3bd8ee52ac54816fac65e2c5324fe56f`).
3. GS-001 Golden Vertical Slice (`3c08ee52ac54816e8c94d9ab631190a8`).
4. OME-EVIDENCE-PROVENANCE (`3c18ee52ac5481ce87b4d9a6e4fb8bdb`).
5. AL-001 Learning/Adaptive (`3bf8ee52ac548158a1ecded03b42744b`).
6. AL-002 Fast K* (`3bf8ee52ac54816581c7c76d809aa0c4`).
7. AL-003 Predictor Fast-to-Zero (`3bf8ee52ac54813abde0c72fe2b29320`).
8. AL-003A Predictor Validation Battery V4 (`3bf8ee52ac5481b48827d6b433cb5658`).
9. AL-004 E2E Fast-to-Zero (`3bf8ee52ac54812b9a2bc293da13a446`).
10. OME-ADP-001 Adaptive binding (`3c08ee52ac548155a3e8da79e2e6206c`).
11. HW-001 TayTech RK3326 (`3bf8ee52ac54816796ccc4cad509a5a8`).

This is a **minimum**, not a static substitute for the Registry. Any additional ACTIVE applicable contract is equally binding.

The machine-readable enforcement surface is `docs/contracts/transversal-pass-fail-gate.json`.

## Conditional UI/UX bindings

When an owner touches a visible or interactive surface, state-to-human projection, CTA, navigation, enabled/disabled reason, human-facing error/recovery, or technical-to-human translation, it must resolve and classify all three current UI authorities:

- `OME-STATE-HUMAN-UI`;
- `UIUX-CUSTOMROM`;
- `UIUX-OMEGADEV`.

For OMEGAS Phase 09 / 197–232 these are expected to apply unless an owner records a specific `NOT_APPLICABLE_WITH_REASON`. UI must derive from typed state/gates and must not create a parallel scientific authority.

## Provenance-based audit independence

`AUDIT_INDEPENDENCE=PROVENANCE_BASED`.

Independence belongs to `AUDIT_EPOCH_ID + AUDIT_RUN_ID + AUDIT_SCOPE_FINGERPRINT`, not to person, model, chat, session or `AGENT_ID`. A provenance-independent audit run must start from live governance/source/evidence, declare `AUDITOR_MODE=READ_ONLY_NORMATIVE` and keep `AUDITOR_NORMATIVE_WRITES=0` over the audited normative target/criteria.

A `NORMATIVE_AUDITED_MUTATION` during that run disqualifies the run from granting PASS to the state it produced. Append-only evidence/control-plane writes are allowed when they do not alter the audited target or criteria. Any material finding closes that audit run as non-PASS; remediation occurs outside the audit run and requires a new audit epoch/run. Meta-audit must use `META_AUDIT_RUN_ID != AUDIT_RUN_ID` and remain read-only over the normative surfaces it judges. The same agent/model may execute the later audit/meta-audit when these provenance constraints are satisfied; identity is traceability, not eligibility.

## Required PASS CONTRACT block

Every owner must record, before its release test:

- exact remote `TARGET_SHA` and owner ID;
- `TRANSVERSE_APPLICABILITY`: every minimum source and every ACTIVE applicable Registry contract marked `APPLIES` or `NOT_APPLICABLE_WITH_REASON`;
- `TRANSVERSE_REQUIREMENTS`: concrete obligations inherited from each applicable source/contract;
- `TRANSVERSE_EVIDENCE`: exact code path, test, corpus, device or measurement proving each obligation;
- `CONSUMER_CALL_PATH`: mandatory for helpers/models/projections added by the owner;
- `FALSIFIERS`: negative cases capable of disproving the implementation;
- `IMPLEMENTATION_RUN_ID`, `AUDIT_EPOCH_ID`, `AUDIT_RUN_ID`, `AUDIT_SCOPE_FINGERPRINT`, `AUDITOR_MODE`, `AUDITOR_NORMATIVE_WRITES` and `META_AUDIT_RUN_ID`;
- `INDEPENDENT_AUDITOR_RECEIPT`: independence is provenance-based under `GLOBAL-LEDGER-001`;
- `UNPROVEN_SURFACES` and `INVALIDATION_EVENTS`.

## Automatic non-PASS conditions

Any of the following makes the verdict non-PASS and blocks material dependents:

- Governance EntryPoint / Contract Registry not resolved;
- `GLOBAL-LEDGER-001` not loaded;
- minimum transversal source not read;
- applicability not classified;
- ACTIVE applicable contract not classified;
- human-facing owner that does not classify `OME-STATE-HUMAN-UI + UIUX-CUSTOMROM + UIUX-OMEGADEV`;
- applicable requirement without evidence;
- audit provenance that is absent, reused or not independent;
- `NORMATIVE_AUDITED_MUTATION` inside the audit run that attempts to judge the state it produced;
- `META_AUDIT_RUN_ID == AUDIT_RUN_ID`;
- new helper/model without a real consumer call path;
- string/grep-only test used as proof of executable behavior;
- host benchmark claimed as TayTech/RK3326 evidence;
- Prediction reused as Observation;
- scientific constant promoted without invariant/sweep/holdout/evidence classification;
- broken/timed-out harness or partial output treated as evidence.

Use `PARTIAL`, `FAIL`, `INCONCLUSIVE`, `TEST_NOT_AVAILABLE`, `STALE_BY_EVIDENCE` or `STALE_BY_GOVERNANCE` as appropriate. Only a fresh provenance-independent audit PASS plus a distinct meta-audit PASS may unlock a material dependent.

## Cross-contract scientific obligations

- **MASTER TRACE MAP:** preserve authority separation and the causal route ECU → typed state → evidence → physics → predictor → draft → human review → writer → ACK/readback → reconcile/revalidation. UI/render is never a scientific heartbeat.
- **GS-001:** preserve one trace/provenance story through each reached stage; no concatenation of independent harnesses may be called E2E.
- **OME-EVIDENCE-PROVENANCE:** every material claim traces `claim → Ledger receipt → Evidence Lab/artifact/source`; evidence is not promoted to execution state by copy-paste.
- **AL-001:** evidence is calibration-bound, continuous-context aware and does not turn arbitrary counts/gaps/visits into universal scientific authority. Microstate/context is preserved where material.
- **AL-002:** optimize time-to-zero; separate reference readiness, K* measurement, target estimation and StepPolicy. 4/6-frame observations are evidence from a specific log, not universal constants. Effective Map/Curve context must not be silently discarded.
- **AL-003:** IdealTarget is distinct from K_next/StepPolicy; prediction never becomes observation; sparse prediction requires uncertainty/abstention and local/contextual conflict handling.
- **AL-003A:** predictor/model/confidence changes require the risk/noise/drift/context/abstention validation battery on authoritative evidence.
- **AL-004:** no helper without consumer, no structural-only proof for executable behavior, no arbitrary scientific constants, no broken harness as evidence; product metrics include time-to-reference/K*/post-write decision and risk/coverage.
- **OME-ADP-001:** Adaptive materialization must remain on one physical/runtime backbone, preserve typed authority separation and trigger proportional retro-audit rather than rewriting history.
- **HW-001:** acquisition remains dominant; queues/buffers are bounded; performance-sensitive changes require target-device evidence before claiming RK3326 PASS; ABI/ROM constraints remain explicit.
- **UI/UX bindings:** human projection must derive from typed state/authority, preserve intention/consequence and explicit failure/recovery, and keep technical detail on demand without duplicating science in the UI.

## Retroactive rule

Any earlier owner marked PASS without an explicit transversal receipt, provenance-independent audit receipt and distinct meta-audit receipt is `STALE_BY_GOVERNANCE` until reaudited against the exact current remote SHA. This does not imply the implementation is wrong; it means the previous evidence is insufficient to release downstream work under the strengthened contract.
