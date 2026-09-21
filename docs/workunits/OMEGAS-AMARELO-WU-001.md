# OMEGAS-AMARELO-WU-001 — Ground truth byte a byte do AutoCAL

Issue: #73
Estado: ACTIVE
Depende de: spec kit aprovado.

## Resultado observável
Tabela e parser/timeline que explicam, com raw bytes, as ações e objetos AutoCAL relevantes nos dois Portmon.

## Entrada
ProgBase 4.2.0.6, PortmonAUTOCAL, PortmonLOGNOVO, relatório SIL existente e fixture real do Verde.

## Escopo
- actions 1/2/4/8;
- enable/disable/start/finish/reset;
- 0x014A..0x018E relevantes;
- checksum/timing;
- MUL_ACT/counter transitions;
- host write vs ECU mutation.

## Não escopo
UI nova, Mapa K, APK.

## Verificação
- parser determinístico;
- fixtures extraídos dos dois logs;
- cada fato com hash + índice;
- nenhuma fórmula OMEGAS como ground truth.

## Verde gate
Antes de começar: atualizar #80 com HEAD e delta.

## Continuidade remota — 2026-09-21
- Verde watermark: `eeefaaa4d8371e3c4c6cf1260f1ee4b65e836618`.
- Research Farm authoritative run: `35642442355` — SUCCESS, 160/160 receipts, BROKEN=0.
- Enable Auto Calibration (0x014A) já possui contrato byte-exato comprovado.
- Ação 4 / Reset All possui frame observado nos dois Portmon.
- Fechamento não depende mais de inventar um `start` separado: `AUTO_CAL_ENABLE` é o controle host nativo do modo. Restam principalmente efeito físico exato do Finish, atribuição completa host-write vs ECU-mutation e unknowns de firmware ainda não observáveis nos raws.


## Gate action-wire — 2026-09-21

### PROVEN
A família nativa de ações está fechada no nível de request:
- Reset Petrol (1): `02 24 04 01 2B`;
- Reset Gas (2): `02 24 04 02 2C`;
- Reset All (4): `02 24 04 04 2E`;
- AutoMatch (8): `02 24 04 08 32`.

Proveniência:
- ProgBase original prova dispatcher 1/2/4/8 e bridge genérico `0x512280`;
- ambos os Portmon observam diretamente o frame de ação 4;
- 1/2/8 são binary-proven, wire-unobserved nos dois captures atuais;
- checksum aditivo foi verificado em 36.463/36.463 writes do AUTOCAL e em 39.522/39.524 writes do LOGNOVO; as duas exceções são writes startup `00`, fora do framing protocolar.

Evidence:
- `docs/omegas-amarelo/evidence/WU-001-action-wire-proof-v2.md`;
- `tests/fixtures/amarelo-autocal-action-wire-v1.json`;
- `tests/test_amarelo_autocal_action_wire_evidence.py`.

### CI
- SHA: `584fed6fc432a460576558c74c511428d51ec5c2`
- workflow: OMEGAS Amarelo fast contracts
- run: `35645763077`
- result: **SUCCESS**

### Remaining closure blockers
- exact ECU-side effect of Finish after the statically proven `NUM_AUTOMATCH_EXECUTED := MAX_AUTOMATCH` assignment and connection-aware commit path;
- any ECU/physical state machine distinct from the already-proven refresh scheduler;
- exact physical labels/order of acquired-zone indices 0..3;
- complete host-write vs ECU-mutation attribution for remaining native mutations.

### Enable / automatic AutoMatch coupling — 2026-09-21
- ProgBase resource binds `CheckAutoCalEnable` directly to `AutoCalDM.AUTO_CAL_ENABLE` — PROVEN_RESOURCE.
- Manual AutoMatch is a separate `ActionAutoMatchExecute` action with code 8 — PROVEN_RESOURCE_STATIC.
- LOGNOVO raw contains 4 enable writes `12 4A 01 01 5E` and 4 disable writes `12 4A 01 00 5D`.
- LOGNOVO contains zero `02 24 04 08` manual-AutoMatch writes.
- Therefore the normal host model is one AutoCAL mode control; do not introduce a second AutoMatch-enable writer.
- Exact internal ECU mechanism that schedules automatic AutoMatch remains firmware-internal; current evidence supports it but does not byte-level prove the internal trigger.

Evidence:
- `tests/fixtures/amarelo-autocal-enable-automatch-coupling-v1.json`;
- `tests/test_amarelo_autocal_enable_automatch_coupling.py`.

WU-002 research is unblocked; product implementation remains blocked until WU-001/WU-002 closure.


## Finish AutoCAL — RTTI correction — 2026-09-21

Direct byte parsing of the Delphi field table plus `ActionFinishAutocalExecute@0x51A390` corrected an earlier 4-byte semantic offset error.

Correct field map:
- `DM+0x78 = VECT_AUTOCAL_U8_1`;
- `DM+0x7C = VECT_AUTOCAL_U8_2 / MAX_AUTOMATCH`;
- `DM+0xC8 = VECT_AUTOCAL_U8_0`;
- `DM+0xCC = NUM_ATUOMATCH_EXECUTED` (`0x0174`).

The Finish handler performs:
`MAX_AUTOMATCH -> getter -> NUM_AUTOMATCH_EXECUTED -> setter -> 100 ms -> PostActionRefresh`.

Therefore:
- `NUM_AUTOMATCH_EXECUTED := MAX_AUTOMATCH`: **PROVEN_STATIC**;
- previous interpretation `VECT_AUTOCAL_U8_1 -> VECT_AUTOCAL_U8_0`: **FALSIFIED**;
- exact ECU-side effect of that write: still **UNKNOWN** because supplied Portmons contain no observed `0x0174` write during Finish.

Canonical evidence:
- `tests/fixtures/amarelo-autocal-finish-0165-v1.json`;
- `tests/test_amarelo_autocal_finish_0165_evidence.py`.
