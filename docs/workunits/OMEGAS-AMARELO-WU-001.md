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
- Fechamento continua bloqueado pelos códigos 1/2/8, semântica completa start/finish/reset e separação host-write vs ECU-mutation nos estados ainda UNKNOWN.


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
- exact Finish semantics / `0x0165` subindex responsibility;
- complete native state transitions, including acquire-petrol-line and draw-gas-petrol-curve;
- acquired-zone bit/byte semantics;
- complete host-write vs ECU-mutation attribution.

WU-002 research is unblocked; product implementation remains blocked until WU-001/WU-002 closure.
