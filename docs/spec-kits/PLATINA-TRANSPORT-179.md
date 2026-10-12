# PLATINA-TRANSPORT-179 — Spec Kit

- Issue:#179.
- Branch:`work/platina-obsidian-enxuto`.
- WorkUnit:`docs/workunits/PLATINA-TRANSPORT-179.md`.
- Evidence:`docs/evidence/PLATINA-TRANSPORT-179.md`.

## Requirement

An invalid reply beginning with ACK status53 must not authorize the MP48 handshake's success branch. Preserve classification of valid ACK and valid negative CA replies. Use retained ProgBase evidence before native reanalysis; retain actual REA observations and unknowns with clear authority.

## Acceptance

Focused invalid-ACK regression demonstrates RED on exact remote source, then all existing reply tests plus regression pass on the fix SHA. Remote diff/readback and applicable CI are inspected. Current original executable identity, DFM fixture binding, saved-project instruction-byte attestation and catalog applicability are retained. No claim of physical ECU validation.

## Non-goals

No0x96 wire-format change without captured evidence, no timeout rewrite, firmware/ECU mutation, APK publication, SIL/CIU or unrelated target/tool execution.