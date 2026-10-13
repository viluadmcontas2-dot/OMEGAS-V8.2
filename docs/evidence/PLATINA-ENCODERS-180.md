# PLATINA-ENCODERS-180 — original protocol parity and consolidation

Issue #180, Spec Kit/WorkUnit PLATINA-ENCODERS-180, branch `work/platina-obsidian-enxuto`.
Baseline `82f6184481b6ed6e7b0eee1f2bb33aa01ab838c5`; production fix `ee2b0637ec3ac57ca420daf5ee914c30d35e27fe`.

## Confirmed discrepancy and result

Original `TAebProtocol.SetVector`, VA0092617C, retained export lines3915–4039, computes v2 `local_1c=count*width+1`, compact opcode `local_1c|0x30` when local_1c<7, otherwise37. It emits addrLo, optional extended length, addrHi, then values and SUM8. The 1/2-element U16 helpers previously always emitted37; they now emit33/35, with no extended length byte. Three or more U16 remain37. There is no current observed production caller for writeVectorU16 in the inspected inventory; this is encoder parity, not an observed vehicle failure.

U8 and U16 typed writers now share one private frameVector routine. Three KFactor methods delegate to existing AutoCal readVector/writeIndexedU16; original index0..29 and raw0..FFFF validation remains. Production diff removes17 net lines (21 additions/38 deletions) across two files. No new transaction owner, feature, ECU mutation, automatic calibration or API integration was introduced.

## Evidence reuse

REA canonical bundle lookup returned the retained resource evidence `ev_f2fd067dbbc24e27666eb781d12249b1753bdf9b0e43efc5b472aca7d6b2a161` and transport residual record `ev_8439fb125abf59a9b3eba3342494ac4a6c6334ec42e8c5873950a651f4bd918b`. These records do not prove vector encoding; the relevant native instruction evidence is the existing saved-project export and attestation from #179: `progbase-saved-project-179.json` and `progbase-saved-project-attestation-179.json`. SetVector overloads are among the seven functions whose1198 instruction bytes were checked against executable SHA256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`. Historical pseudocode plus matching native instructions supports the serialized branch; static analysis does not observe ECU acceptance. No duplicate import, analysis, or native launch was necessary.

## Verification

RED `a00b9cac5af2c9e85ea5bdc66f7ec52b4344d64f`: compilation succeeded,31 JVM tests ran, exactly two failed at the compact opcode/length boundary. GREEN `ee2b0637ec3ac57ca420daf5ee914c30d35e27fe`: all31 passed from exact GitHub source/test bytes. Literal frame expectations are independently calculated; the extended example checksum is SUM8(671)=9F. An intermediate test checksum edit introduced03 incorrectly; it was repaired before the accepted RED run. No production change was made to accommodate that invalid expectation.

Coverage includes1/2/3 U16 elements, U8 five/six-byte compact boundary, LE values, literal checksums,126-element maximum and127 rejection, invalid values/address, existing AutoCal fields/actions/scales/decoders, existing K reads/writes/scales and K index/raw bounds. Cached Kotlin2.2.10, JVM target17, Java21, JUnit4.13.2. Receipts: `encoders-180-test-receipts.json`. Full remote CI is required on the final docs-inclusive SHA; run receipt belongs in #180.

## Bounded parity ledger

| Surface | Result | Remaining boundary |
|---|---|---|
| AutoCal scalar/indexed U8/U16 encoders | Consistent with retained SetNumber branch | No universal firmware-effect claim |
| AutoCal vector U8/U16 | Short-U16 discrepancy fixed; shared framing | Only the existing two-byte addressed protocol variant |
| KFactor reads/indexed writes | Byte-equivalent consolidation | ACK/readback and human intent remain in managers |
| AutoCal action modes | Existing v2 original-derived contract retained | AGENTS broad-reset naming conflict remains documented in #178 |
| Invalid ACK classification | Existing #179 fix retained | Not physical USB validation |
| Status96 and CA grammar | Separate unresolved transport gap | Original fixed-byte reads differ from generic LEN parser; actual unobserved variants need capture/replay evidence |
| Timeouts/retry/baud/flash | No speculative changes | Different defaults alone do not prove failure; no new flash path |
| Firmware calibration formulas and sensor interpretation | Unknown beyond established host evidence | PC host cannot establish ECU firmware algorithm |

This unit completes an encoder correction and redundancy removal. It does not establish zero differences across the entire original application or every ECU. No APK or physical vehicle validation is claimed.
