# ProgBase 4.2.0.6 — AutoCal byte/consumer matrix

**WorkUnit:** OMEGAS-WU-006  
**Issue:** #82  
**Reference binary:** `Copy of ProgBase (3).exe`  
**SHA-256:** `8A2D297C8C21FF3B4F7A47F7FE64593B0FEC9014DD938BD91022DC0C68AC36F4`  
**Real-log fixture:** `tests/fixtures/portmon-autocal-cycle-v1.json`  
**Raw Portmon SHA-256:** `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`

This document records only behavior supported by the original binary, its Delphi resources/RTTI, disassembly, and the committed real Portmon fixture. Unknowns remain explicit. It is not an OMEGAS implementation spec by inference, and SIL/CIU is not used.

## 1. Two independent refresh planes

The original does **not** make the AutoCal screen depend on one slow probe loop.

The live plane is `48 01 49` -> 34-byte telemetry payload -> `TStreamDati` cache at `self+0x293C` -> cache getter `0x004381FC` -> `TFormConfig.TimerDatiTimer` at `0x004A307C` -> AutoCal live helper `0x005158F4` -> `RunPoint`, axes and `CurrentBand`.

The DFM gives `TimerDati.Interval = 75 ms`, with `Enabled=false` as the resource default. When the callback runs it directly invokes `TimerIOTimer` at `0x00437C6C` before consuming the cache. `TStreamDati.TimerIO` also exists as a separate DFM timer at 3000 ms; that resource timer is therefore not the live presentation cadence.

The committed Portmon replay observes `48 01 49` at median **45.844 ms** in the compact sample. The 75 ms UI timer and ~46 ms serial observation are different clocks and must not be collapsed into one scheduler assumption.

The slow AutoCal plane refreshes buffers/reference structures around ~2 s, with `0x018D/0x018E` around ~4 s in the same captured behavior.

## 2. Live payload identity

A normal telemetry response is 40 bytes total: echoed three-byte request + `53` + length `22` + 34-byte payload + checksum. The offsets consumed from `TStreamDati+0x293C` line up with that payload:

| Payload offset | Width | Original use |
|---:|---:|---|
| 0 | u16 LE | RPM raw |
| 6 | u16 LE | gas injection raw |
| 8 | u16 LE | petrol injection raw -> live `RunPoint.x` |
| 11 | u8 | fuel-state byte |
| 13 | u8 | **LEVELS RAW** |
| 14 | u16 LE | gas pressure raw |
| 16 | u8 | gas temperature raw |
| 17 | s16 LE | MAP raw -> live `RunPoint.y` |
| 24 | u16 LE | gas injection bank 2 raw |
| 28 | u16 LE | petrol injection bank 2 raw |

`ChartData` names the X axis **Petrol injection time** and the Y axis **MAP**.

For the live point, the binary computes X as `u16(payload[8:10]) * TStreamDati[0x2980] * 0.00025 * 0.004`. The source field at `+0x2980` is proven but its semantic name/value provenance is not yet named here, so this matrix does not silently replace it by a magic constant.

Y is computed through signed word payload bytes 17..18, integer division by 10, then multiplication by 0.01. This is the original path that yields the MAP axis.

The helper `0x005158F4` also maps live state to 0/1/2, updates `RunPoint`/axes, invokes the current-band helper and then `PostActionRefresh`.

## 3. Slow AutoCal producers and consumers

| DM producer | Serial | Wire evidence in compact fixture | Logical shape | Original consumer |
|---|---:|---|---|---|
| `NUM_BUF_UPD_PETR` | 0x015B | `29 5B 01 85`, 43 B, median 2063.806 ms | `TAebVector`, element length 2 | petrol maturity / `NumPetrPt` |
| `NUM_BUF_UPD_GAS` | 0x015C | `29 5C 01 86`, 43 B, median 2008.368 ms | vector, element length 2 | gas maturity / `NumGasPt` |
| `PETR_INJ_TBUF_GAS_PREV` | 0x015D | `29 5D 01 87`, 43 B, median 2020.241 ms | vector, element length 2 | `GasPointPrev.x` |
| `MNFLD_PRESS_BUF_GAS_PREV` | 0x015E | `29 5E 01 88`, 43 B, median 2018.945 ms | signed vector, element length 2 | `GasPointPrev.y` |
| `PETR_INJ_TBUF_GAS` | 0x015F | `29 5F 01 89`, 43 B, median 2009.749 ms | vector, element length 2 | `GasPoint.x` |
| `MNFLD_PRESS_BUF_GAS` | 0x0160 | `29 60 01 8A`, 43 B, median 2010.503 ms | signed vector, element length 2 | `GasPoint.y` |
| `MUL_ACT` | 0x0161 | `29 61 01 8B`, 67 B, median 1971.708 ms | vector, element length 2 | `KLine.y` |
| `PETR_INJ_TBUF` | 0x0162 | `29 62 01 8C`, 43 B, median 2066.998 ms | vector, element length 2 | `PetrolPoint.x` |
| `MNFLD_PRESS_BUF` | 0x0163 | `29 63 01 8D`, 43 B, median 2059.304 ms | signed vector, element length 2 | `PetrolPoint.y` |
| `ACQUIRED_ZONES_PETROL` | 0x016F | `29 6F 01 99`, 11 B, median 1966.836 ms | byte-masked vector | `AcqusitionAreas` |
| `ACQUIRED_ZONES_GAS` | 0x0170 | `29 70 01 9A`, 11 B, median 2030.338 ms | byte-masked vector | `AcqusitionAreas` |
| `NUM_ATUOMATCH_EXECUTED` | 0x0174 | `09 74 01 7E`, sparse/event-like in selected fixture | byte-masked number | automatch/finish state |
| `PETR_MNFLD_PRESS_RV` | 0x018D | `29 8D 01 B7`, 67 B, median 3934.289 ms | signed vector, element length 2 | `PetrolCurve.y` |
| `GAS_MNFLD_PRESS_RV` | 0x018E | `29 8E 01 B8`, 67 B, median 3924.587 ms | signed vector, element length 2 | `GasCurve.y` |

