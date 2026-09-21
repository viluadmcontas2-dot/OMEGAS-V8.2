# Research Farm Wave 001 — synthesis

Authoritative run: **35642442355** at SHA `dba9b911c1a222f58d37f5d68d97d62a31881f4d`.

## Outcome

- 160 / 160 receipts.
- 35 PASS.
- 120 INFO.
- 5 RED.
- 0 BROKEN.
- drivers: 100 source-scan, 20 symbol-scan, 40 verde-forensic.

The farm was useful as a **discovery/indexing surface**, not as final native authority.
ProgBase + raw Portmons remain the authority for AutoCAL semantics.

## What materially paid off

The wave surfaced or accelerated investigation of:
- AutoCAL slow-object shapes around 0x015B..0x0163;
- observed ~2 s AutoCAL cadence and ~4 s reference cadence;
- Portmon request/checksum structure;
- slow-read ordering and live-telemetry interleave;
- bridge/runtime serial-I/O boundaries;
- native AutoCAL action/runtime/writer/correlator code surfaces;
- acquired-zone surfaces;
- LEVELS RAW / invented-percentage violations.

Those findings were later revalidated with stronger evidence and promoted selectively into
fixtures, tests, consumer graph, WU-001/WU-002 or UI contracts.

## What did not become truth by itself

- PASS means the lane's stated check passed; it is not proof of firmware semantics.
- INFO is navigation/context, not a scientific conclusion.
- Verde-forensic findings are harvest/revalidate material, never native authority.
- RED is a useful contradiction/finding, not infrastructure failure.
- No lane may override direct ProgBase disassembly or raw Portmon evidence.

## Practical verdict

The 160-lane wave was **worth keeping as a one-time broad map**.
Repeating broad waves now would be wasteful.

Current strategy:
1. use the farm summary/artifacts to locate promising code/symbols;
2. test the exact hypothesis against ProgBase/raw captures;
3. promote only compact, reproducible evidence into Amarelo;
4. launch new targeted lanes only when they can answer a concrete unresolved question.

This keeps the farm as an accelerator instead of turning it into a second source of truth.
