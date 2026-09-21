# OMEGAS Amarelo WU-001 — Action Bridge Evidence Slice 001

Issue: #73  
Parent program: #72  
Branch: `work/amarelo-wu-001-native-autocal-ground-truth`

## Sources

### ProgBase original
- Version: 4.2.0.6
- SHA-256: `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`

### PortmonAUTOCAL
- SHA-256: `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`

### PortmonLOGNOVO
- SHA-256: `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`

## Evidence grades

- **PROVEN_BY_CAPTURE** — observed byte-exact in an authoritative raw Portmon.
- **PROVEN_BY_BINARY** — directly recovered from ProgBase code/resource structure, but not observed in available captures.
- **PROVEN_BY_BINARY_AND_CAPTURE** — both agree.
- **INFERRED** — best current interpretation; not yet enough for authority.
- **UNKNOWN** — intentionally unresolved.

## Shared AutoCal action bridge

Published ProgBase handlers feed a shared dispatcher at approximately `0x517568`.

Recovered action-code mapping from the binary:

| UI action | code | evidence |
|---|---:|---|
| Reset petrol | 1 | PROVEN_BY_BINARY |
| Reset gas | 2 | PROVEN_BY_BINARY |
| Reset all | 4 | PROVEN_BY_BINARY_AND_CAPTURE |
| Manual AutoMatch | 8 | PROVEN_BY_BINARY |

The shared dispatcher passes the code to bridge `0x512280`.

### Bridge 0x512280

Disassembly proves the bridge constructs a 2-byte payload:

`[0x04, actionCode]`

and sends it through protocol service/command `0x24`.

The additive request checksum observed in the protocol produces:

| action | predicted request | capture status |
|---:|---|---|
| 1 | `02 24 04 01 2B` | NOT CAPTURED |
| 2 | `02 24 04 02 2C` | NOT CAPTURED |
| 4 | `02 24 04 04 2E` | CAPTURED in both authoritative logs |
| 8 | `02 24 04 08 32` | NOT CAPTURED |

A corpus-wide scan over raw and zipped `.LOG` files under the OMEGAS Drive found no captures of action codes 1, 2, or 8. Their frame forms therefore remain **PROVEN_BY_BINARY**, not capture-proven.

### Captured Reset All

PortmonAUTOCAL:
- request `02 24 04 04 2E`
- observed once in the unique raw source.

PortmonLOGNOVO:
- Portmon index 34971
- source line 69944
- request `02 24 04 04 2E`
- response `02 24 04 04 2E 53 00 53`

This establishes the action-code bridge format with binary + wire agreement.

## Second one-byte bridge

Bridge `0x512300` constructs a one-byte payload whose value is `0x05` and sends it through service `0x24`.

Direct xref:
- only AutoCal-module caller observed: `0x512954 -> 0x512300`.

Climbing the call graph:
- `0x518FEB -> 0x512858`
- `0x518FEB` lies inside published `ActionDeleteSelectedPointsExecute`.
- that UI path builds Boolean selection arrays from chart point selections, calls `0x512858`, then refreshes via `PostActionRefresh`.

Therefore the one-byte bridge belongs to the **delete-selected-points workflow**, not the AutoMatch/Finish workflow.

The exact wire frame corresponding to this bridge is **NOT CAPTURED** in the two mandatory logs and remains PROVEN_BY_BINARY only.

## Transport handshake classification

Frames:
- `01 00 3A 3B`
- `00 25 25`

were initially unresolved.

Raw context now shows them immediately after serial-port create/configuration:
- baud 9600;
- 8N1;
- timeout setup;
- queue setup;
- purge;
- then the two frames;
- then normal ECU object reads.

They repeat at reconnect/open sequences in LOGNOVO.

Classification: **TRANSPORT_HANDSHAKE / CONNECTION NEGOTIATION**, not an AutoCal action.

This prevents transport setup from being falsely inserted into the AutoCal state machine.

## LOGNOVO control facts locked by fixture

The companion fixture `tests/fixtures/portmon-lognovo-autocal-control-v1.json` records:
- Reset All action;
- 439 `MUL_ACT` reads;
- 7 AutoMatch counter reads;
- 3 `MAX_RPM_FOR_AUTOCAL` reads;
- 4 AutoCal-disable writes;
- 4 AutoCal-enable writes;
- repeated connection-handshake frames.

Observed `MAX_RPM_FOR_AUTOCAL` payload:
- `B8 0B` little-endian = **3000 rpm**.

Observed AutoMatch count values include:
- 0, 1, 2, 3.

## What this slice does NOT prove

- exact firmware formula for AutoMatch;
- that action 1/2/8 frames were ever emitted in available recordings;
- exact wire frame for delete-selected-points bridge;
- complete start/finish state machine;
- meaning of every AutoCal state vector or zone bit.

Those remain later WU-001/WU-002 work, not assumptions.
