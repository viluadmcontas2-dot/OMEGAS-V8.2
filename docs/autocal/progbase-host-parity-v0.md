# ProgBase host parity — AutoCal

Canonical tracker: #104

## Mission

Reproduce the host-side behavior of ProgBase 4.2.0.6 inside OmegasVerde.

The ECU remains a black box for native AutoMatch. Omegas does not need the ECU's
internal AutoMatch equation to be faithful to ProgBase.

## Architecture boundary

```text
ProgBase host contract
  reads native objects
  starts/stops acquisition
  invokes proven native commands
  renders/progresses state
  edits references through proven host paths
  finishes/exports through proven host paths
           |
           v
       MP48 protocol
           |
           v
         ECU
  native acquisition + AutoMatch
```

Omegas may modernize everything above the MP48 compatibility boundary:
- clearer UI;
- state explanations;
- live comparison;
- event ledger;
- replay;
- deterministic tests;
- simulator;
- diagnostics;
- fewer clicks;
- no duplicate confirmations;
- stronger error handling.

It must not replace a proven ProgBase operation with an inferred local algorithm.

## Operation matrix

| Operation | ProgBase evidence | OmegasVerde current path | Verdict |
|---|---|---|---|
| Enable AutoCal | AUTO_CAL_ENABLE / exact host write | AutoCalProtocol.setEnabled + one-touch bridge | MATCH_PROVEN |
| Pause AutoCal | AUTO_CAL_ENABLE / exact host write | AutoCalProtocol.setEnabled(false) + readback | MATCH_PROVEN |
| Reset petrol acquisition | ActionResetPetrolExecute -> 0x24/0x04/0x01 | AutoCalNativeActionManager.RESET_PETROL | MATCH_PROVEN |
| Reset gas acquisition | ActionResetGasExecute -> 0x24/0x04/0x02 | AutoCalNativeActionManager.RESET_GAS | MATCH_PROVEN |
| Reset all acquisition | ActionResetAllExecute -> 0x24/0x04/0x04 | AutoCalNativeActionManager.RESET_ALL | MATCH_PROVEN |
| Manual AutoMatch | ActionAutoMatchExecute -> 0x24/0x04/0x08 | Manager had action; bridge now allows prepare + execute | MATCH_PROVEN_AFTER_FIX |
| Reset K factor | ActionResetKFactorExecute writes MUL_ACT elements to 1.0 | K-factor reset path through existing writer/readback | MATCH_STRUCTURAL_ONLY |
| Delete/reacquire selected point | Chart selection + ActionDeleteSelectedPointsExecute | AutoCalPointDeleteProtocol + point UI | MATCH_STRUCTURAL_ONLY |
| 18-point reference editor | TFormRifAutocal EditRifInj0..17 + EditRifMap0..17 | reference surface exists; full write lifecycle not frozen | NOT_FULLY_COMPARED |
| 18/30 configuration | ProgBase mode/grid helpers | current field/grid handling | MATCH_STRUCTURAL_ONLY |
| Finish AutoCal | U8_1 -> U8_0; indexed U8 write 0x13; 100 ms; refresh tail | AutoCalNativeActionManager FINISH_AUTOCAL + readback + receipt + fresh snapshot | MATCH_PROVEN |
| Finish AutoMatch | U8_1 -> U8_0 without FinishAutocal wait/UI tail | AutoCalNativeActionManager FINISH_AUTOMATCH + readback | MATCH_PROVEN |
| ExportToK | ProgBase action exists | current K paths exist; exact action parity not frozen | NOT_FULLY_COMPARED |
| Native AutoMatch equation | ECU behavior | observed only; no local replacement permitted | OUT_OF_SCOPE |

## Critical product rule

Classes such as `AutoMatchV5Engine` and other inferred analysis may remain as
diagnostic/research tooling only. Their output must never silently substitute for
native ProgBase/ECU behavior in the AutoCal compatibility core.

## First closed mismatch

Before #104, OmegasVerde displayed `MANUAL_AUTOMATCH` and
`AutoCalNativeActionManager` carried the proven command, but
`AutoCalJavascriptBridge` rejected that action in its prepare/execute allowlists.

Fixed on OmegasVerde:
- bridge commit `17961562fb7e7e80a92559bdb2f201ef40c3e33b`;
- regression contract commit `a8a40cfd240bf647af00b8942d69b90892dd318b`.

## Next closure order

1. Prove exact Reset-K behavior end-to-end against the recovered ProgBase routine.
2. Freeze point-delete masks/write sequence against ProgBase.
3. Freeze reference-editor read/write lifecycle.
4. Resolve FinishAutoCal / FinishAutoMatch host semantics.
5. Resolve ExportToK host semantics.
6. Only then declare host-parity freeze.

APK generation is outside this WU unless explicitly authorized.


## 2026-09-28 closure — Finish + operational refresh

Finish is now byte-grounded:
- `VECT_AUTOCAL_U8_1 = 0x0165:index1`;
- `VECT_AUTOCAL_U8_0 = 0x0165:index0`;
- ProgBase one-index U8 setter emits `0x13`;
- write body: `13 65 01 00 <value>` + additive checksum;
- example value 6: `13 65 01 00 06 7F`;
- Finish AutoCal preserves the observed 100 ms settle;
- post-finish ProgBase tail is refresh/render, not another proved mutation.

OmegasVerde now mirrors that with source read, indexed commit, ACK, target
readback equality, native receipt persistence and fresh snapshot request.

Operational acquisition refresh was also moved to a grouped READ_ONLY serial
unit and a 1 Hz eligibility/tick. Reference/MUL refresh remains 4 s. The MP48
scheduler remains the only serial authority and telemetry receives one handoff
after each grouped refresh.
