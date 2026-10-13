# PLATINA-REPLIES-181 — fixed negative-response boundaries

Issue #181; Spec Kit/WorkUnit PLATINA-REPLIES-181; owner-selected `work/platina-obsidian-enxuto`.
Original host parity continuation after #180 (encoder CI38333044503 SUCCESS).

## Observation and correction

Original TAebProtocol.IsAck VA00923860 consumes status96, one fixed byte, then checksum; for CA it consumes one fixed byte, one error code, then checksum. It never uses the fixed byte as a length for these statuses. ACK53 still leaves length/data parsing to the command reader.

UsbSerialManager previously treated the second byte as LEN for every status. The length decision is now explicit:96 yields zero data bytes beyond the fixed byte,CA one code byte,other statuses retain the existing variable-length rule. The same reader,192-byte payload guard, checksum, full request echo, session fences and failure paths remain.96 is still failure/UNKNOWN; CA08/10/unknown classifications are unchanged. No automatic retry or ECU write was enabled.

The extracted helper is internal in the existing reply file, used by the existing serial transaction. No extra serial owner, decoder object, dependency or feature was added. This removes the generic-LEN assumption for the two original fixed shapes.

## Direct byte binding without reanalysis

The saved LandiRenzo Ghidra project was opened read-only using `-process ProgBase.exe -noanalysis -readOnly`, with the same trusted export method described in #179. No new binary import, decompilation, target execution or ECU access occurred. Executable SHA256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`, imagebase00400000.

`progbase-replies-saved-project-181.json`: ReadByte VA00922B0C(28 instructions), IsAck VA00923860(200), ChecksumOk VA00923B30(195). All423 instruction bytes independently mapped to the current PE's section bytes; receipt `progbase-replies-attestation-181.json`. The retained pseudocode at desktop protocol-export lines1909–1957 provides readable flow, while the attested original instructions bind the compared fixed reads to this executable.

REA lookup reused `ev_f2fd067dbbc24e27666eb781d12249b1753bdf9b0e43efc5b472aca7d6b2a161` and residual `ev_8439fb125abf59a9b3eba3342494ac4a6c6334ec42e8c5873950a651f4bd918b`; those records are resource/unknown evidence, not native dossiers. Saved-project extraction through shell is explicitly distinct from MCP native observations. The REA capture unknown `unk_1158314d1bd457d17d5c23cf1ec3ee7eb519072c754441e090ed90cdf466cd60` remains open: correcting static host grammar does not demonstrate occurrence of96 with nonzero metadata on the deployed ECU.

## RED/GREEN and limits

The RED commit `3788e44871c3e0651d5316df42acd9b1c7ccf61e` extracted the previous identity length decision without changing wire behavior and added literal original-derived synthetic replay boundaries. Eight JVM tests compiled; two failed:96 over-read and CA under-read when metadata is00. ACK variable length and the five existing reply classification tests passed. GREEN `b5565ebceb23a213baa1a1c888462a64bb199de2` changes only the status-specific payload count; all8 pass. Inputs96 01 97,96 FF 95,CA 00 10 DA,CA FF 08 D1 are synthetic original-derived examples, never claimed captured ECU traffic. Each has a following ACK frame; tests assert exact consumed size and preserved following bytes. They test the production count used by protocolTransaction, not a physical USB transaction or a new full-frame decoder.

Existing checksum and incomplete-response rejection code was inspected unchanged; the prior invalid-ACK classification regression remains green. The new boundary tests do not independently exercise Android USB callbacks, timeouts, hardware checksum faults or ECU effects. Complete remote JVM suite and Android lint must pass on the final docs-inclusive SHA; final CI receipt is tracked in #181.

No claim of total protocol/firmware equivalence. Unobserved firmware families, baud/flash behavior, retry policy differences and calibration equations remain outside demonstrated physical equivalence. No APK or vehicle validation.
