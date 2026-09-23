# Atlas semantic refinement — AutoCal acquisition topology

Status: **PROVEN**

The native ProgBase AutoCal acquisition surface has **two different granularities** that must not be conflated.

## 18-element native grid

Reference axes:

- `PETR_INJ_TBP` — serial `0x014B`, 18 elements.
- `MNFLD_PRESS_THD` — serial `0x014C`, 18 elements.

Read-only acquisition counters:

- `NUM_BUF_UPD_PETR` — serial `0x015B`, 18 elements.
- `NUM_BUF_UPD_GAS` — serial `0x015C`, 18 elements.

## 4-element acquired-zone state

- `ACQUIRED_ZONES_PETROL` — serial `0x016F`, 4 elements.
- `ACQUIRED_ZONES_GAS` — serial `0x0170`, 4 elements.

The native render proof also shows `CurrentBand` is a separate series/path.

### Consequence

**18 acquisition/reference bins ≠ Z1–Z4 acquired-zone state.**

A product UI can therefore emphasize the 4 coarse zones for driving guidance and keep the 18-element counters/grid as technical detail without losing native meaning. The Atlas does not prescribe the UX; it only proves the underlying topology.
