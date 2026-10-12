# PLATINA-PROTOCOL-AUDIT-178 — retained evidence ledger

Issue: #178. Spec: `docs/spec-kits/PLATINA-PROTOCOL-AUDIT-178.md`. WorkUnit: `docs/workunits/PLATINA-PROTOCOL-AUDIT-178.md`.
Owner-selected branch: `work/platina-obsidian-enxuto`.
Base: `c8b24f4271c364403474e35412e6f60123d51482`.
Date: 2026-10-12.

## Reuse boundary

The owner supplied the folder `Protocolo Landirenzo`: 17 Markdown reports and one Python utility. The adjacent manifest identifies every supplied file by SHA-256 and byte count. The reports claim Ghidra/Portmon provenance; this audit inspected their text, not all underlying decompilations/captures. Their completeness scores are historical claims, not this audit's coverage measure.

Consult retained reports and versioned fixtures first. New binary work must name an unresolved question and a focused function/probe. Reuse a matching analysis session/snapshot; do not reopen or broadly reanalyze a binary just because a conversation resumed. Never equate static evidence with an ECU execution.

Original executable identity recorded by the existing fixture and prior REA selection: ProgBase 4.2.0.6, SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`. No fresh decompilation result was obtained in this audit. A prior broad search produced no usable returned evidence and is not cited as proof.

## Action identity: documentation repair

`tests/fixtures/progbase-autocal-action-map-v1.json` contains schema `omegas.progbase.autocal-action-map.v2`, ORIGINAL_DERIVED, and records the original hash, handler identities and addresses, and an Atlas proof/artifact reference. Its retained contract agrees with `AutoCalProtocol.ManualActionMode`, `AutoCalNativeActionManager`, and the detailed current STATUS action section:

| Host action | Handler VA | Mode | Frame |
|---|---|---|---|
| Manual AutoMatch | 005189B4 | 08 | 02 24 04 08 32 |
| Reset petrol | 005189C0 | 01 | 02 24 04 01 2B |
| Reset gas | 005189CC | 02 | 02 24 04 02 2C |
| Reset all | 005189D8 | 04 | 02 24 04 04 2E |

Modify map refs and reset K use separate paths. Historical issue #53 and the old WU-006 paragraph contain an earlier interpretation; the WorkUnit paragraph is corrected to the retained v2 contract. No wire behavior is changed.

Residual terminology conflict: AGENTS calls the operational broad-reset 04 action “Reset gas point”, while the retained v2 host map calls 04 RESET_ALL. Preserve the operational safety requirements (manual intent, backup where required, ACK/readback); this documentation audit does not redefine product policy or infer selective firmware effects from host labels.

## Six questions recovered from the Claude session

| Question | Existing evidence | Finding/status | Minimal remaining probe |
|---|---|---|---|
| GetByAddress 6A/6C bytes | Reports 06 and 12 | PARTIAL: prose gives flags plus 2/4 address bytes. Report 06 opcode formula also varies with flags bit 80, contradicting fixed-opcode prose. | Recover the exact GetByAddress builder and call signature, including whether flags are a wire byte; corroborate with a captured request if available. |
| Extended SetVector v2 order | Reports 07/14; AebProtocolFramesV7.setVector; AutoCalProtocol.writeVectorU8; current dump fixture/contracts | DISCREPANCY: reports put length directly after 37. Current source/fixtures use `37 addrLo (payload.size+2) addrHi payload checksum`, compact for payload <=5. Preserve current implementation; local report prose does not override retained dump evidence. | Only if remaining doubt affects a change: compare retained raw builder/capture at a specific extended frame. |
| SendHEXflash data length | Report 09 | REPORT_SUPPORTED: data_len+3, EOF `53 39 03 FF FF`. This is not new binary proof. Report 16 S19 pseudocode uses a different length expression; do not merge them without establishing each format. | Exact HEX sender routine, including any trailing byte/checksum omitted by the report. |
| PreparaProg flash_addr/value size | Report 09 | PARTIAL: flash_addr is an argument, not a concrete recovered ECU address. Pseudocode shows size=1 and value=FFFFFFFF; narrowing/conversion semantics remain unknown. | Caller-to-builder argument flow and emitted byte count. Do not flash a device to answer this. |
| Old SetEcuBaudRate 115200 in BE16 | Reports 09/10/16 | UNRESOLVED: 115200 exceeds uint16. Reports mix nominal rates, table divisors and a new-format half-rate. Neither truncation nor reusing the new-format rule is justified. | Trace old setter parameter and callers through the two emitted bytes; distinguish nominal host baud from wire representation. |
| Progflash 93 receives A7 | Report 09 | REPORT_SUPPORTED: A7 response with up to four attempts. No live ECU observation in this audit. | Retained Progflash receive comparison or matching historical capture if stronger proof is required. |

## Additional boundaries

Report 15's broad denial of auto-calibration must not remove AutoCal/AutoMatch features: current repo evidence includes ECU-owned acquisition/AutoMatch. A transport capability statement is insufficient to negate ECU behavior. Legacy dump v1 and current v2 contracts must remain distinct.

No protocol implementation, flash/baud logic, ECU state, K writer, UI, or APK changed. No SIL/CIU content was imported. The six-question investigation remains partially unresolved; the completed unit is evidence reuse and documentary reconciliation.

## Validation

At the exact base SHA, clean read-only checkout:
- `python -I -B tests/test_progbase_autocal_action_map_contract.py`: PASS.
- `python -I -B tests/test_progbase_autocal_dump_contract.py`: PASS.
- `python -I -B tests/test_progbase_autocal_original_oracle_contract.py`: PASS.
- `python -I -B tests/test_progbase_finish_rtti_oracle.py`: four tests OK.

These are retained-contract/static checks, not proof of physical ECU behavior or a new remote CI run. Publication is documentation-only; verify remote tree/readback and branch movement before closing #178.