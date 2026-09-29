# Ômegas Platina — DUMP AutoCal coherence gate

This file promotes **Google Drive / OMEGAS / DUMP** from background forensic material to a release coherence gate.

## Identity anchor

The canonical `Copy of ProgBase (3).exe`, `DUMP/ProgBase.exe.Dump.bin` and `DUMP/ProgBase.exe.Dump1.bin` were re-read from Drive and are byte-identical:

`8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`

Therefore the distilled DFM/resources below belong to the exact executable being used as the ProgBase authority.

## Precedence

1. **DUMP** is authoritative for static ProgBase structure: DFM object identity, component binding, names and embedded data-model semantics.
2. **Authorized Portmon** is authoritative for observed wire/runtime behavior: actual frame bytes, ordering, ACK/readback and autonomous ECU transitions.
3. When DUMP and runtime evidence appear to disagree, Platina does **not** invent a reconciliation. The behavior becomes PARTIAL/UNKNOWN and host mutation fails closed.

## Release-blocking facts

- `VECT_AUTOCAL_U8_2` is bound by the original TAutoCalDM to `MaxAutomatch`; Platina maps 0x0165:2 to `MAX_AUTOMATCH`.
- `VECT_AUTOCAL_U8_1` is bound to `!AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD`; it is not MaxAutomatch.
- The exact executable independently closes the runtime maturity selectors at `0x00516F64`: bands `0..5` use petrol `VECT_AUTOCAL_U8_1` or gas `CALIBRATION_VAL_1[5]`; bands `6..17` use petrol `CALIBRATION_VAL_1[2]` or gas `CALIBRATION_VAL_1[8]`. The canonical static boundary bytes at `0x00A9DA1A` are `05 09 0D`, matching the four acquisition-zone boundaries `5/9/13`.
- Normal `MUL_ACT` belongs to TAutoCalDM. `MUL_ACT_EE` and `VECT_AUTOCAL_EE` belong to the distinct TAutoCalDM_EE surface.
- Reset K must continue to use normal `MUL_ACT 0x0161[30]` → Q14 `0x4000`, with readback. An EE field cannot silently replace it.
- The DUMP proves the original UI owns enable, Manual AutoMatch, Finish AutoMatch, reset petrol/gas/all, Reset K, acquisition/live point series and Petrol/Gas reference curves. Platina may modernize presentation, but it may not change the underlying proven semantic identity.
- Native AutoMatch remains ECU-owned. The Android host may observe/bracket/log it, never trigger an automatic K write.

## Exact resource hashes

- TAutoCalUI: `449dda772b16b0d6a79c7bbd315754b37e4dbab26c129cb9346307daa2e954a7`
- TAutoCalDM: `96ba3d934bf66593f919e22e37e2a90a171be5c765530e39d484ecfc16c1ee86`
- TAutoCalSettings: `852de437e29e580936ff000aa49644289e455c1242d8ec9a64f35baa005e8f03`
- TAutoCalDM_EE: `985db334af7211c5136404444131606445ef37831f9fe3ec0d512c81128d2a10`

Machine-readable source: `tests/fixtures/platinum-progbase-dump-autocal-v1.json`.

This gate intentionally does **not** claim the exact internal ECU AutoMatch formula. Unknown firmware internals are non-blocking only when the ECU remains authoritative and the host does not reproduce them as a writer.
