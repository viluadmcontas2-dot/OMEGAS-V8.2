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
The ELM/OBD path remains read-only and writer-isolated. For the finalization slice, the reliable production trim signal remains STFT Bank 1 (PID 0106), timestamp-paired to a fresh MP48 frame.

Accepted paired samples carry:
- OBD STFT;
- MP48 RPM;
- MP48 MAP;
- MP48 Petrol Inj.;
- authoritative MP48 fuel label;
- calibration-state id;
- timestamps/skew.

Gasoline and GNV are stored separately. A GNV observation may be compared only to compatible gasoline evidence. The physical correction ratio is:

`correctionRatio = (1 + STFT_GNV / 100) / (1 + STFT_GASOLINE / 100)`

and the log error is:

`obdErrorLog = ln(correctionRatio)`

This gives Blue a physically interpretable gasoline-relative correction signal. A positive result means the original ECU is adding more fuel on GNV than on gasoline in the matched condition; a negative result means it is removing more.

### Authority boundary
- OBD may measure and expose physical correction magnitude.
- OBD may supply that measured error to Blue when fuel, timing, condition and calibration-state gates pass.
- OBD never calculates a Map K or Curve K target independently.
- Blue remains the only component allowed to translate measured error plus proven actuator response into a correction proposal.
- If OBD and the existing MP48 Petrol-Inj. equivalence measurement conflict materially, the release must surface the conflict instead of averaging it away.
- LTFT may be displayed/recorded later as diagnostic context, but is not promoted into final correction math in this slice because its cross-fuel adaptation/settling semantics are not yet proven for this vehicle.

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
- gasoline STFT reference;
- GNV STFT;
- gasoline-relative physical correction percentage/ratio;
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
4. OBD physical correction math has unit tests with known gasoline/GNV STFT pairs.
5. OBD still has no writer dependency/reachable writer API.
6. Canonical remote CI passes `FAST -> FULL JVM/unit -> lint -> APK` on the exact final SHA.
7. `STATUS.md` records exact SHA/run/evidence and explicitly keeps physical vehicle economy/driveability validation pending.
