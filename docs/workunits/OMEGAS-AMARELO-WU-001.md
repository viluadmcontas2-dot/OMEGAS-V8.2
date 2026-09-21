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
