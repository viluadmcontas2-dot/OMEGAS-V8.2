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

## Execução iniciada
- Branch: `work/omegas-amarelo-wu001-autocal-ground-truth`
- Parent remoto: `41be880bc13f02a33a25c2096bee599cfd6817b0`
- Correção metodológica: o relógio Portmon é acumulado sobre todas as operações (incluindo IOCTL); `at_ms` marca o início do WRITE.
- A análise temporal anterior que tratava o segundo campo como timestamp absoluto é inválida e não será promovida.
