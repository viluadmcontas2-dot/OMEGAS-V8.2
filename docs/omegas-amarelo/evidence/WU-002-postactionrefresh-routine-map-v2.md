# WU-002 — PostActionRefresh routine map v2

Authority:
- ProgBase 4.2.0.6 SHA-256: `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`
- PortmonAUTOCAL SHA-256: `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`
- PortmonLOGNOVO SHA-256: `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`

This artifact narrows the native AutoCAL refresh pipeline. It is not a product implementation and it does not treat ProgBase scheduler labels as ECU/physical states.

## 1. Scheduler structure — PROVEN

`PostActionRefresh @ 0x5162F8` dispatches named labels through helper `0x51ABA8`.

Scheduler storage:
- active table pointer: `DM+0x4BC`;
- table count: `DM+0x4C0`;
- cursor: `DM+0x4C4`;
- selector: `0x51AE20`;
- reset cursor: `0x51AE10`;
- advance cursor: `0x51ADE8`.

The selector chooses one of two runtime-built tables:
- `0xA9EB98`: 6 entries;
- `0xA9EBB4`: 8 entries.

The 6/8 selection depends on the scalar read from `DM+0xC4`. This is a refresh schedule, not proof of an ECU state machine.

## 2. Opaque labels resolved to refresh responsibilities

The following identities are closed by direct disassembly plus correlation with the already-proven original producer/consumer matrix.

| Label | Use site | Native work | Visual/operational consumer | Status |
|---|---:|---|---|---|
| `state_0` | `0x5163B1` | refresh two vectors at DM `+0x84/+0x88`, render point series, refresh associated counter at `+0x8C`, invoke polling helper mode 0 | `PetrolPoint` + petrol maturity/polling | PROVEN |
| `state_1` | `0x516524` | refresh two vectors at DM `+0x90/+0x94`, render point series; no maturity counter refresh | `GasPointPrev` | PROVEN |
| `state_2` | `0x516674` | refresh two vectors at DM `+0x98/+0xA4`, render point series, refresh associated counter at `+0x9C`, invoke polling helper mode 1 | `GasPoint` + gas maturity/polling | PROVEN |
| `state_3` | `0x51679E` | refresh DM `+0xA0`, pair with DM `+0x70`, render complete vector | `KLine = PETR_INJ_TBP × MUL_ACT` | PROVEN |
| `state_4` | `0x51685D` | invoke `0x5171EC` and related acquisition-area projection | acquired zones / acquisition areas | PROVEN |
| `state_5` | `0x516969` | use DM `+0x70` endpoints and UI threshold/geometry fields to rebuild reference-axis geometry | AutoCAL reference axes / run geometry | PROVEN_ROLE; exact widget names still UNKNOWN |

Cross-correlation used for the point identities:
- petrol current requires `PETR_INJ_TBUF + MNFLD_PRESS_BUF + NUM_BUF_UPD_PETR`;
- gas previous requires `PETR_INJ_TBUF_GAS_PREV + MNFLD_PRESS_BUF_GAS_PREV` and no current maturity counter;
- gas current requires `PETR_INJ_TBUF_GAS + MNFLD_PRESS_BUF_GAS + NUM_BUF_UPD_GAS`.

That structural distinction exactly matches the three native blocks above.

## 3. Reference-line acquisition cadence — PROVEN mechanism

### `state_acquire_petrol_line`
Use site: `0x516B92`.

When selected:
1. helper `0x51ABA8` confirms the active scheduler label;
2. byte `0xA9DA18` is checked modulo 2;
3. only one parity invokes the native object at DM `+0xD4` through its refresh/read method;
4. the byte is incremented every selected visit.

Cross-correlation with the curve renderer closes DM `+0xD4` as the petrol reference-vector producer used by `PetrolCurve.y` = `PETR_MNFLD_PRESS_RV`.

### `state_acquire_gas_line`
Use site: `0x516C01`.

Same mechanism with independent parity byte `0xA9DA19` and DM `+0xD8`.

Cross-correlation with the curve renderer closes DM `+0xD8` as `GAS_MNFLD_PRESS_RV`.

### Timing consequence

The binary therefore contains an explicit half-rate gate for each reference line. This is consistent with the authoritative raw capture:
- operational/buffer family around ~2 s;
- petrol/gas reference RV around ~4 s.

The existence of the half-rate mechanism is PROVEN. The observed ~4 s cadence is PROVEN from raw traffic. Treating one as the cause of the other is supported by the exact object correlation and is accepted here as PROVEN for this captured ProgBase behavior.

## 4. Curve projection — PROVEN

`state_draw_gas_petrol_curve @ 0x516C72`:

1. matches the scheduler label;
2. invokes helper `0x516D8C`;
3. iterates the vector length returned by `0x513208`;
4. uses DM `+0x70` as the common X vector;
5. renders one series from DM `+0xD8`;
6. renders the other series from DM `+0xD4`;
7. advances scheduler cursor with `0x51ADE8`.

Cross-correlation with the original matrix identifies:
- DM `+0x70` = `PETR_INJ_TBP`;
- DM `+0xD4` = `PETR_MNFLD_PRESS_RV`;
- DM `+0xD8` = `GAS_MNFLD_PRESS_RV`.

Therefore:
- `PetrolCurve = PETR_INJ_TBP × PETR_MNFLD_PRESS_RV`;
- `GasCurve = PETR_INJ_TBP × GAS_MNFLD_PRESS_RV`.

Important boundary: this block is a host/UI projection over already-read native values. Its execution is **not** evidence that the ECU performed a new calibration adjustment at that instant.

## 5. UX consequences — binding for WU-005

The CUSTOMROM / OMEGADEV / state-to-human rule is: UI state must derive from typed authority and must not create a parallel scientific story.

Therefore:

- Never expose `state_0..state_5` as user-facing AutoCAL phases. They are refresh scheduler labels.
- Never show “Ajustando na ECU” merely because `state_draw_gas_petrol_curve` ran.
- “Coletando gasolina” requires observable petrol acquisition evidence: fresh petrol point/counter/zone progression, not scheduler presence.
- “Coletando GNV” requires observable gas acquisition evidence: fresh gas point/counter/zone progression.
- “Ajustando na ECU” requires native adjustment evidence such as a new `MUL_ACT` mutation and/or AutoMatch execution evidence, not a redraw.
- “Verificando resultado” requires post-adjustment fresh native readback after the mutation event.
- Reference curves may remain valid while waiting for their slower ~4 s refresh; age/freshness must be represented without falsely declaring disconnect.
- Live `RunPoint` remains a separate high-frequency plane and must not block on slow curve/reference reads.
- Normal operation stays compact; stale/degraded/failed acquisition gets the visual space and recovery instruction.

## 6. Still UNKNOWN

- exact semantic identity of `DM+0x7C` / `DM+0xCC` in Finish;
- exact three subindices sharing SerialCode `0x0165`;
- exact bit/byte meaning of acquired-zone masks;
- exact runtime producer that enables the 75 ms `TimerDati`;
- exact user-facing completion/verification guard used by ProgBase after AutoMatch;
- exact semantic names of every widget participating in the `state_5` geometry block.

No UNKNOWN is promoted by this artifact.
