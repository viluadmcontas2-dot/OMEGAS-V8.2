# ProgBase / Native AutoCal Ground-Truth Evidence — 2026-09-21

## Purpose

Establish what the original Landi Renzo ProgBase and the ECU actually do during AutoCal before OMEGAS models or replicates the behavior.

This report separates:
- native ECU behavior observed on the serial wire;
- ProgBase UI/orchestration behavior;
- OMEGAS inference/reconstruction.

No APK and no production-branch mutation are part of this work.

## Authoritative artifacts

### ProgBase

Path on MMMACHINE:

`C:\Users\hugov\Desktop\Landi Renzo\Landi Renzo Omegas\ProgBase.exe`

- File version: `4.2.0.6`
- Size: `12,643,840` bytes
- SHA-256: `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`

Drive copy:

`G:\Meu Drive\OMEGAS\Copy of ProgBase (3).exe`

### Raw Portmon — AutoCal

`G:\Meu Drive\OMEGAS\benchmark_gpu_20260918\raw\autocal\PortmonAUTOCAL (1).LOG`

- Size: `162,700,984` bytes
- SHA-256: `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`

### Raw Portmon — LOGNOVO

`G:\Meu Drive\OMEGAS\benchmark_gpu_20260918\raw\lognovo\PortmonLOGNOVO.LOG`

- Size: `149,911,521` bytes
- SHA-256: `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`

These two raw logs are mandatory scientific replay artifacts. Derived CSVs may accelerate analysis but are not ground truth for protocol behavior.

## Static ProgBase evidence

ASCII/RTTI/resource strings recovered directly from `ProgBase.exe` include:

- `TAutoCalDM`
- `TAutoCalUI`
- `AutoCal_DM`
- `AutoCal_UI`
- `state_acquire_petrol_line`
- `state_draw_gas_petrol_curve`
- `VECT_AUTOCAL_U8_0/1/2`
- `AUTO_CALIBR_BYTE_ARRAY`
- `AUTO_CALIBR_WORD_ARRAY`
- `MUL_ACT`
- `MUL_ACT_EE`
- `PETROL_POINT_2DELETE`
- `ACQUIRED_ZONES_PETROL`
- `CALIBRATION_VAL_1`
- `MAX_RPM_FOR_AUTOCAL`
- `LabelNumAutoMatch`
- `ButtonAutoMatch`
- `ActionAutoMatchExecute`
- `ActionFinishAutocalExecute`
- `CheckAutoCalEnable`
- `PollingPetrol`
- translated UI text `Automatic ECU calibration`
- translated UI text `Autocalibration in progress...`
- translated UI text `Autocalibration completed correctly...`

This proves that ProgBase has explicit AutoCal state/UI/data-model support. It does not by itself prove where every calibration formula executes.

## Wire-level protocol evidence

Known AutoCal objects observed in raw Portmon and already mapped in OMEGAS include:

- `0x014A AUTO_CAL_ENABLE`
- `0x014B PETR_INJ_TBP`
- `0x014C MNFLD_PRESS_THD`
- `0x015B NUM_BUF_UPD_PETR`
- `0x015C NUM_BUF_UPD_GAS`
- `0x015D PETR_INJ_TBUF_GAS_PREV`
- `0x015E MNFLD_PRESS_BUF_GAS_PREV`
- `0x015F PETR_INJ_TBUF_GAS`
- `0x0160 MNFLD_PRESS_BUF_GAS`
- `0x0161 MUL_ACT`
- `0x0162 PETR_INJ_TBUF`
- `0x0163 MNFLD_PRESS_BUF`
- `0x0165 indexed AutoCal settings / MAX_AUTOMATCH`
- `0x016F ACQUIRED_ZONES_PETROL`
- `0x0170 ACQUIRED_ZONES_GAS`
- `0x0172 CALIBRATION_VAL_1`
- `0x0173 MODULE_VERSION`
- `0x0174 NUM_AUTOMATCH_EXECUTED`
- `0x017A MAX_RPM_FOR_AUTOCAL`
- `0x018D PETR_MNFLD_PRESS_RV`
- `0x018E GAS_MNFLD_PRESS_RV`

In LOGNOVO, `MAX_RPM_FOR_AUTOCAL` returned raw `B8 0B` = 3000 rpm. Therefore RPM participates in native AutoCal at least as a condition/limit. This does **not** yet prove RPM is an axis of the resulting K-factor curve.

## Decisive native self-adjustment observation

A streaming transaction reconstruction of the raw AutoCal Portmon produced:

- `747` reads of `MUL_ACT`;
- initial observed `MUL_ACT` = 30 × Q14 `16384` = factor `1.0`;
- three subsequent full-vector `MUL_ACT` changes;
- `NUM_AUTOMATCH_EXECUTED` subsequently observed at `1`, then `2`, then `3`;
- only four non-read protocol requests in the complete AutoCal capture:
  - `02 24 04 04 2E`
  - `01 00 3A 3B` twice
  - `00 25 25`
