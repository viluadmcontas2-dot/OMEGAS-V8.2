# OMEGAS Blue — Finalization Design

## Goal
Finish the current OMEGAS Blue without restarting the product, without redesigning approved surfaces, and without adding speculative subsystems. The release must prioritize useful calibration results, in-car legibility, and fast manual editing.

## Product priorities
1. Blue remains the single calibration decision authority.
2. MP48 remains the authority for active fuel state, Petrol Inj., calibration state, Map K / Curve K readback and all manual writes.
3. OBD becomes first-class physical equivalence evidence instead of a decorative witness.
4. Curve K editing becomes batch-capable and responsive.
5. The existing cockpit visual language is hardened for viewing at driver distance; no shipped UI text may be microscopic.
6. No automatic ECU write. Manual review -> confirm -> write -> ACK -> readback remains mandatory.

## Fuel-state contract
MP48 fuel state is authoritative for scientific labeling.

- `PETROL` / `GASOLINA` -> gasoline evidence.
- `TRANSITION` / `TRANSICAO` -> gasoline evidence, because the engine is still consuming gasoline during MP48 transition.
- `CNG` / `GNV` / `GAS` -> GNV evidence.
- `CUT_OFF` / `CUTOFF` -> excluded from equivalence learning.
- Unknown, stale, or missing MP48 fuel -> OBD may remain visible live but must not enter scientific learning.

No manual fuel button is part of normal operation. OBD fuel inference may exist later only as a fallback/sanity check; it is not required for this release.

## OBD physical-equivalence role
The ELM/OBD path remains read-only and writer-isolated. The production trim signal is STFT Bank 1 (PID 0106), timestamp-paired to a fresh MP48 frame. Only an observation whose authoritative MP48 fuel state is GNV/CNG enters the OBD scientific witness.

Accepted GNV witness samples carry:
- OBD STFT;
- MP48 RPM;
- MP48 MAP;
- MP48 Petrol Inj. **currently observed on GNV**;
- authoritative MP48 fuel label;
- calibration-state id;
- timestamps/skew.

The primary physical equivalence remains MP48: a recent stable gasoline Petrol-Inj. microburst is matched to GNV at equivalent `RPM x MAP`, and Blue computes the Petrol-Inj. deviation. Gasoline OBD STFT is not a prerequisite and LTFT is not a correction input.

The GNV STFT witness does not compute a standalone K error. It answers one question after Blue has a valid MP48 comparison: does the same-region instantaneous lambda correction point in the same correction direction?

### Authority boundary
- MP48/Blue owns the gasoline -> GNV Petrol-Inj. equivalence error.
- OBD supplies same-region **GNV STFT only** as a fast supporting/conflicting witness.
- OBD never calculates a Map K or Curve K target independently.
- Blue remains the only component allowed to translate measured error plus proven actuator response into a correction proposal.
- If GNV STFT and the MP48 Petrol-Inj. equivalence measurement conflict materially, surface the conflict instead of averaging it away.
- LTFT may be displayed/recorded later as diagnostic context, but it has no vote in K correction math.
- Map K evidence/correction is addressed by **current GNV RPM x current GNV Petrol Inj.**, never by the gasoline-reference Petrol Inj.

## Curve K batch editing
The existing Curve K editor remains the surface; it is not redesigned.

Required behavior:
- single tap selects one point;
- additive/toggle selection supports several points;
- dragging across curve hit targets selects the traversed points;
- selected points are visibly highlighted;
- `-0.05`, `-0.01`, `+0.01`, `+0.05` apply to every selected point in one batch;
- an absolute target field remains available for intentional exact entry;
- `Limpar seleção` clears selection without discarding already prepared proposals unless the user explicitly clears proposals;
- one selected point continues to behave naturally;
- a batch nudge performs native preview for each selected point but renders the chart/proposal list once after the batch, avoiding redraw-per-point jank;
- selection/preparation never writes the ECU; the existing review/write/ACK/readback flow remains unchanged.

## Multimedia legibility
The approved cockpit aesthetic is retained. This is a hardening pass, not a redesign.

- No shipped CSS text below 10 px.
- Primary driving values should be materially larger than the 10 px floor.
- Map K axis/header/value typography keeps the existing distance-legibility contract.
- Touch targets used while parked for calibration must remain comfortably tappable.
- Diagnostic detail can stay denser than the dashboard, but must still be readable on the multimedia screen.

## OBD/MDT surface
OBD/MDT remains a dedicated screen/tab, as in the RED product direction. It may show deeper physical evidence without cluttering `Agora`.

The dedicated surface should prioritize:
- live STFT;
- matched MP48 RPM / MAP / Petrol Inj.;
- confirmed fuel;
- GNV STFT;
- MP48 gasoline -> GNV Petrol-Inj. deviation;
- same-region OBD witness state (supports/conflicts/insufficient);
- evidence quality/support;
- conflict/insufficient state;
- connection state.

The main dashboard may show only a concise OBD/Blue status and the most useful large values.

## Performance and safety
- UI batching must avoid render-per-point loops.
- OBD failure degrades gracefully and cannot block Blue/MP48 operation.
- Stale MP48, excessive OBD/MP48 time skew, cut-off, unknown fuel or missing comparable gasoline evidence must fail closed for scientific collection.
- Confirmed Map K / Curve K write/readback starts a new calibration-state boundary.

## Release gates
1. Existing multimedia-distance test goes green by fixing actual typography, not weakening the test.
2. Curve K batch-edit behavior has a RED -> GREEN automated test.
3. Fuel-state normalization has tests proving transition=gasoline and cut-off rejection.
4. OBD witness has unit tests proving GNV STFT works without gasoline OBD and cannot create a standalone K error.
5. OBD still has no writer dependency/reachable writer API.
6. Canonical remote CI passes `FAST -> FULL JVM/unit -> lint` on the exact final SHA and reaches `READY FOR APK GENERATION`; APK build remains owner-gated.
7. `STATUS.md` records exact SHA/run/evidence and explicitly keeps physical vehicle economy/driveability validation pending.
