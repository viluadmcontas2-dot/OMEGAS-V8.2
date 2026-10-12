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
## Follow-up: retained decompilation recovered without reopening the binary

After the first documentation commit, a targeted search of the owner's existing Claude session found `ghidra_protocol_output.txt` on the Desktop and the historical `ExportProtocol.java` launch. Full file: 275576 bytes, SHA-256 `38371eddb21ba54f505ad94f1e629c8a73be3eaa7f4d2a87b517ffc76cef9507`. Eleven relevant complete exported function sections are retained in `docs/evidence/progbase-retained-protocol-export-v1.json`, each with original line range and excerpt digest. This addendum supersedes the evidence levels in the preliminary six-question table above.

The historical launch at session `afb88a05-9cb1-4561-a120-d19dbc8a49c2`, event line 686, imports the same ProgBase path with ExportProtocol.java into project LandiRenzo. The current executable was hashed read-only and matches the repo's canonical identity. Important limitation: the old text does not embed an input hash, so historical provenance plus current matching path/hash is not a cryptographic proof that every old decompiled byte came from this exact revision. This is HISTORICAL_STATIC_EXPORT evidence, not a new REA Evidence observation or live ECU test.

### Answers supported by the retained static export

1. **GetByAddress** (`009259C4`, lines 3456–3518): the builder computes opcode from address width only (`2|68=6A`, `4|68=6C`), sends flags, then SendInt(address), optionally SendInt(address>>16), then checksum. SendInt emits low byte then high byte. Therefore there are **3 or 5 bytes after opcode before checksum**, or **4 or 6 including checksum**. Bit 80 selects response width, not an extra request byte or different opcode. The report 06 formula adding one for bit 80 is contradicted by the retained function.
2. **SetVector v2 extended** (`0092617C`, lines 3915–4039; row overload `009263B0`, 4040–4166): send opcode, addrLo, extended length, addrHi, optional row, values, checksum. For payload excluding the address, extended length is payload.size+2. This supports the current repository grammar; reports 07/14 have the length byte in the wrong position. Compact remains valid through five payload bytes in v2.
3. **SendHEXflash** (`00928968`, lines 5837–5968): the count sent is **data_len+3**, not +2. The function then sends address high/low, reverses the order within each two-byte data pair, complements the accumulator, and calls SendChecksum. EOF also calls SendChecksum after `53 39 03 FF FF`. Consequently the report's displayed frames omit a trailing transmitted checksum, and `[data_bytes...]` conceals pair swapping. Odd-length input behavior/ECU acceptance is not established; the visible loop processes complete pairs only. Do not implement a generic HEX writer from the report alone.
4. **PreparaProg** (`00927D80`, lines 5276–5440) passes the caller's flash address into an AEB_DATA with count=1,size=1 and calls SetNumber(...,FFFFFFFF). **SetNumber narrows to one transmitted FF byte**, as shown by its size-1 branch (`00925AFC`, 3519–3648). There is no universal hardcoded flash address in this routine. The retained app-layer caller obtains its argument from an object field; a specific ECU/module address remains a runtime/configuration-dependent unknown. No address is invented.
5. **Legacy SetEcuBaudRate** (`009247F0`, lines 2552–2603) sends `02 03`, SendByte(value>>8), SendByte(value&FF). SendByte accepts a char and writes one byte. **If the passed value is 115200 (1C200), the transmitted value bytes are C2 00**, i.e. the low 16 bits, not a three-byte encoding or the new setter's half-rate rule. This answers host serialization only; it does not establish that a caller passes nominal 115200 or that a physical ECU supports that request. Nominal table labels and divisor interpretations remain separate.
6. **Progflash** (`0092839C`, lines 5602–5637) sends 93 plus checksum, makes two one-byte reads, and succeeds only when the two bytes match and equal signed `-0x59` = **A7**. The loop allows four attempts. “Receives A7” is supported; a single isolated A7 is an incomplete description of its success condition.

### Reusable boundaries

The exported Address headers are 0x400000 above hexadecimal comments in these sections. Both representations are retained; do not hand either blindly to a new analysis session without reconciling image base. Decompiled signatures and extraout variables are artifacts. Conclusions above use explicit send/read operations and dependency functions, not recovered argument names alone.

All six questions now have supported host-side answers or an explicit parameter-dependent boundary. Still unproved: historical export's embedded binary binding, a universal boot address, the actual old-setter caller value for every ECU, odd HEX record policy, and physical ECU acceptance/effects. No protocol implementation changes are justified by these remaining unknowns.