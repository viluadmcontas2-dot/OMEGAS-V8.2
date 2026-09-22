# OMEGAS-AMARELO-WU-001 — Ground truth byte a byte do AutoCAL

Issue: #73  
Estado: **CLOSURE_CANDIDATE — aguardando receipt integrado final**

## Objetivo

Reconstruir o contrato observável do AutoCAL nativo usando somente:
- ProgBase 4.2.0.6 original;
- PortmonAUTOCAL cru;
- PortmonLOGNOVO cru;
- fixtures/testes derivados desses artefatos.

Nenhuma fórmula OMEGAS é autoridade para firmware.

## Autoridade

- ProgBase SHA-256: `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`
- PortmonAUTOCAL SHA-256: `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`
- PortmonLOGNOVO SHA-256: `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`

## Contrato nativo fechado

### Enable / Disable

Objeto: `0x014A AUTO_CAL_ENABLE`.

- Enable: `12 4A 01 01 5E`
- Disable: `12 4A 01 00 5D`

ProgBase liga seu controle diretamente a `AUTO_CAL_ENABLE`. LOGNOVO contém quatro enables e quatro disables.

A observação operacional do original indica um único controle humano para o modo AutoCAL. Isso **não prescreve checkbox na nova UX**. O componente visual é decisão CUSTOMROM/OMEGADEV; o contrato científico é apenas um único enable/disable nativo.

Nenhum comando manual AutoMatch `action=8` acompanha os enables observados. AutoMatch automático pertence ao comportamento nativo da ECU quando AutoCAL está ativo.

### Actions 1 / 2 / 4 / 8

ProgBase prova o dispatcher comum e o bridge nativo:

- Reset Petrol = 1 → `02 24 04 01 2B`
- Reset Gas = 2 → `02 24 04 02 2C`
- Reset All = 4 → `02 24 04 04 2E`
- Manual AutoMatch = 8 → `02 24 04 08 32`

A família completa é **PROVEN_STATIC/BINARY** pelo bridge + checksum.  
Ação 4 é também **PROVEN_RAW** nos dois Portmons.  
1/2/8 não aparecem nos raws fornecidos e não devem ser descritos como raw-observed.

### Finish AutoCAL

A leitura RTTI corrigida é:

- `DM+0x78 = VECT_AUTOCAL_U8_1`
- `DM+0x7C = VECT_AUTOCAL_U8_2 / MAX_AUTOMATCH`
- `DM+0xC8 = VECT_AUTOCAL_U8_0`
- `DM+0xCC = NUM_ATUOMATCH_EXECUTED` / `0x0174`

`ActionFinishAutocalExecute@0x51A390` executa:

`MAX_AUTOMATCH -> NUM_AUTOMATCH_EXECUTED -> wait 100 ms -> PostActionRefresh`

Classificação:
- `NUM_AUTOMATCH_EXECUTED := MAX_AUTOMATCH` = **PROVEN_STATIC**
- caminho connection-aware do setter = **PROVEN_STATIC**
- write `0x0174` durante Finish nos raws fornecidos = **NOT OBSERVED**
- efeito interno exato da ECU após esse write = **UNKNOWN**

A interpretação histórica `U8_1 -> U8_0` está **FALSIFICADA** e não é contrato vigente.

### Ownership host x ECU

Nos dois Portmons:
- `0x015D..0x0160` (GasPointPrev/GasPoint current) são lidos pelo host e mudam sem host write;
- `0x016F/0x0170` (ACQUIRED_ZONES petrol/GNV) são lidos pelo host e mudam sem host write;
- `MUL_ACT` muda em epochs AutoMatch sem replacement-vector write do host.

Conclusão operacional:
**ECU/firmware possui a mutação nativa de pontos, flags de região e Curve K/MUL_ACT. ProgBase/OMEGAS observa e projeta.**

OMEGAS não deve sintetizar nem sobrescrever esses buffers para imitar o firmware.

### Maturidade / polling

ProgBase `0x516F64` compara counters da ECU contra thresholds também vindos da ECU.

- Gas 0..5 → `CALIBRATION_VAL_1[5]`
- Gas 6..17 → `CALIBRATION_VAL_1[8]`
- Petrol 0..5 → `VECT_AUTOCAL_U8_1`
- Petrol 6..17 → `CALIBRATION_VAL_1[2]`

LOGNOVO observa valor 3 nos quatro thresholds efetivos desta captura.  
**3 não é constante de produto.**

Essa comparação governa apresentação/maturidade; não é a fórmula de `ACQUIRED_ZONES`.

### AutoMatch epoch observável

Em cada epoch mensurável nas capturas aparecem, por readback independente:
- mudança de `NUM_AUTOMATCH_EXECUTED`;
- mudança dos 30 nós `MUL_ACT`;
- substituição em bloco de `GasPointPrev`;
- reset/reseed de counters GNV;
- clear de zonas quando o vetor estava não-zero.

Isso fecha o evento observável AutoMatch sem alegar ordem de instrução interna do firmware.

## Bounded UNKNOWN — não reproduzir no app

Os artefatos atuais não contêm firmware da ECU. Portanto permanecem deliberadamente UNKNOWN:

- fórmula interna que atualiza/remarca GasPoint;
- guard interno que seta/limpa `ACQUIRED_ZONES`;
- instante/instrução exata que copia Current → Prev no rollover;
- efeito interno exato de Finish após o write em `NUM_AUTOMATCH_EXECUTED`;
- eventual state machine física da ECU distinta do scheduler de refresh do ProgBase.

Esses itens possuem **stop condition**: não derivar fórmula host-side a partir de polling assíncrono quando o produto pode consumir o valor nativo diretamente.

## DoD

| Critério | Estado |
| --- | --- |
| parser/replay determinístico dos raws | PROVEN |
| hashes/proveniência canônicos | PROVEN |
| enable/disable byte-exato | PROVEN_RAW |
| action bridge 1/2/4/8 | PROVEN_STATIC; 4 também RAW |
| checksum/framing | PROVEN |
| host write vs ECU mutation observável | PROVEN para superfícies usadas pelo produto |
| MUL_ACT / AutoMatch epoch observável | PROVEN |
| facts separados de inferência | PROVEN por fixtures/gates |
| UNKNOWNs internos explicitamente limitados | PROVEN |
| receipt integrado do HEAD de fechamento | PENDING |

## Evidência canônica

- `docs/omegas-amarelo/evidence/WU-001-action-wire-proof-v2.md`
- `tests/fixtures/amarelo-autocal-action-wire-v1.json`
- `tests/fixtures/amarelo-autocal-enable-automatch-coupling-v1.json`
- `tests/fixtures/amarelo-autocal-finish-0165-v1.json`
- `tests/fixtures/amarelo-autocal-point-buffer-ownership-v1.json`
- `tests/fixtures/amarelo-autocal-acquired-zones-v1.json`
- `tests/fixtures/amarelo-autocal-epoch-lifecycle-v1.json`
- respectivos `tests/test_amarelo_*.py`

## Gate de fechamento

Não declarar CLOSED/PROVEN no issue #73 até existir um receipt do fast-contract contendo o conjunto integrado atual. UNKNOWNs firmware-internos acima são limites de evidência, não convite para inventar implementação paralela.
