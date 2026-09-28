# ProgBase host parity — AutoCal

Canonical tracker: #104

## Mission

Reproduce the **host-side** AutoCal behavior of ProgBase 4.2.0.6 inside
OmegasVerde. The ECU remains the authority for its internal AutoMatch
calculation; Omegas does not replace that logic.

Modernization is allowed above the MP48 compatibility boundary: clearer UX,
live telemetry, receipts, session persistence, replay, diagnostics and stronger
readback/error handling. It must not silently replace a proven ProgBase
operation with an inferred local algorithm.

## Product workflow

The normal operator workflow is intentionally small:

1. Iniciar aquisição.
2. Acompanhar gasolina/GNV, cursor AGORA and acquisition progress.
3. Readquirir a fuel/point only when needed.
4. Finalizar AutoCal.
5. Accept success only after ECU ACK + readback.

Technical operations (Manual AutoMatch, Reset K, Reset All) remain behind the
advanced area. `Finish AutoMatch` remains implemented for protocol parity but
is not exposed as a second user-facing finish choice.

## Canonical operation matrix

| Operation | Canonical ProgBase evidence | OmegasVerde | Verdict |
|---|---|---|---|
| Enable AutoCal | AUTO_CAL_ENABLE exact SetNumber | one-touch write + readback | MATCH_PROVEN |
| Pause AutoCal | AUTO_CAL_ENABLE=0 | one-touch write + readback | MATCH_PROVEN |
| Reset petrol | command 0x24 / sub-op 0x04 / mode 0x01 | native action + ACK/readback | MATCH_PROVEN |
| Reset gas | command 0x24 / sub-op 0x04 / mode 0x02 | native action + ACK/readback | MATCH_PROVEN |
| Reset all acquisition | command 0x24 / sub-op 0x04 / mode 0x04 | native action + ACK/readback | MATCH_PROVEN |
| Manual AutoMatch | ActionAutoMatchExecute -> mode 0x08 | advanced action only | MATCH_PROVEN |
| Finish AutoCal | TAutoCalDM +0x7C (VECT_AUTOCAL_U8_1 / 0x0165:index1) -> +0xCC (VECT_AUTOCAL_U8_0 / 0x0165 scalar), Sleep(100), refresh tail | row1 -> row0 SetNumber, ACK/readback, fresh snapshot, receipt | MATCH_PROVEN_IMPLEMENTED |
| Finish AutoMatch | same +0x7C -> +0xCC scalar copy, without 100 ms/UI tail | backend parity action, not exposed as normal UI choice | MATCH_PROVEN_IMPLEMENTED |
| Reset K | ActionResetKFactorExecute loops MUL_ACT and calls TAebVector_SetDouble(1.0) | 30 indexed writes of Q14 0x4000 + complete MUL_ACT readback | MATCH_PROVEN_IMPLEMENTED |
| Delete/reacquire point | native masks + commit | point-level reacquisition + readback | MATCH_STRUCTURAL_ONLY |
| Reference editor | TFormRifAutocal 18 Tinj + 18 MAP controls | read/display exists; exact mutation path not yet promoted | EVIDENCE_GATED |
| ExportToK | ProgBase action exists | no mutation promoted until exact host semantics are closed | EVIDENCE_GATED |
| Native AutoMatch equation | internal ECU behavior | observed, never replaced by local formula | OUT_OF_SCOPE |

## Finish AutoCal — canonical proof

