# OMEGAS RED V8.2 — causalidade e gate do Predictor

Data: 2026-08-31  
Branch isolada: `work/red-v82-causal-bridge`  
Parent remoto: `e9e093e79c68c47e002f1c1424b1ab6a196c4e95`  
RED preservado: `b637f5fff19b1ece93f22d1fced9640618609a60`

## Resposta direta

As três experiências seguintes foram executadas até o limite dos derivados já versionados. Nenhuma autorizou alteração do Android ou do Predictor. Isso não é ausência de progresso: agora existem gates executáveis e determinísticos que impedem que alinhamento visual, hashes ou snapshots parciais sejam promovidos como causalidade.

## 1. Ponte intervenção → resultado

O encaixe cronológico é forte:

- 1.708 episódios governados;
- 15 sessões terminam antes das intervenções;
- 11 batches MAP_K ocorrem no intervalo;
- 3 sessões, com 549 episódios, começam depois;
- nenhum episódio cruza o intervalo dos batches;
- identidade, ordem e relação `session.created_at_ms ≤ episode.start_ms ≤ episode.end_ms` passam.

Mesmo assim, o manifest de episódios e o fixture agregado MAP_K não carregam um mesmo `clock_contract` imutável. Um rótulo `UNIX_EPOCH_MS` fornecido pelo chamador é deliberadamente rejeitado. Resultado:

`DEFER_CLOCK_PROVENANCE_MISSING`

Claim máximo: **alinhamento estrutural aparente**, não efeito causal.

## 2. RED versus RED + Mapa/Curva

O RED mantém a prova cega existente:

| Métrica | RED |
|---|---:|
| Mediana | 1,253% |
| P90 | 5,406% |
| P95 | 8,014% |

Depois do último batch existe somente um hash aparente de Mapa K. O fixture público não contém dois estados 12×12 completos comparáveis, e não existe estado histórico governado da Curva K de 30 pontos associado a esses episódios. Sem duas condições explícitas, um candidato calibration-aware seria matematicamente não identificável.

Resultado: `DEFER_INSUFFICIENT_EXPLICIT_CALIBRATION_STATES`.

O candidato não foi inventado, suas métricas permanecem `null`, e o RED segue fallback integral.

## 3. AutoCal como explicador de regime/OOD

O contrato dimensional foi congelado corretamente:

| Entidade | Dimensão |
|---|---:|
| Faixas de aquisição AutoCal | 18 |
| Curva K / K-factor | 30 pontos |
| Mapa K | 12×12 |

Há 12 snapshots no checkpoint; os 12 são parciais, zero é temporalmente coerente e zero possui as 18 zonas decodificadas e alinhadas à telemetria. `PETR_INJ_TBP`, `MUL_ACT`, `PETR_MNFLD_PRESS_RV` e `GAS_MNFLD_PRESS_RV` permanecem família de 30 pontos e recebem status `UNKNOWN_PENDING_PROTOCOL_PROOF`, não corrupção.

Resultado: `DEFER_NO_TEMPORALLY_COHERENT_18_ZONE_SUPPORT`.

## Decisão de produto

`PRESERVE_RED_NO_ANDROID_PROMOTION`

- `HELD_OUT_GAIN_PROVEN=false`
- `P_IMPROVE_PROVEN=false`
- `VEHICLE_PROVEN=false`
- `AUTO_WRITE_ECU=false`
- Android/RED/Predictor: sem alteração nesta branch

## Evidência mínima que destrava o próximo passo

Não é necessário reimportar todo o corpus. Basta materializar, a partir do cache privado já existente, três derivados pequenos e privacy-safe:

1. um `clock_contract` com hash idêntico no manifest de episódios e no histórico de intervenções;
2. pelo menos dois estados físicos completos de Mapa K 12×12 e dois estados de Curva K 30, ligados a sessões por estado `EXPLICIT` ou `CHAIN_RECONSTRUCTED`;
3. snapshots reais contendo as 18 zonas, temporalmente coerentes e associados à sessão/telemetria, mantendo os quatro vetores de 30 separados.

Com esses três envelopes, os gates atuais deixam de retornar `DEFER` e executam as comparações cegas sem mudar o hot path.
