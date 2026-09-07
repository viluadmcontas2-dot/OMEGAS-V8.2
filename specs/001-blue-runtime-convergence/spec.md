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
7. As the operator, Blue works fully from MP48 physical evidence even when no OBD adapter is connected, while an available OBD adapter provides an independent STFT witness that can accelerate confidence without becoming a second writer or correction authority.
8. As the operator, the OBD connection flow is observable and recoverable: permission, Bluetooth transport, ELM handshake, protocol negotiation and STFT acquisition are distinguishable instead of collapsing into a generic failure.

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
- FR-012: retain OBD as an optional auxiliary feedback subsystem. OBD must never be required for MP48 learning, Blue proposal generation, Map K/Curve K manual operation or Auto-Cal. OBD must never directly write the ECU or own correction mathematics.
- FR-013: the production OBD evidence signal is STFT. LTFT, coolant, calculated load, throttle, MAF, vehicle speed, intake temperature and similar PIDs are not inputs to Blue confidence or OBD evidence matching in this implementation.
- FR-014: every accepted STFT observation is paired to the nearest valid MP48 frame and carries exactly the physical context needed by the OMEGAS model: RPM, MAP, Petrol Inj., fuel label and current calibration state. Only observations whose authoritative MP48 fuel is GNV/CNG enter the OBD scientific witness.
- FR-015: gasoline remains the physical reference through the MP48 Petrol-Inj. comparison, not through a required gasoline OBD trim. The production OBD witness is STFT Bank 1 observed on GNV; gasoline OBD STFT and LTFT are not requirements for Blue equivalence or confidence.
- FR-016: OBD can accelerate Blue confidence when temporally paired GNV STFT from the same RPM/MAP/current-GNV-Petrol-Inj. region agrees in correction direction with Blue MP48 physical evidence. It may reduce uncertainty/confidence requirements, but it does not calculate K targets and cannot override contradictory Blue evidence.
- FR-017: OBD transport failures must be diagnosable by stage (permission, adapter, RFCOMM, ELM init, protocol, STFT PID), and connection attempts must not remain indefinitely stuck in a non-recoverable `CONECTANDO` state.
- FR-018: OBD evidence is calibration-state aware. Confirmed Map K or Curve K write/readback opens a new OBD evidence epoch so pre-write and post-write STFT observations are never pooled.

## OBD evidence model
For an STFT observation at time `t_obd`, select the nearest fresh MP48 frame and persist:

- `stft_pct`
- `mp48_rpm`
- `mp48_map_bar`
- `mp48_petrol_ms`
- `mp48_fuel`
- `calibration_state_id`
- `obd_observed_at`
- `mp48_observed_at`
- `pair_skew_ms`

No temperature, load, throttle, MAF, vehicle-speed or other operating variables participate in region matching or confidence.

The OBD store summarizes only GNV STFT observations that are fresh, calibration-state compatible and matched to the current GNV RPM/MAP/Petrol-Inj. region. It does not manufacture a standalone K error from STFT and does not require an OBD gasoline baseline.

The Blue MP48 comparison remains the primary physical error: a recent compatible gasoline Petrol-Inj. microburst is compared with the current GNV Petrol Inj. at equivalent RPM x MAP. The OBD STFT on GNV is then classified only as an agreeing or conflicting witness to that already-computed Blue direction.

## OBD confidence behavior
Blue physical evidence remains sufficient by itself. OBD is an optional witness:

- `SUPPORTS`: same-region GNV STFT and Blue MP48 deviation agree in correction direction; confidence may rise faster.
- `CONFLICTS`: directions disagree materially; do not accelerate confidence and surface the conflict.
- `INSUFFICIENT`: OBD is connected but lacks enough compatible paired observations.
- `UNAVAILABLE`: no usable OBD session.

OBD never writes K and never computes a K target.

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
- `BlueCausalEngine` and Blue physical evidence remain independent of OBD availability.
- OBD route, Bluetooth permissions, ELM transport and OBD evidence subsystem remain present as an optional auxiliary layer.
- OBD cannot call Map K/Curve K write APIs directly and cannot bypass human confirmation, ACK or readback.
- OBD confidence input uses STFT paired to MP48 RPM/MAP/Petrol Inj. only; temperature/load/throttle/MAF/speed are absent from the evidence decision path.
- OBD scientific witness remains useful with GNV STFT alone; no gasoline OBD STFT baseline is required.
- A same-region matched GNV STFT witness that agrees with Blue can accelerate confidence; a conflicting witness cannot increase confidence.
- Confirmed calibration readback creates a new OBD evidence epoch.
- A failed or stalled OBD connection produces a bounded, visible stage/error and can be retried without restarting the app.
- FAST, full unit/JVM and lint pass on the final exact SHA and establish `READY FOR APK GENERATION`. APK generation remains a separate owner-authorized gate.