Direct disassembly of canonical ProgBase
SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`
establishes:

- `ActionFinishAutocalExecute @ 0x0051A390`
  - reads object field `TAutoCalDM+0x7C`;
  - assigns that scalar to `TAutoCalDM+0xCC`;
  - sleeps 100 ms;
  - invokes refresh/render tail `0x005162F8`.
- `BtnFinishAutomatchClick @ 0x0051A454`
  - performs the same scalar copy;
  - omits the 100 ms/tail.

The decisive evidence is the Delphi class field RTTI recovered from the same
canonical DUMP. It maps the instance fields directly:

- `+0x7C = VECT_AUTOCAL_U8_1 = SerialCode 0x0165, RowIndex 1`;
- `+0x80 = VECT_AUTOCAL_U8_2 = MAX_AUTOMATCH = SerialCode 0x0165, RowIndex 2`;
- `+0xCC = VECT_AUTOCAL_U8_0 = SerialCode 0x0165, default/scalar element`;
- `+0xD0 = NUM_ATUOMATCH_EXECUTED = SerialCode 0x0174`.

Therefore the compatibility mutation is:

```text
VECT_AUTOCAL_U8_1  ->  VECT_AUTOCAL_U8_0
      0x0165:1             0x0165 scalar
```

A previous audit checkpoint incorrectly mapped `+0x7C/+0xCC` to
`MAX_AUTOMATCH/NUM_AUTOMATCH_EXECUTED`. Exact Delphi field RTTI falsified that
interpretation. That hypothesis is superseded and must not be reintroduced.

Omegas mirrors the proven host assignment, then requires:
- write ACK;
- readback of `VECT_AUTOCAL_U8_0` equal to the source row1 value;
- fresh native snapshot;
- durable receipt.

This adds fail-closed verification without changing the ProgBase mutation.

## Reset K — canonical proof

`ActionResetKFactorExecute @ 0x0051A070` iterates over the native
`MUL_ACT` vector (`TAutoCalDM+0xA0`) and calls
`TAebVector_SetDouble @ 0x00979EE8` with **1.0** for each element.

For the 30-point Q14 multiplier vector:
- factor 1.0 = raw `0x4000`;
- Omegas writes each indexed point through SetNumber;
- after all 30 ACKs, Omegas rereads full `MUL_ACT`;
- success requires all 30 values to return `0x4000`.

`VECT_AUTOCAL_EE 0x0164[4]` is a separate persistent AutoCal surface. It is
**not** used as a substitute for the proven Reset-K path.

## Acquisition and realtime UX

The compatibility data remains native:
- 18 petrol acquisition counters;
- 18 gas acquisition counters;
- current and previous gas buffers;
- acquisition-zone flags;
- native reference/K vectors.

Presentation layer:
- live cursor AGORA uses fast telemetry cadence;
- AutoCal state/projection refreshes at the 1 s status cadence;
- acquisition dots distinguish `COLLECTING` from `ACQUIRED`;
- collecting dots show native counter/threshold progress;
- acquired status is shown only after the native threshold is reached;
- no UI progress state changes ECU criteria.

## Persistence and failure semantics

A mutating action is never considered successful from a button tap alone.

For Finish/Reset/point operations Omegas requires the applicable combination of:
- same physical USB session;
- interlock ownership;
- write ACK;
- post-write ECU readback;
- fresh native snapshot;
- durable atomic action receipt;
- SessionRecorder event/snapshot persistence.

If write/readback diverges, the UI reports **Não concluído**. It must not present
a synthetic completed state.

Historical sessions may be displayed as history, but they must not be promoted
as current ECU truth after reconnect.

## UI rule

The operator must not need to understand the distinction between internal
`Finish AutoCal` and `Finish AutoMatch`.

Normal screen:
- Iniciar/Pausar aquisição;
- Readquirir GNV;
- Readquirir gasolina;
- **Finalizar AutoCal**.

Advanced tools:
- Manual AutoMatch;
- Resetar Curva K para 1.0;
- Nova aquisição completa.

Backend-only parity may retain additional ProgBase actions for diagnostics/tests
without adding duplicate user choices.

## Remaining evidence-gated work

No write will be guessed for:
1. the 18+18 reference editor lifecycle;
2. ExportToK.

Those paths can be promoted only after their exact ProgBase host bindings and
commit/readback sequence are proven from the authorized corpus.

APK generation remains outside this WU unless explicitly authorized.
