# WU-001 — AutoCAL native action wire proof v2

Date: 2026-09-21
Issue: #73
Scope: action codes 1/2/4/8 only.

## Authority
- ProgBase 4.2.0.6 SHA-256: `8A2D297C8C21FF3B4F7A47F7FE64593B0FEC9014DD938BD91022DC0C68AC36F4`.
- PortmonAUTOCAL raw SHA-256: `4A70F5AE79B1D688C05BD169F3E6A588B52105580D24B8A72A5CFF398A384C0B`.
- PortmonLOGNOVO raw SHA-256: `43A632724182C72CBD4F386EA0F7421E01D38242B48B919705671751E9EB8A64`.

## Static binary proof
`AutoCalNativeAction @ 0x512280` copies the caller action byte into its one-byte action payload at instruction `0x5122B9`, then invokes the common transport with service identifier `0x24` at `0x5122CE`.

Direct call sites into the shared dispatcher `0x517568` push exactly:
- `0x5189B4: push 8`
- `0x5189C0: push 1`
- `0x5189CC: push 2`
- `0x5189D8: push 4`

Existing ProgBase handler/resource binding identifies these respectively as AutoMatch, Reset Petrol, Reset Gas and Reset All.

## Raw protocol proof
All 36,463 ProgBase writes parsed from PortmonAUTOCAL satisfy the additive checksum contract.
PortmonLOGNOVO has 39,524 writes; 39,522 satisfy it. The only exceptions are two startup one-byte `00` writes (events 15 and 34), not framed protocol requests.

Service `0x24` action observed in both raw logs:
- PortmonAUTOCAL event 26238 / source line 52478: `02 24 04 04 2E`.
- PortmonLOGNOVO event 34971 / source line 69944: `02 24 04 04 2E`.

No action 1/2/8 service-0x24 frame occurs in either capture.

## Exact request contract
The binary uses one generic action-byte path, while the observed action-4 frame fixes the surrounding service-0x24 framing. The checksum is the low byte of the additive sum of all preceding request bytes.

| Human action | Code | Request | Classification |
|---|---:|---|---|
| Reset Petrol | 1 | `02 24 04 01 2B` | PROVEN — static generic path + framed checksum derivation |
| Reset Gas | 2 | `02 24 04 02 2C` | PROVEN — static generic path + framed checksum derivation |
| Reset All | 4 | `02 24 04 04 2E` | PROVEN — observed independently in both raw logs + static path |
| AutoMatch | 8 | `02 24 04 08 32` | PROVEN — static generic path + framed checksum derivation |

Important distinction: codes 1/2/8 are **not raw-observed in the supplied captures**. Their exact requests are proven by the original binary's generic byte construction plus the independently validated framing/checksum contract; provenance must retain that distinction.

## What this does not prove
This does not yet close start/finish semantics, state transitions after each action, acquired-zone meaning, or full host-write vs ECU-mutation attribution.
