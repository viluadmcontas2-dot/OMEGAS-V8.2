# PLATINA-TRANSPORT-179 — invalid ACK and REA evidence continuation

Issue: #179. Branch: `work/platina-obsidian-enxuto` (owner-selected).
Spec Kit and WorkUnit: `PLATINA-TRANSPORT-179`.

## Observable defect and minimal fix

`UsbSerialManager` returns `ok=false,status=53` for malformed/incomplete replies and failed checksum validation. Previously `UsbProtocolReply.statusClass` still classified those replies as ACK, and the first-handshake switch in `ResponseDrivenEcuEngine` could advance to init2 and online state.

The reply property now classifies only invalid ACK-shaped replies as UNKNOWN. Valid ACK still maps to ACK. Valid negative CA replies intentionally have ok=false; retryable08, nonretryable10, and unknown CA behavior are preserved. No wire bytes, timeout, retry policy, ECU writes or APK publication changed.

## RED -> GREEN, exact remote source

- Test-only remote commit: `0c2fe99d5a469b617fcbff10752afe600bb60e7a`.
- Kotlin/JUnit compilation succeeded, then the new regression failed for the intended reason: expected UNKNOWN but was ACK. Five tests executed, one failed.
- Fix remote commit: `d58c736290faad49a64a3fdb35d2753c05f93f06`.
- The same five tests passed against source/test bytes fetched from that exact SHA through GitHub API.
- Compiler: cached Kotlin 2.2.10, Java21, JVM target17; JUnit4.13.2. No dependency installation or local repository source editing.
- Receipts: `transport-179-test-receipts.json`. An independent read-only reviewer inspected the integrated remote diff and handshake consumer; no blocker found. Reviewer did not execute tests.

This test exercises the production reply classification seam, not a physical USB/ECU handshake. The control-flow consequence is established by the consumer's UNKNOWN branch rejecting init1 before init2.

## REA resource evidence

`inspect_pe_resources` produced shipped-artifact observed Evidence `ev_f2fd067dbbc24e27666eb781d12249b1753bdf9b0e43efc5b472aca7d6b2a161` for executable SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`. Coverage:312 entries,152 resources,complete. TAUTOCALDM is resource index96, file offset9191468,14428 bytes, SHA-256 `96ba3d934bf66593f919e22e37e2a90a171be5c765530e39d484ecfc16c1ee86`.

The resource bytes exactly match `tests/fixtures/progbase-tautocaldm-dfm.bin.b64`. This directly binds the retained DFM fixture to the current executable. Other resource payload semantics remain opaque in the REA parser; icon decoding, Windows loading and UI execution are not observed.

Complete canonical REA bundle: `rea-progbase-179.json` (two records,one residual unknown). It contains the resource inventory and the unknown mutation record, not a native dossier or a process capture.

## Reuse of saved Ghidra analysis

REA's installed Ghidra launcher always imports into an ephemeral project; it does not expose opening an existing .gpr. To avoid repeating the complete analysis, the existing LandiRenzo project was opened via Ghidra headless with `-process ProgBase.exe -noanalysis -readOnly`. A trusted read-only script verified currentProgram.getExecutableSHA256 against the canonical executable and exported existing instructions for seven functions; it did not execute ProgBase or decompile/reanalyze it.

`progbase-saved-project-179.json` preserves the seven function identities and1198 instructions. `progbase-saved-project-attestation-179.json` records independent verification of every instruction byte against the original PE file's mapped section bytes. All1198 matched. Saved-program embedded digest and current executable hash also match.

Address ambiguity from #178 is now reconciled: image base is00400000, dump Address headers are virtual addresses, and each corresponding hexadecimal export comment is an RVA. For all seven selected functions, `comment RVA+image base=header VA=saved project entry`. Do not pass an RVA as a VA to a live provider.

These checks strengthen source-byte binding for selected instructions and the DFM resource. They do not make the old pseudocode original source or prove every old report claim/ECU effect.

## Retained transport findings and unknowns

- Original IsAck in retained dump lines1909–1920 consumes one fixed byte after96 and then checksum. Current APK generic status/LEN parser instead treats that byte as payload length. Synthetic example `96 01 97` illustrates the difference; it is NOT a captured ECU response.
- REA residual unknown `unk_1158314d1bd457d17d5c23cf1ec3ee7eb519072c754441e090ed90cdf466cd60` asks whether deployed ECU captures actually contain this nonzero following byte. No speculative0x96 parser change was made.
- CA frames01 08 and01 10 fit current parser. Original bitmask treatment and generic CA lengths differ; unobserved variants remain unknown.
- SUM8 checksum and full echo verification are consistent for currently supported frames; no evidence supports replacing SUM8 with CRC or including request echo in response checksum.
- Timeout/retry defaults differ but do not alone prove a defect. The guide's retry pseudocode omits parts of the original ComFail logic; do not copy it verbatim.

## Tool-family applicability

`rea-tool-applicability-179.json` inventories every advertised REA tool and its observed availability at the selected session boundary. Applicable routes used here: native PE resource inventory, session/evidence lookup, unknown registration and bundle export, plus existing native database inspection through the shell. Each operation serves a concrete evidence gap.

Process capture was attempted and is unsupported_host on Windows; its availability requires Linux/macOS. No process-capture Evidence was produced. Shell Ghidra output is explicitly separate. Android/ADB, Apple/Swift, JavaScript/web/Electron, managed/JEB, firmware/Linux-core and EVM workflows have no corresponding selected target in this task. They were not invoked just to exercise tool names. Mutating annotation/device/uninstall tools are unnecessary for protocol parity.

Remote CI for the final documentation-inclusive SHA is tracked in #179. Local focused GREEN and source-byte attestation do not imply remote CI or vehicle validation.