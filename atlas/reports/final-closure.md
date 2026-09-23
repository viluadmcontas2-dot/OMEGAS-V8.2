# OMEGAS Atlas — ProgBase AutoCal final closure

**Status: CLOSED**

Canonical binary: `ProgBase 4.2.0.6`  
SHA-256: `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`

Final scientific reconciliation:
- run: `35811055077`
- wave: **11**
- targets: **1417 / 1417 PROVEN**
- open: **0**
- BROKEN: **0**
- stalled: **0**
- semantic closure: **true**
- behavioral closure: **true — 14/14 gates**
- next frontier: **false**
- semantic targets: **862 total / 0 missing / 0 open**
- unresolved semantic bindings: **0**

Final state artifact:
- artifact id: `10730051091`
- artifact digest: `sha256:f66479c7fa74f97f755688243768723897b1e866a06b3ad2643aa18635d2112f`
- `state.json` SHA-256: `f77fe11b60b19722a0b11a46dc783942178b02bc2b7c12055fcb06db4b6ae495`
- `next-matrix.json` SHA-256: `d8c04ef911e850e4eeed2e35ca6ee5d4cdf7f6086297d89e40c56f4e96472fe7`

## Canonical action map

- Manual AutoMatch: `0x08` → `02 24 04 08 32`
- Reset Petrol: `0x01` → `02 24 04 01 2B`
- Reset Gas: `0x02` → `02 24 04 02 2C`
- Reset All: `0x04` → `02 24 04 04 2E`
- Reset K Factor: separate path; writes every `MUL_ACT[i] = 1.0`
- Modify Map Refs: separate modal path; writes native MAP/Tinj reference objects and limit objects through TAeb setters.

The raw RTTI + wrapper proof supersedes any shifted action oracle.

## Captured Reset All effect

`0x04` is observed in both canonical Portmon captures. In LOGNOVO, the before/after window shows the broad AutoCal reset belongs to **Reset All**, including zero-dominant petrol/GNV acquisition buffers/reference curves and `MUL_ACT` returning to 30 Q14 values representing 1.0.

## Native refresh/render

The AutoCal graph closes petrol acquisition → `PetrolPoint`, previous gas acquisition → `GasPointPrev`, gas acquisition → `GasPoint`, and `MUL_ACT` → `KLine`.

Acquisition zones and current band are separate TeeChart series. The zones path consumes `ACQUIRED_ZONES_PETROL/GAS` and MAP thresholds.

Native refresh timing is **configuration-driven**: `refresh_time_ms`, default **300 ms**, UI range **200–2000 ms**. A fixed 75 ms loop is not the native ProgBase AutoCal scheduler contract.

## Epistemic boundary

This closes the **ProgBase 4.2.0.6 AutoCal subsystem**, not every function in the whole executable.

For Reset Petrol, Reset Gas and Manual AutoMatch, the host-side implementation/frame/refresh path is fully reconstructed, but those commands do not occur in the supplied Portmon captures. Their firmware-internal selective effects are therefore not invented.

The native catalog/decompiler hit diagnostic caps (12000 catalog functions / 2500 decompilations), but all **semantically admitted AutoCal targets and dependencies** closed: 1417/1417, with zero unresolved bindings and zero frontier.
