# Feature 001 — Blue Runtime Convergence

## Problem
OMEGAS Blue already contains the first causal engine, but runtime still carries compatibility/legacy authorities, Auto-Cal is not bound to the real Blue proposal, session lifetime follows USB connections, retention defaults to three, useful recordings can be evicted by reconnect probes, storage lives in app-private external files, and the Learning UI mixes measurement, prediction and suggestion semantics.

The previous full CI also fails because tests for removed AutoMatch engines still compile against deleted production classes.

## Goal
Converge the V8.0 RED-derived Blue branch into one causal runtime with durable, useful sessions and one clear correction experience, without changing proven MP48 protocol/writer safety behavior.

## User stories
1. As the operator, I can review/apply Curve K or Map K at any engine RPM; RPM never blocks a manual write merely because it is above idle/1200 RPM.
2. As the operator, USB reconnects during one drive do not fragment my useful history or cause good sessions to be overwritten.
3. As the operator, I retain at least 20 meaningful sessions; default is 30, while tiny probes do not consume that budget.
4. As the operator, useful session artifacts can live in a user-controlled OMEGAS folder, with private crash-safe spool as fallback.
5. As the operator, Learning shows what was measured separately from what Blue proposes to change.
6. As the system, exactly one runtime component owns correction mathematics: `BlueCausalEngine`.
7. As the operator, Blue does not depend on an OBD adapter, Bluetooth OBD session, STFT/LTFT map or OBD-derived learning gate; MP48 + Blue physical evidence are the calibration source of truth.

## Functional requirements
- FR-001: remove reachable V7 equivalence, legacy Advisor/AutoMatch/Predictor decision math and compatibility facades; pure physical protocol utilities may survive only when they own no decision math and are renamed/migrated when touched.
- FR-002: Auto-Cal reads a real `BlueCorrectionProposal`; no `BLUE_ENGINE_PROPOSAL_NOT_BOUND_YET` terminal placeholder remains.
- FR-003: no mutating path for Map K, Curve K or Auto-Cal may compare live RPM to an upper/lower write threshold. RPM remains evidence/cell-location data.
- FR-004: preserve service/USB/ECU-ready/fresh-telemetry/human-confirmation/ACK/readback safety gates.
- FR-005: logical session persists across transient USB segments while service remains alive.
- FR-006: classify closed sessions as PROBE/VALID/PROTECTED from evidence, not raw existence. Calibration-confirmed/readback or explicit protection implies PROTECTED.
- FR-007: retention budget counts VALID/PROTECTED, defaults to 30 and cannot be configured below 20. PROBE cleanup is independent and may never evict useful sessions.
- FR-008: live recording writes to private spool; qualified sessions can be promoted to a persisted user-selected SAF tree. Promotion failure leaves spool intact and visible as pending.
- FR-009: Learning primary layers are petrol evidence, CNG evidence and measured deviation. Proposal is shown separately and comes only from Blue.
- FR-010: stale tests/assets whose only purpose is removed legacy decision math are deleted, not patched to preserve dead APIs.
- FR-011: Spec Kit artifacts and a convergence/drift contract are mandatory CI inputs.
- FR-012: Blue production runtime contains no OBD subsystem, OBD route/screen, OBD-derived evidence map/gate, OBD browser bridge, OBD-only Bluetooth permission/flow, or OBD settings. Removal must not affect MP48 USB, GPS, LAN, session recording, Blue evidence, manual K writers or Auto-Cal.

## Session relevance policy
A session is `PROTECTED` if it contains a confirmed calibration write/readback or operator protection marker. Otherwise it becomes `VALID` after at least 20 telemetry frames spanning at least 5 seconds. Anything smaller is `PROBE`. These thresholds are operational defaults and may be tightened later from evidence; they are deliberately based on useful telemetry rather than byte count.

## Acceptance criteria
- A safety-policy regression proves `rpm=5000` is not rejected when all real write-safety conditions are valid.
- Static convergence gate finds no RPM write threshold in mutating authority paths.
- No V7 equivalence compatibility facade or legacy AutoMatch/advisor decision authority is reachable.
- Auto-Cal returns the current Blue proposal or an explicit evidence/gain reason.
- USB disconnect/reconnect produces segment boundary events in one logical session when auto-reconnect keeps service alive.
- 30 useful sessions are retained by default; setting below 20 is coerced to 20; probes do not consume useful retention.
- Vault permission/promotion is failure-safe.
- Learning does not use prediction/stability fallbacks to label a value as directly measured deviation.
- Production/source tree has no OBD package, OBD UI route/assets, OBD bridge methods, OBD settings, or OBD-only Bluetooth permissions; dashboard/overlay remain functional from MP48 telemetry.
- FAST, full unit/JVM, lint and APK pass on the final exact SHA.