Additional original dependencies proven from `TAutoCalDM`:
- `PETR_INJ_TBP` = SerialCode 0x014B, vector length 2 -> `KLine.x`, both reference-curve X axes and acquisition-area geometry.
- `MNFLD_PRESS_THD` = SerialCode 0x014C, signed vector length 2 -> current-band and acquisition-area thresholds.
- The selected compact transaction slice does not contain 0x014B/0x014C reads, so no cadence is assigned to them from that fixture.

`PetrolCurve = PETR_INJ_TBP × PETR_MNFLD_PRESS_RV` by paired chart axes; `GasCurve = PETR_INJ_TBP × GAS_MNFLD_PRESS_RV`. No extra physical scaling is invented by this matrix.

## 4. UI-only consumers

`PollingPetrol` and `PollingGas` are `TShape` objects (red/lime), resource-default `Visible=false`. `PostActionRefresh` positions/toggles them. They are polling indicators, not ECU parameters.

`CurrentBand` is a `THorizAreaSeries`. The enable callback does not draw it. Live point processing calls helper `0x0051A614`, which locates current MAP against `MNFLD_PRESS_THD`; helper `0x0051A51C` builds the area segments.

`AcqusitionAreas` is a `THorizAreaSeries` built by `0x005171EC/0x00517254` from petrol/gas acquired-zone state plus the injection-time and MAP-threshold axes.

## 5. LEVELS: raw signal versus learned references

The original has a separate `TFormConfig/GroupRifSensore`; it is not a member of `TAutoCalDM`.

The **live signal** is the unconverted byte at telemetry payload offset 13. `TimerDatiTimer` copies it to form state `+0x1800`.

The calibration/reference state is separate. Generic parameter index `0x0E` reads `TStreamDati+0x2BE4`. Its low byte is the learned minimum reference and its high byte the learned maximum reference:
- `ButtonMinLevelClick` preserves the high byte and replaces the low byte with current LEVELS RAW.
- `ButtonMaxLevelClick` preserves the low byte and replaces the high byte with current LEVELS RAW.
- both write through generic setter index `0x0E`; its case `0x00436F07` updates `TStreamDati+0x2BE4` and triggers the common update/persistence path.

`ButtonDoLevelClick` (“Find levels”) reads those two extrema, checks sensor direction, and derives four internal thresholds with `step = abs(max-min) * 0.2` and multipliers 1, 2, 3, 4 (reversed for the opposite sensor direction). The UI labels are `Riserva`, `1/4`, `2/4`, `3/4`.

This is **not** evidence for converting live RAW into percent, liters, m³ or “full cylinder”. Those semantics remain prohibited without independent physical pressure/temperature calibration.

## 6. Enable / pause / finish

`CheckAutoCalEnable` binds directly to `AutoCalDM.AUTO_CAL_ENABLE`, SerialCode 0x014A, and calls `CheckAutoCalEnableBeforeSetData` before changing it.

No separate AutoCal “pause” control has been proven in `TAutoCalUI`. The original exposes enable/disable plus finish actions; the matrix will not invent a third runtime state.

`ActionFinishAutocalExecute` at `0x0051A390` accesses the shared SerialCode 0x0165 wrapper and `NUM_ATUOMATCH_EXECUTED` (0x0174). The exact subindex semantics of the three `VECT_AUTOCAL_U8_*` wrappers sharing 0x0165 remain **INCONCLUSIVE**.

## 7. What this changes for the OMEGAS investigation

The key property to test in #83/#84 is now concrete: **live AGORA/RunPoint renewal is a separate path from slow AutoCal reference/buffer renewal in ProgBase**.

Therefore an OMEGAS design in which visible AutoCal freshness is renewed only when a slow probe/material-change monitor emits a new snapshot is not equivalent by construction. That is a hypothesis for parity classification, not yet permission to patch production.

## 8. Explicit unresolved items

1. Semantic name/runtime value provenance of `TStreamDati+0x2980` used in injection-time scaling.
2. Exact subindex meanings of `VECT_AUTOCAL_U8_0/_1/_2` sharing SerialCode 0x0165.
3. Exact runtime site that flips `TimerDati` from its resource default `Enabled=false`.
4. 0x014A/0x014B/0x014C cadence is not asserted because those reads are absent from the selected compact fixture.

These unknowns do not block comparing the observable live-vs-slow renewal architecture, but they remain open evidence items rather than guessed facts.