- no host `WRITE_U8 0x12` write to `MUL_ACT`;
- no host per-point/vector write of the 30 K-factor values around those changes.

Observed curve transitions:

### Initial
All 30 points = `1.000000`.

### Before AutoMatch count observed as 1
Selected factor values:
- points 0..4 ≈ `0.947632`
- point 5 ≈ `1.018188`
- point 7 ≈ `1.132324`
- points 10/11/14..19 = `1.25`
- upper tail ≈ `1.200012`

### Before count observed as 2
Selected values:
- points 0..4 ≈ `0.924622`
- point 7 ≈ `1.268188`
- several central points ≈ `1.399963`
- upper tail ≈ `1.343994`

### Before count observed as 3
Selected values:
- points 0..4 ≈ `0.980042`
- point 7 ≈ `1.344238`
- several central points ≈ `1.483948`
- upper tail ≈ `1.424622`

### Interpretation

Within this captured serial session, the 30-point `MUL_ACT` curve changes without ProgBase transmitting the replacement vector.

The strongest supported interpretation is:

> the ECU/native AutoCal machinery changes the K-factor curve internally, while ProgBase reads and presents the resulting state.

This is materially stronger evidence than OMEGAS' previous inferred AutoMatch reconstruction.

Remaining uncertainty:
- the exact internal firmware formula/state transitions are not yet decoded;
- some initialization/action frames may occur outside the AutoCal capture start;
- ProgBase may still send enable/start/state commands while the ECU performs the numerical update internally.

## LOGNOVO corroboration

LOGNOVO contains:
- `AUTO_CAL_ENABLE` read as enabled;
- `NUM_AUTOMATCH_EXECUTED` transitions including a reset to 0 and later 1→2→3;
- multiple `MUL_ACT` vector transitions;
- reads of petrol/gas pressure curves and AutoCal acquisition buffers;
- explicit write frames `12 4A 01 00/01` that disable/enable AutoCal at several points.

LOGNOVO also contains many unrelated calibration/map writes. They must be separated from native AutoCal state transitions rather than treated as AutoCal writes.

## Scientific consequences for OMEGAS

1. The phrase **AutoCal** must refer to the native ECU mechanism unless explicitly qualified.
2. `AutoMatchV5Engine` is currently an **OMEGAS inferred reconstruction**, not the authority for native ECU behavior.
3. OMEGAS must not use its inferred formula as ground truth merely because it can approximate snapshots.
4. Native AutoCal state should be reconstructed from raw-wire evidence first:
   - acquisition buffers;
   - counters;
   - pressure curves;
   - state/enable flags;
   - `MUL_ACT` transitions;
   - completion/reset behavior.
5. The app may use native results as evidence only with explicit provenance `ECU_NATIVE_AUTOCAL`.
6. Any attempt to map native AutoCal knowledge onto the 144-node Map K must respect that the native K-factor curve and Map K are different calibration surfaces.
7. RPM observed in native AutoCal may be a gating/operating constraint. No RPM-dependent local Map K claim may be invented unless the raw trace or correlated telemetry supports it.

## Mandatory raw-log test rule

Every scientific subsystem in the new SIL method must have:
- deterministic synthetic unit tests **and**
- a replay/contract test sourced from **both** raw Portmon artifacts above.

Small byte-exact fixtures may be extracted from the two raw logs for fast unit tests, but their source path, SHA-256, transaction indices and extraction recipe must be recorded.

No scientific claim may be promoted solely from generated/synthetic data or derived CSVs.

## Next reverse-engineering work

1. Build a byte-exact transaction timeline for AutoCal-specific objects in both raw logs.
2. Detect all `MUL_ACT` transitions and pair them with:
   - `NUM_AUTOMATCH_EXECUTED`;
   - `NUM_BUF_UPD_PETR/GAS`;
   - `PETR/MNFLD` acquisition buffers;
   - acquired-zone masks;
   - native state flags.
3. Identify start/reset/finish command frames from ProgBase.
4. Recover ProgBase methods/state-machine structure around:
   - `ActionAutoCalRifExecute`;
   - `ActionAutoMatchExecute`;
   - `ActionResetPetrolExecute`;
   - `ActionResetKFactorExecute`;
   - `ActionFinishAutocalExecute`;
   - `state_acquire_petrol_line`;
   - `state_draw_gas_petrol_curve`.
5. Compare every OMEGAS AutoCal protocol assumption against those two traces.
6. Only then decide whether OMEGAS should:
   - merely observe native AutoCal;
   - replicate ProgBase orchestration;
   - replicate any proven host-side calculation;
   - or keep inferred analysis explicitly separate.
