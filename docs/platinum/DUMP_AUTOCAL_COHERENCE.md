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

## Didactic/runtime binding: what each original DUMP layer means

Static authority: the matching TAutoCalUI, TAutoCalDM and TAutoCalSettings binary resources in Drive/DUMP. Component/field identities are proven, not the ECU firmware AutoMatch formula.

| TAutoCalUI component | ECU/host source | On-screen meaning | Reset/AutoMatch handling |
| --- | --- | --- | --- |
| PetrolPoint / PetrolLine | PETR_INJ_TBUF, MNFLD_PRESS_BUF, NUM_BUF_UPD_PETR (18 bands) | CURRENT petrol acquisition, only bands with samples | RESET_PETROL clears petrol only; native GNV AutoMatch preserves it |
| GasPoint / GasLine | PETR_INJ_TBUF_GAS, MNFLD_PRESS_BUF_GAS, NUM_BUF_UPD_GAS (18 bands) | CURRENT GNV acquisition, never an archived session | RESET_GAS and each observed AutoMatch start a new GNV generation; no exact zero-frame need be captured |
| GasPointPrev | PETR_INJ_TBUF_GAS_PREV plus MNFLD_PRESS_BUF_GAS_PREV | PREVIOUS GNV as subdued context, not proof of current support | There is no NUM_BUF_UPD_GAS_PREV in TAutoCalDM; do not count it toward maturity or equivalence |
| PetrolCurve | PETR_INJ_TBP plus PETR_MNFLD_PRESS_RV | ECU gasoline reference curve | Remains displayed when ONLY GNV resets, if timestamps are coherent; invalidated when petrol resets |
| GasCurve | PETR_INJ_TBP plus GAS_MNFLD_PRESS_RV | ECU GNV reference curve | Ineligible for current equivalence until current GNV acquisition and a fresh coherent reference group |
| RunPoint / CurrentBand | Live Petrol Inj., MAP plus native thresholds | NOW / active pressure region; telemetry is not acquisition | Never include live cursor in learned samples or chart domain |
| KLine | Normal MUL_ACT, 30 factors | Independent K correction curve, not the 18 acquisition bands nor 2D Map K | Native AutoMatch can reshape K independently of buffer resets; acquisition reset must not be represented as K reset |
| ChartPoint / NumGasPt | Native acquisition activity/counters | Support evidence, separate from PetrolCurve/GasCurve | Counter > 0 signals sample activity, not proof of ECU maturity |

Per-fuel invariant: after RESET_GAS or native AutoMatch, invalidate only the CURRENT GNV acquisition and its GNV reference; preserve current petrol and PetrolCurve when their temporal provenance remains valid. Apply the converse after RESET_PETROL. RESET_ALL invalidates both. Archived GNV must remain clearly distinct. A manual-reader fallback must not resurrect stale RV or buffers.

Didactic lifecycle: petrol acquiring -> petrol reference exists -> GNV acquiring -> native AutoMatch 1 (GNV reacquires) -> AutoMatch 2 (GNV reacquires) -> AutoMatch 3 (GNV reacquires) -> residual comparison once the current evidence permits it. AutoMatch 3/3 is a quota, NOT a command to end acquisition; AUTO_CAL_ENABLE remains independent.

Data semantics: 0/18..18/18 counts bands with samples, not completion. Maturity requires applicable ECU-provided thresholds, low bands 0..5 and normal bands 6..17 with selectors documented above. MIN_COMMON_BANDS=3 in NativeAutoCalAcquisitionEpoch is a host geometric presentation guard, NOT a ProgBase/ECU maturity criterion. Equivalence needs overlapping supported acquisitions and coherent time provenance after each intervention.

Concrete regression anchors:
- NativeAutoCalAcquisitionEpoch.kt tracks petrolReferencePending and gasReferencePending independently, including counter changes, resets and USB generations.
- AutoCalUiProjection.kt masks stale fields per fuel while preserving the original evidence snapshot.
- autocal-cockpit.js distinguishes current points, separately preserved native PetrolCurve, ghosted GAS_PREV, live AGORA and independent K state.
- NativeAutoCalAcquisitionEpochTest.kt, AutoCalUiProjectionTest.kt and tests/ui/autocal-live-epoch-gate.test.cjs cover these transitions.

## Exact resource hashes

- TAutoCalUI: `449dda772b16b0d6a79c7bbd315754b37e4dbab26c129cb9346307daa2e954a7`
- TAutoCalDM: `96ba3d934bf66593f919e22e37e2a90a171be5c765530e39d484ecfc16c1ee86`
- TAutoCalSettings: `852de437e29e580936ff000aa49644289e455c1242d8ec9a64f35baa005e8f03`
- TAutoCalDM_EE: `985db334af7211c5136404444131606445ef37831f9fe3ec0d512c81128d2a10`

Machine-readable source: `tests/fixtures/platinum-progbase-dump-autocal-v1.json`.

This gate intentionally does **not** claim the exact internal ECU AutoMatch formula. Unknown firmware internals are non-blocking only when the ECU remains authoritative and the host does not reproduce them as a writer.
