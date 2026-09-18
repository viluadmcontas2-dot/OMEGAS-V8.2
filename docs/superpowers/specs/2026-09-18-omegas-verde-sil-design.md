# OMEGAS Verde SIL — Headless Scientific Replay Design

**Status:** OWNER APPROVED
**Approved:** 2026-09-18
**Repository:** `viluadmcontas2-dot/OMEGAS-V8.2`
**Base branch:** `OmegasVerde`
**Implementation branch:** `work/omegas-verde-sil`

## 1. Goal

Create a deterministic headless Software-in-the-Loop (SIL) laboratory that replays recorded MP48 traffic through the real OMEGAS Verde scientific runtime without Android Emulator, APK, UI, or physical USB.

The SIL exists to test science and learning behavior at machine speed. It must not become a second product engine.

## 2. Non-negotiable architecture

The scientific path reuses production classes wherever possible:

`recorded MP48 payload -> transport seam -> ResponseDrivenEcuEngine -> Mp48Protocol.decodeTelemetry -> MotorSampleAnalyzer -> MotorLearningMemory -> exported comparisons/reference -> Verde stability/advisor analysis`.

Only platform boundaries may be replaced by deterministic test doubles: USB transport, monotonic clock/sleep, filesystem root, and owner-facing delivery.

No copied Python implementation of the learning algorithm is authoritative.

## 3. Corpus

AgentRed reads source logs from `G:\Meu Drive\OMEGAS`.

Supported sources:
- OMEGAS session ZIPs with raw USB / telemetry evidence;
- Portmon LOGNOVO/AUTOCAL archives where the MP48 transaction can be reconstructed.

Exact duplicate sessions must be collapsed by content fingerprint before cross-session validation. Provenance remains attached to every canonical session.

## 4. Replay semantics

Replay is chronological and deterministic. The transport answers the real MP48 handshake and telemetry requests using recorded responses. No wall-clock sleeps are required in accelerated mode.

Each replay frame receives a stable SIL sequence id. The trace must preserve:
- source/session;
- raw 34-byte MP48 payload;
- decoded RPM/MAP/Petrol/GNV/fuel state;
- `SampleDecision`;
- learning state/reference/comparison;
- cell/visit/epoch identifiers where available;
- stability/advisor output when materialized.

## 5. Operational accuracy criterion

The scientific target is **zero correction**: the learned/reference behavior should drive the residual correction/STFT toward **0%** across the usable operating region.

Define signed correction error as:

`correction_pct = (predicted_ms - observed_ms) / observed_ms * 100`.

The optimization target is always:

`correction_pct -> 0%`.

An absolute correction of **5% or less** is the owner's operational acceptance band:

`abs(correction_pct) <= 5%`.

This tolerance is **not** a stopping target and does not mean that samples already inside ±5% should be ignored. Improvements from ±4% toward 0% remain scientifically valuable, provided they generalize and do not reduce robustness.

Metrics therefore track both:
- distance to zero: mean/median/P90/P99 absolute correction and signed bias;
- operational acceptance: `within_5pct_rate`.

Generalization to unseen sessions, resistance to stale/frozen signals and transients, deterministic repeatability, coverage, and explainability remain mandatory constraints while optimizing toward zero.

## 6. Validation

Mandatory gates:
- parser/protocol parity against known MP48 frames;
- real production decoder used in SIL;
- real production analyzer and learning memory used in SIL;
- replay determinism: same corpus => byte-identical normalized trace/summary;
- exact-session deduplication;
- leave-one-session-out evaluation;
- blocked temporal evaluation for transient/high-load subsets;
- explicit stale/freeze accounting;
- no prediction counted as physical evidence;
- no ECU writer invoked.

## 7. Safety and scope

The SIL is read-only with respect to vehicle/calibration hardware. It may calculate suggestions but never perform a writer action.

No Android Emulator, APK installation, UI rendering, ADB, or physical ECU validation belongs to this flow.

## 8. Definition of Done

The first usable SIL is complete when:
1. a canonical LOGNOVO replay traverses the real decoder/analyzer/learning chain headlessly;
2. all canonical sessions in the corpus can be enumerated/deduplicated;
3. a machine-readable frame trace and session summary are produced;
4. the summary reports `mean_abs_correction_pct`, signed bias, P90/P99 absolute correction, `within_5pct_rate`, MAE, coverage, stale/freeze counts and learning/reference states;
5. focused parity/determinism tests pass;
6. AgentRed executes the corpus run and publishes receipts/artifacts;
7. no production scientific algorithm has been duplicated or silently replaced.
