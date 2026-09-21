# OMEGAS Amarelo WU-001 — Protocol fan-out reconciliation

Date: 2026-09-21

## Authority

ProgBase 4.2.0.6:
- SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`

PortmonAUTOCAL:
- SHA-256 `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`
- 36,463 reconstructed serial transactions.

PortmonLOGNOVO:
- SHA-256 `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`
- 39,517 reconstructed serial transactions.

## Corrected clock

The second numeric value on a Portmon event row is operation duration, not an absolute timestamp.

Canonical transaction time:
`at_ms = cumulative duration of all completed Portmon operations before the IRP_MJ_WRITE that starts the transaction`.

Any earlier cadence result based on treating that field as absolute time is invalid.

## Native action family

Static ProgBase proof:
- Reset Petrol = action code 1.
- Reset Gas = action code 2.
- Reset All = action code 4.
- Manual AutoMatch = action code 8.
- all four enter shared dispatcher `0x00517568`;
- native bridge `0x00512280`;
- transport `0x009244C8`;
- request rule: `02 24 04 <action> <additive checksum>`.

Classification:
- Reset Petrol: `02 24 04 01 2B` — **BINARY_PROVEN_WIRE_UNOBSERVED**.
- Reset Gas: `02 24 04 02 2C` — **BINARY_PROVEN_WIRE_UNOBSERVED**.
- Reset All: `02 24 04 04 2E` — **BINARY_PROVEN_AND_RAW_OBSERVED** in both authoritative captures.
- Manual AutoMatch: `02 24 04 08 32` — **BINARY_PROVEN_WIRE_UNOBSERVED**.

No current capture contains the 1/2/8 action frames. Their absence is not filled by invented wire evidence.

## Native curve mutation

PortmonAUTOCAL:
- 747 `MUL_ACT` reads.
- initial observed curve = 30 × Q14 1.0.
- three later full 30-point curve transitions.
- AutoMatch counter subsequently observed 1 -> 2 -> 3.
- no host replacement-vector write to `MUL_ACT` in that capture.

LOGNOVO:
- non-unity `MUL_ACT` observed initially;
- Reset All frame observed;
- subsequent `MUL_ACT` = 30 × Q14 1.0;
- AutoMatch counter observations include 3 -> 0 -> 1 -> 2 -> 3;
- later full-vector mutations occur again.

Supported conclusion:
the native ECU mechanism can mutate `MUL_ACT` internally; the host does not need to transmit a replacement 30-point vector for each observed AutoMatch transition.

## Enable Auto Calibration

LOGNOVO contains:
- enable `12 4A 01 01 5E` ×4;
- disable `12 4A 01 00 5D` ×4.

Object: `0x014A AUTO_CAL_ENABLE`.

This supports one native enable/disable acquisition control. A separate Pause command is not proven.

## Corrected-clock cadence

### PortmonAUTOCAL
- live `48 01 49`: 21,167 requests; median 46.568 ms.
- `MUL_ACT 0x0161`: median 2024.937 ms.
- `NUM_BUF_UPD_PETR 0x015B`: median 2013.279 ms.
- `NUM_BUF_UPD_GAS 0x015C`: median 2014.502 ms.
- `ACQUIRED_ZONES_PETROL 0x016F`: median 2026.853 ms while active.
- `ACQUIRED_ZONES_GAS 0x0170`: median 2028.965 ms while active.
- `PETR_MNFLD_PRESS_RV 0x018D`: median 4045.793 ms.
- `GAS_MNFLD_PRESS_RV 0x018E`: median 4044.636 ms.

### PortmonLOGNOVO
- live `48 01 49`: 20,451 requests; median 59.880 ms.
- `MUL_ACT 0x0161`: median 2002.729 ms.
- `NUM_BUF_UPD_PETR 0x015B`: median 2010.859 ms.
- `NUM_BUF_UPD_GAS 0x015C`: median 1995.049 ms.
- `ACQUIRED_ZONES_PETROL 0x016F`: median 2028.767 ms while active.
- `ACQUIRED_ZONES_GAS 0x0170`: median 2010.399 ms while active.
- `PETR_MNFLD_PRESS_RV 0x018D`: median 4024.709 ms.
- `GAS_MNFLD_PRESS_RV 0x018E`: median 4029.306 ms.

These are observed behavior, not hard-coded Amarelo scheduler constants.

## Fan-out local receipts

Read-only workers ran independently on MMMACHINE:

- P1 protocol output SHA-256: `92539E84F7F4D1FADB4FA03B694322A82A4E68E0FB4DF90C3D659C77E114CF26`.
- P4 timing output SHA-256: `35C15C412C8B63D37427505FBDDB9B98F9D4682F334F9A54E9B1FDF4D87E84D4`.

Parallel exploratory outputs reserved for WU-002:
- P2 state-xrefs SHA-256: `6B93CA4967065A79C1071E71669BA79A0C2E29737BEE4FAA9997691C5E01BFFE`.
- P3 visual causal slices SHA-256: `7EF6E96362E2C978D3963166C6EE540A76D9000BB58B6773348B46CE2D4221E4`.

P2/P3 are not promoted as WU-001 conclusions; they seed WU-002.

## WU-001 verdict

**PROVEN_WITH_EXPLICIT_WIRE_GAPS**

The serial contract, object reads/writes, checksum family, corrected timing model, native curve mutation evidence, enable/disable path, and action-family generation are sufficiently proven to unblock WU-002/WU-003.

Explicit limitation:
action codes 1/2/8 are statically proven from the original binary but not observed on the wire in the two available captures.

This limitation is preserved as provenance, not guessed away.
